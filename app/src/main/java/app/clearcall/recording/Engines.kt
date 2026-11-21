package app.clearcall.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import app.clearcall.data.RecordingQuality
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/** One capture session writing to one file. Instances are single-use. */
interface CallRecorderEngine {
    /** Highest absolute sample amplitude seen so far (0..32768). Used to detect silent captures. */
    val peak: Int

    /** Throws if the audio source cannot be opened; the caller then tries the next source. */
    fun start()

    /** Polled periodically by the service; engines that track the peak themselves do nothing. */
    fun sample()

    /** Finalizes the file. Returns false if no usable audio was written. */
    fun stop(): Boolean
}

object EngineFactory {
    fun create(
        context: Context,
        source: Int,
        quality: RecordingQuality,
        sampleRate: Int,
        bitrate: Int,
        file: File,
    ): CallRecorderEngine =
        if (quality.isLossless) {
            WavRecorderEngine(file, source, sampleRate)
        } else {
            MediaRecorderEngine(context, file, source, sampleRate, bitrate)
        }
}

/** AAC in an MPEG-4 (.m4a) container via the platform encoder. */
class MediaRecorderEngine(
    private val context: Context,
    private val file: File,
    private val source: Int,
    private val sampleRate: Int,
    private val bitrate: Int,
) : CallRecorderEngine {

    private var recorder: MediaRecorder? = null

    @Volatile
    override var peak: Int = 0
        private set

    override fun start() {
        val r = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(sampleRate)
            r.setAudioEncodingBitRate(bitrate)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (t: Throwable) {
            runCatching { r.release() }
            throw t
        }
        recorder = r
        sample() // the first maxAmplitude call always returns 0; prime it
    }

    override fun sample() {
        val r = recorder ?: return
        try {
            peak = max(peak, r.maxAmplitude)
        } catch (_: RuntimeException) {
            // Not in a state that can report amplitude; ignore.
        }
    }

    override fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            r.stop()
            true
        } catch (_: RuntimeException) {
            // Thrown when stopped immediately after start: nothing usable was encoded.
            false
        } finally {
            runCatching { r.release() }
        }
    }
}

/** Lossless 16-bit mono PCM in a WAV container, written on a dedicated thread. */
class WavRecorderEngine(
    private val file: File,
    private val source: Int,
    private val sampleRate: Int,
) : CallRecorderEngine {

    private var record: AudioRecord? = null
    private var output: OutputStream? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var dataBytes = 0L

    @Volatile
    override var peak: Int = 0
        private set

    @SuppressLint("MissingPermission") // RECORD_AUDIO is verified by the service before starting.
    override fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) throw IllegalStateException("Unsupported audio configuration")
        val bufferSize = max(minBuffer * 4, sampleRate * 2 / 5)

        val rec = AudioRecord(
            source,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("Audio source could not be initialised")
        }
        try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Audio source refused to start")
            }
        } catch (t: Throwable) {
            runCatching { rec.release() }
            throw t
        }

        val out = try {
            // Placeholder header, patched in stop() once the data length is known.
            FileOutputStream(file).buffered(64 * 1024).also { it.write(WavHeader.build(0, sampleRate)) }
        } catch (t: Throwable) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            throw t
        }
        record = rec
        output = out
        running = true
        thread = Thread({ pump(rec, out, bufferSize) }, "clearcall-wav").also { it.start() }
    }

    private fun pump(rec: AudioRecord, out: OutputStream, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        try {
            while (running) {
                val n = rec.read(buffer, 0, buffer.size)
                if (n < 0) break
                if (n == 0) continue
                out.write(buffer, 0, n)
                dataBytes += n
                var localPeak = peak
                var i = 0
                while (i + 1 < n) {
                    val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                    val magnitude = abs(sample)
                    if (magnitude > localPeak) localPeak = magnitude
                    i += 2
                }
                peak = localPeak
            }
        } catch (_: Exception) {
            // Disk full or recorder torn down; stop() reports whatever was written.
        }
    }

    override fun sample() = Unit

    override fun stop(): Boolean {
        val rec = record ?: return false
        record = null
        running = false
        runCatching { rec.stop() } // unblocks a pending read()
        thread?.join(3_000)
        runCatching { rec.release() }
        runCatching { output?.flush() }
        runCatching { output?.close() }
        output = null

        val bytes = dataBytes
        if (bytes <= 0) return false
        return runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(WavHeader.build(bytes, sampleRate))
            }
            true
        }.getOrDefault(false)
    }
}

object WavHeader {
    const val SIZE = 44

    fun build(dataBytes: Long, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val buffer = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt((36 + dataBytes).toInt())
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes.toInt())
        return buffer.array()
    }

    /** Rewrites the sizes in a WAV whose recorder died before stop(). */
    fun repairInPlace(file: File) {
        if (file.length() <= SIZE) return
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(24)
            val rate = Integer.reverseBytes(raf.readInt()).takeIf { it in 8_000..192_000 } ?: 48_000
            raf.seek(0)
            raf.write(build(file.length() - SIZE, rate))
        }
    }
}

object AudioMetadata {
    fun durationMs(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

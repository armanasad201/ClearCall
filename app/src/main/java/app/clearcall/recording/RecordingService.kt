package app.clearcall.recording

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import app.clearcall.ClearCallApp
import app.clearcall.core.AppPermissions
import app.clearcall.data.CallDirection
import app.clearcall.data.CaptureMode
import app.clearcall.data.RecordingEntity
import app.clearcall.data.RecordingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Foreground service (type "microphone") that owns exactly one recording at a time.
 *
 * Honest by construction: it never fabricates audio. Each capture source is really opened;
 * if none can be opened the attempt fails visibly. The source that actually worked is stored
 * with the recording, and a recording whose signal never rose above silence is flagged.
 */
class RecordingService : Service() {

    private enum class Phase { IDLE, STARTING, RECORDING, FINALIZING }

    private class Candidate(val mode: CaptureMode, val source: Int)

    private class Session(
        val id: String,
        val file: File,
        val engine: CallRecorderEngine,
        val mode: CaptureMode,
        val quality: RecordingQuality,
        val sampleRate: Int,
        val bitrate: Int,
        val direction: CallDirection,
        val number: String?,
        val startedAt: Long,
        val startElapsed: Long,
    )

    private sealed interface Outcome {
        data object Discarded : Outcome
        data class Saved(val silent: Boolean, val mode: CaptureMode) : Outcome
    }

    // All state below is touched only on the main thread (Main.immediate).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var phase = Phase.IDLE
    private var stopRequested = false
    private var session: Session? = null
    private var ticker: Job? = null

    private val container get() = (application as ClearCallApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> handleStopRequest()
            else -> begin(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        // Killed mid-recording: finalize the file so it stays playable; recoverOrphans() adopts it.
        session?.let { runCatching { it.engine.stop() } }
        scope.cancel()
        super.onDestroy()
    }

    /** Safe to call from any thread (used by the phone-state receiver when the call ends). */
    fun handleStopRequest() {
        scope.launch {
            when (phase) {
                Phase.RECORDING -> finishRecording()
                Phase.STARTING -> stopRequested = true
                Phase.IDLE -> stopSelf()
                Phase.FINALIZING -> Unit
            }
        }
    }

    private fun begin(intent: Intent?) {
        val direction = intent?.getStringExtra(EXTRA_DIRECTION)
            ?.let { name -> CallDirection.entries.firstOrNull { it.name == name } }
            ?: CallDirection.UNKNOWN
        val number = intent?.getStringExtra(EXTRA_NUMBER)

        // A service started with startForegroundService() must go foreground immediately.
        val promoted = promoteToForeground()
        if (phase != Phase.IDLE) {
            // Already recording. The call above satisfied startForegroundService's contract but
            // replaced the notification with "Starting…", so put the real one back.
            session?.let { NotificationHelper.updateRecording(this, it.startedAt, it.mode) }
            return
        }

        if (!AppPermissions.has(this, Manifest.permission.RECORD_AUDIO)) {
            fail("Microphone permission is off. Turn it on in ClearCall's settings.", direction, number, retry = false)
            return
        }
        if (!promoted) {
            fail(
                "Android blocked starting the recorder in the background. You can still record by tapping Record.",
                direction,
                number,
                retry = true,
            )
            return
        }

        phase = Phase.STARTING
        stopRequested = false
        scope.launch {
            try {
                startSession(direction, number)
            } catch (t: Throwable) {
                fail("Recording failed: ${t.message ?: t.javaClass.simpleName}", direction, number, retry = false)
            }
        }
    }

    private fun promoteToForeground(): Boolean = try {
        val notification = NotificationHelper.recordingNotification(this, null, "Starting…")
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(
                NotificationHelper.ID_RECORDING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NotificationHelper.ID_RECORDING, notification)
        }
        true
    } catch (_: Exception) {
        // ForegroundServiceStartNotAllowedException / SecurityException on modern Android.
        false
    }

    private suspend fun startSession(direction: CallDirection, number: String?) {
        val settings = withContext(Dispatchers.IO) { container.settings.current() }
        val caps = container.capabilities.evaluate()

        val candidates = buildList {
            if (caps.callAudioCapture) add(Candidate(CaptureMode.CALL_AUDIO, MediaRecorder.AudioSource.VOICE_CALL))
            if (settings.allowMicFallback) {
                add(Candidate(CaptureMode.MICROPHONE, MediaRecorder.AudioSource.MIC))
                add(Candidate(CaptureMode.MICROPHONE, MediaRecorder.AudioSource.VOICE_RECOGNITION))
            }
        }
        if (candidates.isEmpty()) {
            fail(
                "This device does not allow apps to capture call audio, and microphone mode is turned off.",
                direction,
                number,
                retry = false,
            )
            return
        }

        val quality = settings.quality
        val id = UUID.randomUUID().toString()
        val file = File(container.recordings.recordingsDir, "$id.${quality.extension}")
        // Each source is tried at the requested settings first, then at progressively safer ones,
        // so one unsupported encoder configuration doesn't make the whole recording fail.
        val ladder = listOf(
            quality.sampleRate to quality.bitrate,
            44_100 to minOf(quality.bitrate, 128_000),
            16_000 to minOf(quality.bitrate, 64_000),
        ).distinct()

        var chosen: Candidate? = null
        var engine: CallRecorderEngine? = null
        var usedRate = quality.sampleRate
        var usedBitrate = quality.bitrate
        search@ for (candidate in candidates) {
            for ((rate, bitrate) in ladder) {
                val attempt = EngineFactory.create(this, candidate.source, quality, rate, bitrate, file)
                try {
                    attempt.start()
                    chosen = candidate
                    engine = attempt
                    usedRate = rate
                    usedBitrate = bitrate
                    break@search
                } catch (_: Throwable) {
                    runCatching { file.delete() }
                }
            }
        }

        if (chosen == null || engine == null) {
            fail(
                "Android refused access to the audio source. Some phones block recording during calls.",
                direction,
                number,
                retry = false,
            )
            return
        }

        val startedAt = System.currentTimeMillis()
        session = Session(
            id = id,
            file = file,
            engine = engine,
            mode = chosen.mode,
            quality = quality,
            sampleRate = usedRate,
            bitrate = usedBitrate,
            direction = direction,
            number = number,
            startedAt = startedAt,
            startElapsed = SystemClock.elapsedRealtime(),
        )
        phase = Phase.RECORDING
        container.recordingState.set(RecorderStatus.Recording(startedAt, chosen.mode, direction))
        NotificationHelper.updateRecording(this, startedAt, chosen.mode)
        NotificationHelper.cancelPrompt(this)

        val running: CallRecorderEngine = engine
        ticker = scope.launch {
            while (isActive) {
                delay(500)
                running.sample()
            }
        }

        if (stopRequested) finishRecording()
    }

    private fun finishRecording() {
        val s = session ?: return
        if (phase != Phase.RECORDING) return
        phase = Phase.FINALIZING
        ticker?.cancel()
        scope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) { finalizeSession(s) }
            } catch (_: Throwable) {
                Outcome.Discarded
            }
            session = null
            phase = Phase.IDLE
            container.recordingState.set(RecorderStatus.Idle)
            if (outcome is Outcome.Saved) {
                NotificationHelper.postSaved(this@RecordingService, outcome.silent, outcome.mode)
            }
            ServiceCompat.stopForeground(this@RecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun finalizeSession(s: Session): Outcome {
        s.engine.sample()
        val finalized = s.engine.stop()
        val duration = AudioMetadata.durationMs(s.file) ?: (SystemClock.elapsedRealtime() - s.startElapsed)

        if (!finalized || !s.file.exists() || s.file.length() < MIN_BYTES || duration < MIN_DURATION_MS) {
            s.file.delete()
            return Outcome.Discarded
        }

        val silent = s.engine.peak < SILENCE_PEAK
        val bitrate = if (s.quality.isLossless) s.sampleRate * 16 else s.bitrate
        val repo = container.recordings
        repo.insert(
            RecordingEntity(
                id = s.id,
                fileName = s.file.name,
                startedAt = s.startedAt,
                durationMs = duration,
                sizeBytes = s.file.length(),
                format = s.quality.extension,
                sampleRate = s.sampleRate,
                bitrate = bitrate,
                direction = s.direction,
                phoneNumber = s.number,
                contactName = s.number?.let { ContactResolver.nameFor(this, it) },
                customTitle = null,
                captureMode = s.mode,
                silent = silent,
            ),
        )

        // The call log is written moments after the call ends; give it a beat, then enrich.
        delay(1_500)
        CallLogMatcher.find(this, s.startedAt)?.let { info ->
            repo.updateCallInfo(
                id = s.id,
                number = info.number ?: s.number,
                name = info.name,
                direction = info.direction ?: s.direction,
            )
        }
        repo.applyRetention(container.settings.current().retentionDays)
        return Outcome.Saved(silent, s.mode)
    }

    private fun fail(reason: String, direction: CallDirection, number: String?, retry: Boolean) {
        container.recordingState.set(RecorderStatus.Failed(reason, System.currentTimeMillis()))
        if (retry) {
            NotificationHelper.postRecordPrompt(this, direction, number, NotificationHelper.PROMPT_BLOCKED)
        } else {
            NotificationHelper.postStatus(this, "Couldn't record this call", reason)
        }
        phase = Phase.IDLE
        session = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "app.clearcall.action.START"
        const val ACTION_STOP = "app.clearcall.action.STOP"
        private const val EXTRA_DIRECTION = "direction"
        private const val EXTRA_NUMBER = "number"

        /** Below this the file is considered an aborted start, not a recording. */
        private const val MIN_DURATION_MS = 1_500L
        private const val MIN_BYTES = 1_024L

        /** Peak amplitude (0..32768) below which a capture is treated as silent. */
        private const val SILENCE_PEAK = 60

        @Volatile
        private var instance: RecordingService? = null

        fun startIntent(context: Context, direction: CallDirection, number: String?): Intent =
            Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DIRECTION, direction.name)
                .putExtra(EXTRA_NUMBER, number)

        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_STOP)

        /** Called when the call ends; a no-op if nothing is recording. */
        fun requestStop() {
            instance?.handleStopRequest()
        }
    }
}

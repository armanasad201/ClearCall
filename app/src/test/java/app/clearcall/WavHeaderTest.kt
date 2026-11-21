package app.clearcall

import app.clearcall.recording.WavHeader
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {

    private fun ByteArray.ascii(offset: Int, length: Int) = String(this, offset, length, Charsets.US_ASCII)
    private fun ByteArray.buffer(): ByteBuffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun build_writesAValidPcmHeader() {
        val header = WavHeader.build(dataBytes = 1_000, sampleRate = 48_000)
        val b = header.buffer()

        assertEquals(44, header.size)
        assertEquals("RIFF", header.ascii(0, 4))
        assertEquals(1_036, b.getInt(4)) // 36 + data
        assertEquals("WAVE", header.ascii(8, 4))
        assertEquals("fmt ", header.ascii(12, 4))
        assertEquals(16, b.getInt(16))
        assertEquals(1.toShort(), b.getShort(20)) // PCM
        assertEquals(1.toShort(), b.getShort(22)) // mono
        assertEquals(48_000, b.getInt(24))
        assertEquals(96_000, b.getInt(28)) // 48000 * 1 * 16 / 8
        assertEquals(2.toShort(), b.getShort(32))
        assertEquals(16.toShort(), b.getShort(34))
        assertEquals("data", header.ascii(36, 4))
        assertEquals(1_000, b.getInt(40))
    }

    @Test
    fun repairInPlace_fixesSizesOfAnUnfinishedRecording() {
        val file = File.createTempFile("clearcall", ".wav")
        try {
            // A recorder that died before stop(): placeholder header says 0 bytes, but audio follows.
            file.writeBytes(WavHeader.build(0, 44_100) + ByteArray(2_000))

            WavHeader.repairInPlace(file)

            val b = file.readBytes().buffer()
            assertEquals(2_036, b.getInt(4))
            assertEquals(44_100, b.getInt(24))
            assertEquals(2_000, b.getInt(40))
        } finally {
            file.delete()
        }
    }

    @Test
    fun repairInPlace_leavesHeaderOnlyFilesAlone() {
        val file = File.createTempFile("clearcall", ".wav")
        try {
            val original = WavHeader.build(0, 48_000)
            file.writeBytes(original)
            WavHeader.repairInPlace(file)
            assertEquals(original.toList(), file.readBytes().toList())
        } finally {
            file.delete()
        }
    }
}

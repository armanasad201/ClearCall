package app.clearcall

import app.clearcall.data.CallDirection
import app.clearcall.data.CaptureMode
import app.clearcall.data.RecordingEntity
import app.clearcall.data.displayTitle
import app.clearcall.data.mimeType
import app.clearcall.recording.DeviceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    private fun recording(
        title: String? = null,
        contact: String? = null,
        number: String? = null,
        format: String = "m4a",
    ) = RecordingEntity(
        id = "id",
        fileName = "id.$format",
        startedAt = 0,
        durationMs = 10_000,
        sizeBytes = 1_000,
        format = format,
        sampleRate = 48_000,
        bitrate = 256_000,
        direction = CallDirection.INCOMING,
        phoneNumber = number,
        contactName = contact,
        customTitle = title,
        captureMode = CaptureMode.MICROPHONE,
        silent = false,
    )

    @Test
    fun displayTitle_prefersCustomTitleThenContactThenNumber() {
        assertEquals("Mum", recording(title = "Mum", contact = "Mother", number = "+8801").displayTitle)
        assertEquals("Mother", recording(contact = "Mother", number = "+8801").displayTitle)
        assertEquals("+8801", recording(number = "+8801").displayTitle)
        assertEquals("Unknown caller", recording().displayTitle)
    }

    @Test
    fun displayTitle_ignoresBlankValues() {
        assertEquals("Mother", recording(title = "  ", contact = "Mother").displayTitle)
        assertEquals("Unknown caller", recording(title = "", contact = " ", number = "").displayTitle)
    }

    @Test
    fun mimeType_followsFormat() {
        assertEquals("audio/mp4", recording(format = "m4a").mimeType)
        assertEquals("audio/wav", recording(format = "wav").mimeType)
    }

    private fun caps(callAudio: Boolean) = DeviceCapabilities(
        sdkInt = 34,
        release = "14",
        manufacturer = "Google",
        model = "Pixel 8",
        hasTelephony = true,
        callAudioCapture = callAudio,
        microphonePermission = true,
    )

    @Test
    fun captureSummary_isHonestAboutEachCase() {
        assertTrue(caps(callAudio = true).captureSummary(micFallbackEnabled = true).contains("both sides"))

        val mic = caps(callAudio = false).captureSummary(micFallbackEnabled = true)
        assertTrue(mic.contains("microphone"))
        assertTrue(mic.contains("speakerphone"))

        val off = caps(callAudio = false).captureSummary(micFallbackEnabled = false)
        assertTrue(off.contains("will not record"))
    }

    @Test
    fun deviceName_avoidsRepeatingTheManufacturer() {
        assertEquals("Google Pixel 8", caps(false).deviceName)
        assertEquals(
            "Samsung SM-S918B",
            caps(false).copy(manufacturer = "Samsung", model = "SM-S918B").deviceName,
        )
        assertEquals(
            "Samsung Galaxy S23",
            caps(false).copy(manufacturer = "Samsung", model = "Samsung Galaxy S23").deviceName,
        )
    }
}

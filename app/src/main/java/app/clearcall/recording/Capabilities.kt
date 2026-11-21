package app.clearcall.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.clearcall.core.AppPermissions

data class DeviceCapabilities(
    val sdkInt: Int,
    val release: String,
    val manufacturer: String,
    val model: String,
    val hasTelephony: Boolean,
    /** True only if this build holds CAPTURE_AUDIO_OUTPUT (system/privileged installs). */
    val callAudioCapture: Boolean,
    val microphonePermission: Boolean,
) {
    val deviceName: String
        get() = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"

    /** Plain-language statement of what ClearCall will really capture on this device. */
    fun captureSummary(micFallbackEnabled: Boolean): String = when {
        callAudioCapture ->
            "Full call audio (both sides) is available on this device."
        micFallbackEnabled ->
            "Android does not let regular apps capture the other person's voice on this device. " +
                "ClearCall will use the microphone instead: your voice is recorded clearly, and the " +
                "other person only when you use speakerphone. Some phones silence the microphone " +
                "during calls; ClearCall flags any recording where no audio was detected."
        else ->
            "Android does not let regular apps capture call audio on this device, and microphone " +
                "mode is turned off, so ClearCall will not record calls."
    }
}

class CaptureCapabilities(private val context: Context) {

    fun evaluate(): DeviceCapabilities = DeviceCapabilities(
        sdkInt = Build.VERSION.SDK_INT,
        release = Build.VERSION.RELEASE,
        manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
        model = Build.MODEL,
        hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
        callAudioCapture = ContextCompat.checkSelfPermission(context, CAPTURE_AUDIO_OUTPUT) ==
            PackageManager.PERMISSION_GRANTED,
        microphonePermission = AppPermissions.has(context, Manifest.permission.RECORD_AUDIO),
    )

    private companion object {
        // Literal on purpose: a signature|privileged permission that regular apps can never hold.
        const val CAPTURE_AUDIO_OUTPUT = "android.permission.CAPTURE_AUDIO_OUTPUT"
    }
}

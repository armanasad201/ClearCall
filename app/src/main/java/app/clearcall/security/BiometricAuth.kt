package app.clearcall.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Biometric (fingerprint/face) with the device PIN, pattern or password as fallback, so the
 * user can never be locked out of their own recordings and ClearCall stores no secret itself.
 *
 * BIOMETRIC_WEAK | DEVICE_CREDENTIAL is the combination supported on every API level 23+
 * (BIOMETRIC_STRONG | DEVICE_CREDENTIAL is rejected on Android 9 and 10).
 */
object BiometricAuth {
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    @Volatile
    private var inProgress = false

    /** One of the BiometricManager.BIOMETRIC_* result codes. */
    fun status(context: Context): Int = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)

    fun canAuthenticate(context: Context): Boolean = status(context) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * True when the device has no screen lock or biometrics at all, so app lock cannot be
     * enforced. Transient errors (hardware busy, security update pending) are not included.
     */
    fun isNotSetUp(status: Int): Boolean =
        status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
            status == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onResult: (Boolean) -> Unit,
    ) {
        if (inProgress) return
        inProgress = true

        // If the activity is destroyed while the prompt is showing no callback is delivered,
        // so release the guard here or the lock screen could never prompt again.
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    inProgress = false
                    owner.lifecycle.removeObserver(this)
                }
            },
        )

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                inProgress = false
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                inProgress = false
                onResult(false)
            }
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .build()

        try {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
        } catch (_: IllegalStateException) {
            // The activity's state was already saved (app is going to the background).
            inProgress = false
            onResult(false)
        }
    }
}

package app.clearcall.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory unlock state. It starts locked on every process start and re-locks when the app
 * has been out of the foreground for longer than [GRACE_MS]. The grace period keeps the share
 * sheet, file pickers and screen rotation from forcing a new authentication every time.
 * Whether locking applies at all is decided by the "app lock" setting.
 */
class AppLockManager(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun markUnlocked() {
        backgroundedAt = null
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }

    fun onBackgrounded() {
        backgroundedAt = clock()
    }

    fun onForegrounded() {
        val left = backgroundedAt
        backgroundedAt = null
        if (_unlocked.value && left != null && clock() - left > GRACE_MS) {
            lock()
        }
    }

    companion object {
        const val GRACE_MS = 15_000L
    }
}

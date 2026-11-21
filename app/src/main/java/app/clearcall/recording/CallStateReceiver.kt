package app.clearcall.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import app.clearcall.appContainer
import app.clearcall.core.AppPermissions
import app.clearcall.data.CallDirection
import app.clearcall.data.RecordMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists the last phone state so the receiver (re-created for every broadcast) can tell an
 * answered incoming call from an outgoing one. Numbers are held only for the length of a call.
 */
internal class CallTracker(context: Context) {
    private val prefs = context.getSharedPreferences("call_tracker", Context.MODE_PRIVATE)

    var state: String
        get() = prefs.getString(KEY_STATE, IDLE) ?: IDLE
        set(value) {
            prefs.edit().putString(KEY_STATE, value).putLong(KEY_SINCE, System.currentTimeMillis()).apply()
        }

    /**
     * True while a call is genuinely in progress. An OFFHOOK older than [MAX_CALL_MS] means the
     * IDLE broadcast was missed (app force-stopped or permission revoked mid-call); treating it as
     * live would make the next call go unrecorded.
     */
    fun inCall(): Boolean =
        state == OFFHOOK && System.currentTimeMillis() - prefs.getLong(KEY_SINCE, 0L) < MAX_CALL_MS

    var incoming: Boolean
        get() = prefs.getBoolean(KEY_INCOMING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INCOMING, value).apply()
        }

    var number: String?
        get() = prefs.getString(KEY_NUMBER, null)
        set(value) {
            prefs.edit().putString(KEY_NUMBER, value).apply()
        }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val IDLE = "IDLE"
        const val RINGING = "RINGING"
        const val OFFHOOK = "OFFHOOK"
        private const val KEY_STATE = "state"
        private const val KEY_INCOMING = "incoming"
        private const val KEY_NUMBER = "number"
        private const val KEY_SINCE = "since"
        private const val MAX_CALL_MS = 12L * 60 * 60 * 1000
    }
}

/**
 * Listens for call state changes and decides, per the user's settings, whether to start
 * recording, offer a "Record" notification, or do nothing. Nothing starts unless the user
 * finished onboarding and accepted the recording-consent notice.
 */
class CallStateReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                lock.withLock { handle(app, state, number) }
            } catch (_: Throwable) {
                // A receiver must never crash the process during a call.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, state: String, number: String?) {
        val tracker = CallTracker(context)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // A second incoming call while one is active (call waiting) is ignored.
                if (!tracker.inCall()) {
                    tracker.state = CallTracker.RINGING
                    tracker.incoming = true
                    tracker.number = number ?: tracker.number
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!tracker.inCall()) {
                    val incoming = tracker.state == CallTracker.RINGING
                    tracker.state = CallTracker.OFFHOOK
                    tracker.incoming = incoming
                    tracker.number = number ?: tracker.number
                    onCallActive(context, incoming, tracker.number)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (tracker.state != CallTracker.IDLE) {
                    tracker.reset()
                    NotificationHelper.cancelPrompt(context)
                    RecordingService.requestStop()
                }
            }
        }
    }

    private suspend fun onCallActive(context: Context, incoming: Boolean, number: String?) {
        val container = context.appContainer
        val settings = container.settings.current()
        if (!settings.onboardingDone || !settings.consentAccepted) return
        if (settings.recordMode == RecordMode.OFF) return
        if (incoming && !settings.recordIncoming) return
        if (!incoming && !settings.recordOutgoing) return
        if (!AppPermissions.hasCore(context)) return

        val direction = if (incoming) CallDirection.INCOMING else CallDirection.OUTGOING
        if (settings.recordMode == RecordMode.AUTO) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    RecordingService.startIntent(context, direction, number),
                )
            } catch (_: Exception) {
                // Android 12+ forbids starting a foreground service from the background in most
                // cases. Fall back to a tap, which is always allowed.
                NotificationHelper.postRecordPrompt(context, direction, number, NotificationHelper.PROMPT_BLOCKED)
            }
        } else {
            NotificationHelper.postRecordPrompt(context, direction, number, NotificationHelper.PROMPT_DEFAULT)
        }
    }

    private companion object {
        /** Broadcasts can arrive back-to-back on different threads; serialize them. */
        val lock = Mutex()
    }
}

package app.clearcall.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.clearcall.MainActivity
import app.clearcall.R
import app.clearcall.data.CallDirection
import app.clearcall.data.CaptureMode

/**
 * Notification contents never include phone numbers or names, so nothing about who the user
 * talks to is exposed on the lock screen.
 */
@SuppressLint("MissingPermission") // notify() is a silent no-op when POST_NOTIFICATIONS is denied.
object NotificationHelper {
    private const val CHANNEL_RECORDING = "recording"
    private const val CHANNEL_PROMPT = "prompt"
    private const val CHANNEL_STATUS = "status"

    const val ID_RECORDING = 1001
    private const val ID_PROMPT = 1002
    private const val ID_STATUS = 1003

    const val PROMPT_DEFAULT =
        "Tap Record to start recording this call. Only record if everyone on the call has agreed."
    const val PROMPT_BLOCKED =
        "Android blocked automatic recording. Tap Record to start — only if everyone on the call has agreed."

    private const val IMMUTABLE_UPDATE = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Active recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown the whole time a call is being recorded"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROMPT, "Record this call?", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Offers to start recording when a call begins"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Recording results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Saved recordings and problems"
                setShowBadge(false)
            },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        IMMUTABLE_UPDATE,
    )

    fun recordingNotification(context: Context, startedAt: Long?, text: String): Notification {
        val stop = PendingIntent.getService(context, 1, RecordingService.stopIntent(context), IMMUTABLE_UPDATE)
        val builder = NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle("ClearCall is recording")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .addAction(0, "Stop", stop)
        if (startedAt != null) {
            builder.setUsesChronometer(true).setWhen(startedAt)
        }
        return builder.build()
    }

    fun updateRecording(context: Context, startedAt: Long, mode: CaptureMode) {
        val text = when (mode) {
            CaptureMode.CALL_AUDIO -> "Recording call audio (both sides)"
            else -> "Recording through the microphone (your voice)"
        }
        NotificationManagerCompat.from(context).notify(ID_RECORDING, recordingNotification(context, startedAt, text))
    }

    /** Lets the user start recording with a tap; a user tap is always allowed to start the service. */
    fun postRecordPrompt(context: Context, direction: CallDirection, number: String?, message: String) {
        val start = PendingIntent.getForegroundService(
            context,
            2,
            RecordingService.startIntent(context, direction, number),
            IMMUTABLE_UPDATE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_PROMPT)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle("Call in progress")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setTimeoutAfter(90_000)
            .setContentIntent(openApp(context))
            .addAction(0, "Record", start)
            .build()
        NotificationManagerCompat.from(context).notify(ID_PROMPT, notification)
    }

    fun cancelPrompt(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_PROMPT)
    }

    fun postStatus(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_STATUS, notification)
    }

    fun postSaved(context: Context, silent: Boolean, mode: CaptureMode) {
        when {
            silent -> postStatus(
                context,
                "Recording saved — no audio detected",
                "Your device appears to block call audio capture, so this recording is silent.",
            )
            mode == CaptureMode.MICROPHONE -> postStatus(
                context,
                "Recording saved",
                "Captured through the microphone: your voice, and the other person only if audible.",
            )
            else -> postStatus(context, "Recording saved", "Open ClearCall to listen.")
        }
    }
}

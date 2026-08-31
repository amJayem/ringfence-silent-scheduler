package com.ringfence.silentscheduler.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.ringfence.silentscheduler.MainActivity
import com.ringfence.silentscheduler.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Which real session a silence-started notification's "End silence" action should end. */
sealed class SilenceEndAction {
    data object QuickSilence : SilenceEndAction()
    data class Schedule(val scheduleId: String) : SilenceEndAction()
}

/**
 * Posts the optional silence-started/silence-ended notifications described by the
 * Settings "NOTIFICATION STYLE" choice. [NotificationStyle.BANNER] and
 * [NotificationStyle.SILENT_LOG] each get their own channel — once a channel exists,
 * Android lets the user override its importance from system Settings, and a single
 * shared channel can't express "loud alert" vs "silent history entry" per notification,
 * only per channel.
 */
@Singleton
class SilenceNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BANNER,
                    context.getString(R.string.notification_channel_banner_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.notification_channel_banner_desc) }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SILENT_LOG,
                    context.getString(R.string.notification_channel_silent_log_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = context.getString(R.string.notification_channel_silent_log_desc) }
            )
        }
    }

    /**
     * @param endEpochMillis when this session naturally ends — shown as a live
     * countdown via the system's own chronometer view (see [post]), visible in the
     * notification's collapsed state with no reposting or background work needed.
     * @param endAction identifies which real session "End silence" should end.
     */
    fun notifySilenceStarted(label: String, style: NotificationStyle, endEpochMillis: Long, endAction: SilenceEndAction) {
        post(
            id = label.hashCode(),
            style = style,
            title = context.getString(R.string.notification_silence_started_title, label),
            text = context.getString(R.string.notification_silence_started_text),
            endEpochMillis = endEpochMillis,
            endAction = endAction
        )
    }

    /**
     * @param restoredModeName the mode actually restored ("Normal"/"Vibrate"/"Silent") —
     * see [com.ringfence.silentscheduler.core.ringer.toFriendlyRingerModeName]. The body
     * must name what really happened; a schedule that started while the phone was
     * already on Vibrate restores Vibrate, not Normal (R-19).
     */
    fun notifySilenceEnded(label: String, style: NotificationStyle, restoredModeName: String) {
        post(
            id = label.hashCode(),
            style = style,
            title = context.getString(R.string.notification_silence_ended_title, label),
            text = context.getString(R.string.notification_silence_ended_text, restoredModeName),
            // Once sound is back there's nothing left to act on — the system clears it
            // on its own shortly after so it doesn't linger as clutter. The active
            // (started) notification never gets this: it's still useful for the whole
            // session, not just a fleeting confirmation.
            timeoutAfterMillis = ENDED_NOTIFICATION_TIMEOUT_MILLIS
        )
    }

    private fun post(
        id: Int,
        style: NotificationStyle,
        title: String,
        text: String,
        endEpochMillis: Long? = null,
        endAction: SilenceEndAction? = null,
        timeoutAfterMillis: Long? = null
    ) {
        if (style == NotificationStyle.NONE) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // No runtime permission — the rest of the app (silencing itself) works fine
            // without it, so this feature degrades silently rather than blocking anything.
            return
        }
        val channelId = if (style == NotificationStyle.BANNER) CHANNEL_BANNER else CHANNEL_SILENT_LOG
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, id, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (timeoutAfterMillis != null) {
            builder.setTimeoutAfter(timeoutAfterMillis)
        }

        if (endEpochMillis != null) {
            // setUsesChronometer + setChronometerCountDown hands the ticking off to the
            // system itself, which counts down to setWhen()'s time once a second — the
            // countdown shows in the collapsed notification with no repost/alarm of our
            // own needed to keep it live.
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(endEpochMillis)
        }

        if (endAction != null) {
            val actionIntent = Intent(context, EndSilenceReceiver::class.java).apply {
                action = when (endAction) {
                    is SilenceEndAction.QuickSilence -> EndSilenceReceiver.ACTION_END_QUICK_SILENCE
                    is SilenceEndAction.Schedule -> EndSilenceReceiver.ACTION_END_SCHEDULE
                }
                if (endAction is SilenceEndAction.Schedule) {
                    putExtra(EndSilenceReceiver.EXTRA_SCHEDULE_ID, endAction.scheduleId)
                }
            }
            val actionPendingIntent = PendingIntent.getBroadcast(
                context, id, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_volume_up,
                context.getString(R.string.notification_action_end_silence),
                actionPendingIntent
            )
        }

        notificationManager.notify(id, builder.build())
    }

    private companion object {
        const val CHANNEL_BANNER = "silence_banner"
        const val CHANNEL_SILENT_LOG = "silence_silent_log"
        const val ENDED_NOTIFICATION_TIMEOUT_MILLIS = 3_000L
    }
}

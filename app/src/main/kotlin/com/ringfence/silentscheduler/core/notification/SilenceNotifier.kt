package com.ringfence.silentscheduler.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.ringfence.silentscheduler.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    fun notifySilenceStarted(label: String, style: NotificationStyle) {
        post(
            id = label.hashCode(),
            style = style,
            title = context.getString(R.string.notification_silence_started_title, label),
            text = context.getString(R.string.notification_silence_started_text)
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
            text = context.getString(R.string.notification_silence_ended_text, restoredModeName)
        )
    }

    private fun post(id: Int, style: NotificationStyle, title: String, text: String) {
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
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_do_not_disturb)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    private companion object {
        const val CHANNEL_BANNER = "silence_banner"
        const val CHANNEL_SILENT_LOG = "silence_silent_log"
    }
}

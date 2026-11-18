package com.ringfence.silentscheduler.core.reminder

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

/**
 * Posts the periodic "review your schedules" nudge (see [ReviewReminderScheduler]).
 * Its own channel, not [com.ringfence.silentscheduler.core.notification.SilenceNotifier]'s
 * — this isn't about a silence window starting/ending, and the user's NOTIFICATION
 * STYLE choice (which can be NONE) shouldn't silently suppress a reminder they
 * explicitly opted into separately.
 */
@Singleton
class ReviewReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_review_reminder_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.notification_channel_review_reminder_desc) }
            )
        }
    }

    fun postReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.review_reminder_notification_title))
            .setContentText(context.getString(R.string.review_reminder_notification_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.review_reminder_notification_body)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "review_reminder"
        const val NOTIFICATION_ID = 7001
        const val REQUEST_CODE = 7001
    }
}

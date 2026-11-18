package com.ringfence.silentscheduler.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ringfence.silentscheduler.settings.domain.ReminderUnit
import com.ringfence.silentscheduler.settings.domain.toDays
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms/cancels the single next occurrence of the "review your schedules" nudge.
 * One-shot and re-armed (by [ReviewReminderReceiver], or whenever the interval
 * setting changes), rather than a repeating alarm — AlarmManager's own repeating
 * alarms don't support arbitrary custom periods reliably, and one-shot re-arming is
 * already this app's established pattern (see ScheduleAlarmScheduler).
 *
 * Deliberately inexact (`setAndAllowWhileIdle`, not an exact-alarm API): this is a
 * soft nudge a user turned on voluntarily, not a timing-sensitive silence window —
 * arriving within a day or so of the chosen interval is entirely fine, and it means
 * this feature needs no additional permission.
 */
@Singleton
class ReviewReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ReviewReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Arms the next occurrence for `amount` `unit`s from now, replacing any pending one. */
    fun scheduleNext(amount: Int, unit: ReminderUnit) {
        val triggerAtMillis = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(unit.toDays(amount).toLong())
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerAtMillis, pendingIntent())
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private companion object {
        const val REQUEST_CODE = 7002
    }
}

package com.ringfence.silentscheduler.schedule.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ringfence.silentscheduler.core.alarm.scheduleExactOrInexact
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.Schedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules exactly one (start, end) alarm pair per schedule — the trigger for
 * whichever occurrence [RecurringScheduleCalculator] says is next. The following
 * week's pair is scheduled again once the current one's end fires (see
 * ScheduleTriggerReceiver), rather than using AlarmManager's own repeating-alarm
 * API, since that can't express "these specific days of the week."
 */
@Singleton
class ScheduleAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNextOccurrence(schedule: Schedule) {
        // Defensive, not a UI substitute: ScheduleEditScreen already blocks saving with
        // zero repeat days, but this also guards schedules already sitting in storage
        // from an earlier build that allowed it (RecurringScheduleCalculator throws on
        // an empty set) — without this, a boot-time resweep of all enabled schedules
        // would crash on a single bad row.
        if (schedule.repeatDays.isEmpty()) {
            Log.w(TAG, "Schedule ${schedule.id} has no repeat days — not scheduling")
            return
        }

        val occurrence = RecurringScheduleCalculator.nextOccurrence(schedule, LocalDateTime.now())
        val zone = ZoneId.systemDefault()
        val startMillis = occurrence.start.atZone(zone).toInstant().toEpochMilli()
        val endMillis = occurrence.end.atZone(zone).toInstant().toEpochMilli()

        Log.i(TAG, "Scheduling '${schedule.label}' (${schedule.id}): start=${occurrence.start} end=${occurrence.end}")
        alarmManager.scheduleExactOrInexact(startMillis, pendingIntent(schedule.id, ScheduleTriggerReceiver.ACTION_START))
        alarmManager.scheduleExactOrInexact(endMillis, pendingIntent(schedule.id, ScheduleTriggerReceiver.ACTION_END))
    }

    fun cancelOccurrence(scheduleId: String) {
        alarmManager.cancel(pendingIntent(scheduleId, ScheduleTriggerReceiver.ACTION_START))
        alarmManager.cancel(pendingIntent(scheduleId, ScheduleTriggerReceiver.ACTION_END))
    }

    private fun pendingIntent(scheduleId: String, action: String): PendingIntent {
        val intent = Intent(context, ScheduleTriggerReceiver::class.java).apply {
            this.action = action
            putExtra(ScheduleTriggerReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(scheduleId, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCodeFor(scheduleId: String, action: String): Int = (scheduleId + action).hashCode()

    private companion object {
        const val TAG = "ScheduleAlarmScheduler"
    }
}

package com.ringfence.silentscheduler.quicksilence.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SilenceAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, SilenceRevertReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Falls back to an inexact alarm when the exact-alarm special access isn't
     * granted (Android 12+) instead of blocking Quick Silence behind another
     * permission flow. CLAUDE.md flags SCHEDULE_EXACT_ALARM as "only if truly
     * needed" — revisit this fallback once recurring schedules (step 6) need
     * to-the-minute precision.
     */
    fun scheduleRevert(triggerAtMillis: Long) {
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canBeExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent())
        }
    }

    fun cancelRevert() {
        alarmManager.cancel(pendingIntent())
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}

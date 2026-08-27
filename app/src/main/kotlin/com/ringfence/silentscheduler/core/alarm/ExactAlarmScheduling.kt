package com.ringfence.silentscheduler.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

/**
 * Falls back to an inexact alarm when the exact-alarm special access isn't granted
 * (Android 12+) instead of blocking scheduling behind another permission gate.
 * Shared by Quick Silence and recurring schedule triggers.
 */
fun AlarmManager.scheduleExactOrInexact(triggerAtMillis: Long, pendingIntent: PendingIntent) {
    val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
    if (canBeExact) {
        setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    } else {
        setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}

package com.ringfence.silentscheduler.schedule.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backstop for a missed schedule *start* alarm. [ScheduleTriggerHandler]'s own
 * stale-flag reconciliation (run every minute by [com.ringfence.silentscheduler.widget.WidgetTickScheduler])
 * only runs while something is already silencing, so it can catch a lost *end*
 * alarm but is never even armed to catch a lost *start* — a dropped start alarm
 * (Doze, an OEM battery manager, a revoked exact-alarm permission) means nothing is
 * silencing yet, so that tick stays off and the window is silently skipped with no
 * DND, no notification, and no widget indicator until the app happens to be
 * reopened. This runs unconditionally and independent of any window being active,
 * so a missed start is caught within one interval instead of only on next app-open
 * or reboot.
 */
@Singleton
class ScheduleReconciliationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ScheduleReconciliationReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Idempotent: re-registering the same inexact-repeating alarm is a harmless no-op. */
    fun start() {
        // Inexact: this is a backstop check, not the primary trigger (that's still
        // ScheduleAlarmScheduler's own exact-when-possible alarm) — a window being a
        // few minutes late to be *detected* this way is acceptable, and avoids this
        // backstop needing the exact-alarm permission it exists to cover for.
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL_MILLIS,
            INTERVAL_MILLIS,
            pendingIntent()
        )
    }

    private companion object {
        const val REQUEST_CODE = 2002
        const val INTERVAL_MILLIS = 15 * 60_000L
    }
}

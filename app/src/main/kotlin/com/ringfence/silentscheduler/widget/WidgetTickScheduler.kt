package com.ringfence.silentscheduler.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * W-06's "reflects state changes within a minute" needs something to redraw the
 * widget purely because time passed, not because any state changed — nothing else
 * in the app polls on a timer. Runs only while [SilencerCoordinator]'s shared
 * active-window count is above zero (see its own start/end calls below), so it never
 * wakes the device at all while nothing is silencing.
 */
@Singleton
class WidgetTickScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, WidgetTickReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun start() {
        // Inexact is deliberate: a minute or two of drift while the device is deep
        // asleep is acceptable for a home-screen widget nobody is looking at right
        // then, and avoids needing exact-alarm permission for this.
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + TICK_INTERVAL_MILLIS,
            TICK_INTERVAL_MILLIS,
            pendingIntent()
        )
    }

    fun stop() {
        alarmManager.cancel(pendingIntent())
    }

    private companion object {
        const val REQUEST_CODE = 2001
        const val TICK_INTERVAL_MILLIS = 60_000L
    }
}

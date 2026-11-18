package com.ringfence.silentscheduler.schedule.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms everything whenever the alarms already set can no longer be trusted:
 * after a reboot (AlarmManager clears all pending alarms), after the time zone or
 * system clock changes (each alarm is an absolute instant computed from a
 * wall-clock schedule, so "9:00" would otherwise keep firing at the old zone's
 * 9:00), after the app is updated, and when exact-alarm access is granted.
 * RecurringScheduleCalculator naturally handles a reboot happening mid-window (see its class doc), so no special "was a
 * window active when we rebooted" case is needed.
 */
@AndroidEntryPoint
class BootRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var reconciliationScheduler: ScheduleReconciliationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in REARM_ACTIONS) return
        // Reboot clears every AlarmManager alarm, this backstop included — without
        // re-arming it here, a start alarm dropped after this reboot would have no
        // background-safe way to ever be caught again.
        reconciliationScheduler.start()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                repository.reconcileAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val REARM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+);
            // spelled out because the constant doesn't exist on this app's min SDK.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}

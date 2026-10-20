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
 * AlarmManager clears all pending alarms on reboot, so every enabled schedule's
 * next occurrence has to be re-armed here. RecurringScheduleCalculator naturally
 * handles a reboot happening mid-window (see its class doc), so no special "was a
 * window active when we rebooted" case is needed.
 */
@AndroidEntryPoint
class BootRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var reconciliationScheduler: ScheduleReconciliationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
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
}

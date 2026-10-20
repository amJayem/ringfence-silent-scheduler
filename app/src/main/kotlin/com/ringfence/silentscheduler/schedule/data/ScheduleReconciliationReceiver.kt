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

/** Fired periodically by [ScheduleReconciliationScheduler], independent of app state. */
@AndroidEntryPoint
class ScheduleReconciliationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScheduleRepository

    override fun onReceive(context: Context, intent: Intent) {
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

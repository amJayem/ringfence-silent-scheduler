package com.ringfence.silentscheduler.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.data.ScheduleTriggerHandler
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Fired by the "End silence" action on the silence-started notification — the same
 * effect as the Dashboard's "End silence now" button, reachable without opening the
 * app. Ends whichever kind of session the notification belongs to.
 */
@AndroidEntryPoint
class EndSilenceReceiver : BroadcastReceiver() {

    @Inject
    lateinit var quickSilenceRepository: QuickSilenceRepository

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var triggerHandler: ScheduleTriggerHandler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_END_QUICK_SILENCE -> quickSilenceRepository.revertSilence()
                    ACTION_END_SCHEDULE -> {
                        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return@launch
                        val schedule = scheduleRepository.observeSchedules().first().find { it.id == scheduleId }
                        // The occurrence's own natural end, not "now" — matches the
                        // Dashboard's manual End button (see ScheduleTriggerHandler.handleEnd's
                        // own doc on why the two reference times must differ).
                        val referenceEnd = schedule
                            ?.let { RecurringScheduleCalculator.nextOccurrence(it, LocalDateTime.now()).end }
                            ?: LocalDateTime.now()
                        triggerHandler.handleEnd(scheduleId, referenceEnd)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_END_QUICK_SILENCE = "com.ringfence.silentscheduler.notification.ACTION_END_QUICK_SILENCE"
        const val ACTION_END_SCHEDULE = "com.ringfence.silentscheduler.notification.ACTION_END_SCHEDULE"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    }
}

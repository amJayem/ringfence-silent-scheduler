package com.ringfence.silentscheduler.widget

import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.data.ScheduleTriggerHandler
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The widget's write-side counterpart to [WidgetStateProvider] — mirrors
 * DashboardViewModel.endActiveNow()'s own "which kind of session is this" logic,
 * since the widget has no persisted notion of its own of what's currently silencing.
 */
@Singleton
class WidgetActionHandler @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val quickSilenceRepository: QuickSilenceRepository,
    private val triggerHandler: ScheduleTriggerHandler
) {
    suspend fun startQuickSilence(minutes: Int) {
        quickSilenceRepository.startSilence(minutes)
    }

    /** W-05: ends whichever kind of session is actually running right now. */
    suspend fun endActiveSilence() {
        if (quickSilenceRepository.observeState().first().isActive) {
            quickSilenceRepository.revertSilence()
            return
        }
        val now = LocalDateTime.now()
        val activeSchedule = scheduleRepository.observeSchedules().first().firstOrNull { schedule ->
            if (!schedule.isEnabled || schedule.repeatDays.isEmpty()) return@firstOrNull false
            val occ = RecurringScheduleCalculator.nextOccurrence(schedule, now)
            !occ.start.isAfter(now) && occ.end.isAfter(now)
        } ?: return
        val occ = RecurringScheduleCalculator.nextOccurrence(activeSchedule, now)
        triggerHandler.handleEnd(activeSchedule.id, occ.end)
    }
}

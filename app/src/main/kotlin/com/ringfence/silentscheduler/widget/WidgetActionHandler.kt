package com.ringfence.silentscheduler.widget

import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.data.ScheduleTriggerHandler
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The widget's write-side counterpart to [WidgetStateProvider] — mirrors
 * DashboardViewModel.endActiveNow()'s own "which kind of session is this" logic,
 * since the widget has no persisted notion of its own of what's currently silencing.
 *
 * [isCurrentlySilent] deliberately avoids [WidgetStateProvider.buildState] — that
 * builds the full display state (formatted strings, chip labels, a
 * NotificationManager call) for a decision that only ever needs one boolean, and
 * ToggleSilenceAction calling it just to branch on `.silent` was adding a
 * user-visible delay before the actual toggle even started.
 */
@Singleton
class WidgetActionHandler @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val quickSilenceRepository: QuickSilenceRepository,
    private val settingsRepository: SettingsRepository,
    private val triggerHandler: ScheduleTriggerHandler
) {
    suspend fun isCurrentlySilent(): Boolean =
        quickSilenceRepository.observeState().first().isActive || findActiveSchedule() != null

    suspend fun startQuickSilenceWithDefaultDuration() {
        val minutes = settingsRepository.observeSettings().first().defaultDurationMinutes
        startQuickSilence(minutes)
    }

    suspend fun startQuickSilence(minutes: Int) {
        quickSilenceRepository.startSilence(minutes)
    }

    /** W-05: ends whichever kind of session is actually running right now. */
    suspend fun endActiveSilence() {
        if (quickSilenceRepository.observeState().first().isActive) {
            quickSilenceRepository.revertSilence()
            return
        }
        val activeSchedule = findActiveSchedule() ?: return
        val occ = RecurringScheduleCalculator.nextOccurrence(activeSchedule, LocalDateTime.now())
        triggerHandler.handleEnd(activeSchedule.id, occ.end)
    }

    private suspend fun findActiveSchedule(): Schedule? {
        val now = LocalDateTime.now()
        return scheduleRepository.observeSchedules().first().firstOrNull { schedule ->
            if (!schedule.isEnabled || schedule.repeatDays.isEmpty()) return@firstOrNull false
            val occ = RecurringScheduleCalculator.nextOccurrence(schedule, now)
            !occ.start.isAfter(now) && occ.end.isAfter(now)
        }
    }
}

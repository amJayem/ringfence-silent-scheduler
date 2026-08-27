package com.ringfence.silentscheduler.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.core.time.formatDurationMinutes
import com.ringfence.silentscheduler.core.time.formatMinuteOfDay
import com.ringfence.silentscheduler.quicksilence.domain.DEFAULT_QUICK_SILENCE_DURATION_MILLIS
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceState
import com.ringfence.silentscheduler.schedule.data.ScheduleTriggerHandler
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleOccurrence
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.schedule.domain.toRepeatSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class ScheduleRowUiState(
    val schedule: Schedule,
    val timeRangeText: String,
    val repeatText: String,
    val caption: String,
    val isActiveNow: Boolean
)

/** Which mechanism is currently silencing the phone — determines what [DashboardViewModel.endActiveNow] calls. */
sealed class ActiveSource {
    data class FromSchedule(val scheduleId: String, val endTime: LocalDateTime) : ActiveSource()
    data object FromQuickSilence : ActiveSource()
}

data class ActiveCardState(
    val label: String,
    val remainingText: String,
    val untilText: String,
    val source: ActiveSource
)

data class DashboardUiState(
    val dayLabel: String = "",
    val active: ActiveCardState? = null,
    val idleRemainingText: String? = null,
    val idleUntilText: String? = null,
    val rows: List<ScheduleRowUiState> = emptyList(),
    val enabledCount: Int = 0,
    val totalCount: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val quickSilenceRepository: QuickSilenceRepository,
    private val triggerHandler: ScheduleTriggerHandler
) : ViewModel() {

    // Recomputes derived state (countdowns, NOW badges) even when nothing in
    // storage changed — 30s granularity matches the "Xh Ym" display, no need for
    // per-second ticking.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    // endActiveNow() doesn't change any Schedule's stored fields (the occurrence is
    // derived, not persisted), so repository.observeSchedules() never re-emits after
    // it — without this, the active/idle card would only catch up on the next 30s
    // tick instead of reflecting the tap immediately.
    private val manualRefresh = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeSchedules(),
        quickSilenceRepository.observeState(),
        ticker,
        manualRefresh
    ) { schedules, quickSilence, _, _ ->
        buildState(schedules, quickSilence, LocalDateTime.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun buildState(schedules: List<Schedule>, quickSilence: QuickSilenceState, now: LocalDateTime): DashboardUiState {
        val dayLabel = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

        val occurrencesById: Map<String, ScheduleOccurrence> = schedules
            .filter { it.isEnabled && it.repeatDays.isNotEmpty() }
            .associate { it.id to RecurringScheduleCalculator.nextOccurrence(it, now) }

        val activeSchedule = schedules.firstOrNull { schedule ->
            val occ = occurrencesById[schedule.id] ?: return@firstOrNull false
            !occ.start.isAfter(now) && occ.end.isAfter(now)
        }

        // Quick Silence takes priority for display when both happen to be active —
        // it's the one the user just tapped, so it should be the one they can end.
        val active: ActiveCardState? = when {
            quickSilence.isActive -> {
                val endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(quickSilence.endTimeMillis), ZoneId.systemDefault())
                val endMinuteOfDay = endTime.hour * 60 + endTime.minute
                ActiveCardState(
                    label = "Quick silence",
                    remainingText = formatDurationMinutes(Duration.between(now, endTime).toMinutes()),
                    untilText = "Quick silence · until ${formatMinuteOfDay(endMinuteOfDay)}",
                    source = ActiveSource.FromQuickSilence
                )
            }
            activeSchedule != null -> {
                val occ = occurrencesById.getValue(activeSchedule.id)
                val endMinuteOfDay = occ.end.hour * 60 + occ.end.minute
                ActiveCardState(
                    label = activeSchedule.label,
                    remainingText = formatDurationMinutes(Duration.between(now, occ.end).toMinutes()),
                    untilText = "${activeSchedule.label} · until ${formatMinuteOfDay(endMinuteOfDay)}",
                    source = ActiveSource.FromSchedule(activeSchedule.id, occ.end)
                )
            }
            else -> null
        }

        var idleRemainingText: String? = null
        var idleUntilText: String? = null
        if (active == null) {
            val soonest = schedules
                .mapNotNull { schedule -> occurrencesById[schedule.id]?.let { schedule to it } }
                .minByOrNull { (_, occ) -> occ.start }
            if (soonest != null) {
                val (schedule, occ) = soonest
                idleRemainingText = formatDurationMinutes(Duration.between(now, occ.start).toMinutes())
                idleUntilText = "until ${schedule.label} at ${formatMinuteOfDay(schedule.startMinuteOfDay)}"
            }
        }

        val rows = schedules.map { schedule ->
            val occ = occurrencesById[schedule.id]
            val isActiveNow = occ != null && !occ.start.isAfter(now) && occ.end.isAfter(now)
            val caption = when {
                !schedule.isEnabled -> "Off"
                occ == null -> ""
                isActiveNow -> "NOW · Silent until ${formatMinuteOfDay(schedule.endMinuteOfDay)}"
                else -> "Next in ${formatDurationMinutes(Duration.between(now, occ.start).toMinutes())}"
            }
            ScheduleRowUiState(
                schedule = schedule,
                timeRangeText = "${formatMinuteOfDay(schedule.startMinuteOfDay)} – ${formatMinuteOfDay(schedule.endMinuteOfDay)}",
                repeatText = schedule.repeatDays.toRepeatSummary(),
                caption = caption,
                isActiveNow = isActiveNow
            )
        }

        return DashboardUiState(
            dayLabel = dayLabel,
            active = active,
            idleRemainingText = idleRemainingText,
            idleUntilText = idleUntilText,
            rows = rows,
            enabledCount = schedules.count { it.isEnabled },
            totalCount = schedules.size
        )
    }

    fun toggleEnabled(schedule: Schedule) {
        viewModelScope.launch { repository.setEnabled(schedule.id, !schedule.isEnabled) }
    }

    fun endActiveNow() {
        val active = uiState.value.active ?: return
        viewModelScope.launch {
            when (val source = active.source) {
                is ActiveSource.FromSchedule -> triggerHandler.handleEnd(source.scheduleId, source.endTime)
                ActiveSource.FromQuickSilence -> quickSilenceRepository.revertSilence()
            }
            manualRefresh.value++
        }
    }

    fun silentNow() {
        viewModelScope.launch { quickSilenceRepository.startSilence(DEFAULT_QUICK_SILENCE_DURATION_MILLIS) }
    }
}

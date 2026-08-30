package com.ringfence.silentscheduler.schedule.ui

import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.core.time.formatDurationMinutes
import com.ringfence.silentscheduler.core.time.formatMinuteOfDay
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceState
import com.ringfence.silentscheduler.schedule.data.ScheduleTriggerHandler
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleOccurrence
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.schedule.domain.formatUpcomingTrigger
import com.ringfence.silentscheduler.schedule.domain.toRepeatSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val untilText: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val source: ActiveSource,
    /**
     * R-13: true when this window's own policy is RESTORE and the phone was already
     * silent/vibrate before it started — without calling this out, correctly
     * restoring to "still silent" looks like the app failed to un-mute.
     */
    val alreadySilentWarning: Boolean,
    val revertPolicy: RevertPolicy
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
    private val triggerHandler: ScheduleTriggerHandler,
    private val silencerCoordinator: SilencerCoordinator
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

    init {
        // Self-heal for a missed revert alarm (force-stop, Doze, reboot): a Quick
        // Silence session whose end time already passed but is still marked active
        // would otherwise never get corrected until the app is force-relaunched —
        // this catches it as soon as the Dashboard is open, not just at process start.
        viewModelScope.launch {
            ticker.collect { quickSilenceRepository.reconcileIfExpired() }
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeSchedules(),
        quickSilenceRepository.observeState(),
        silencerCoordinator.observeGlobalPriorMode(),
        ticker,
        manualRefresh
    ) { schedules, quickSilence, globalPriorMode, _, _ ->
        buildState(schedules, quickSilence, globalPriorMode, LocalDateTime.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun buildState(
        schedules: List<Schedule>,
        quickSilence: QuickSilenceState,
        globalPriorMode: Int?,
        now: LocalDateTime
    ): DashboardUiState {
        val dayLabel = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

        val occurrencesById: Map<String, ScheduleOccurrence> = schedules
            .filter { it.isEnabled && it.repeatDays.isNotEmpty() }
            .associate { it.id to RecurringScheduleCalculator.nextOccurrence(it, now) }

        val activeSchedule = schedules.firstOrNull { schedule ->
            val occ = occurrencesById[schedule.id] ?: return@firstOrNull false
            !occ.start.isAfter(now) && occ.end.isAfter(now)
        }

        // A window's own policy governs whether the already-silent warning applies to
        // it (R-13/R-14 talk about "that schedule"/"a quick session" specifically) —
        // if this window's policy is already SOUND, there's nothing surprising to warn
        // about, sound is coming back regardless of what the phone was on before.
        fun alreadySilentWarning(policy: RevertPolicy) =
            policy == RevertPolicy.RESTORE && globalPriorMode != null && globalPriorMode != AudioManager.RINGER_MODE_NORMAL

        // Quick Silence takes priority for display when both happen to be active —
        // it's the one the user just tapped, so it should be the one they can end.
        // Checking endTimeMillis here too (not just isActive) means a session whose
        // revert alarm was lost stops showing as active immediately, the same way a
        // schedule's own active window is derived live from the clock below, instead
        // of waiting on reconcileIfExpired()'s DataStore write to come back around.
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val active: ActiveCardState? = when {
            quickSilence.isActive && quickSilence.endTimeMillis > nowMillis -> {
                val endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(quickSilence.endTimeMillis), ZoneId.systemDefault())
                val endMinuteOfDay = endTime.hour * 60 + endTime.minute
                ActiveCardState(
                    label = "Quick silence",
                    untilText = "Quick silence · until ${formatMinuteOfDay(endMinuteOfDay)}",
                    startEpochMillis = quickSilence.startTimeMillis,
                    endEpochMillis = quickSilence.endTimeMillis,
                    source = ActiveSource.FromQuickSilence,
                    alreadySilentWarning = alreadySilentWarning(quickSilence.revertPolicy),
                    revertPolicy = quickSilence.revertPolicy
                )
            }
            activeSchedule != null -> {
                val occ = occurrencesById.getValue(activeSchedule.id)
                val endMinuteOfDay = occ.end.hour * 60 + occ.end.minute
                val zone = ZoneId.systemDefault()
                ActiveCardState(
                    label = activeSchedule.label,
                    untilText = "${activeSchedule.label} · until ${formatMinuteOfDay(endMinuteOfDay)}",
                    startEpochMillis = occ.start.atZone(zone).toInstant().toEpochMilli(),
                    endEpochMillis = occ.end.atZone(zone).toInstant().toEpochMilli(),
                    source = ActiveSource.FromSchedule(activeSchedule.id, occ.end),
                    alreadySilentWarning = alreadySilentWarning(activeSchedule.revertPolicy),
                    revertPolicy = activeSchedule.revertPolicy
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
                // occ is only null here for an enabled schedule when it has zero
                // repeat days — occurrencesById already filtered out disabled ones.
                !schedule.isEnabled -> "Off — no upcoming trigger"
                occ == null -> "No repeat days selected"
                isActiveNow -> "NOW · Silent until ${formatMinuteOfDay(schedule.endMinuteOfDay)}"
                else -> formatUpcomingTrigger(now, occ.start)
            }
            val styleWord = if (schedule.silenceStyle == SilenceStyle.VIBRATE_ONLY) "vibrate" else "silent"
            // R-17: distinguishes the two revert policies at a glance in the list.
            val revertSuffix = if (schedule.revertPolicy == RevertPolicy.SOUND) " · ends with sound" else ""
            ScheduleRowUiState(
                schedule = schedule,
                timeRangeText = "${formatMinuteOfDay(schedule.startMinuteOfDay)} – ${formatMinuteOfDay(schedule.endMinuteOfDay)}",
                repeatText = "${schedule.repeatDays.toRepeatSummary()} · $styleWord$revertSuffix",
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

    /**
     * R-14: the already-silent warning panel's one-tap override, schedule-only (a
     * quick session has no schedule to persist a policy change against — R-15).
     * Only flips *this* schedule's own policy; the current window keeps running
     * under whatever policy was already in effect when it started.
     */
    fun turnSoundOnForActiveSchedule() {
        val source = uiState.value.active?.source as? ActiveSource.FromSchedule ?: return
        viewModelScope.launch {
            val schedule = repository.observeSchedules().first().find { it.id == source.scheduleId } ?: return@launch
            repository.addOrUpdateSchedule(schedule.copy(revertPolicy = RevertPolicy.SOUND))
        }
    }
}

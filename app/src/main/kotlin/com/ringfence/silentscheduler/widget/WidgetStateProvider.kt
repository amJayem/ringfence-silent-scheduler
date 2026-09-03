package com.ringfence.silentscheduler.widget

import android.app.NotificationManager
import android.content.Context
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.time.formatActiveCountdown
import com.ringfence.silentscheduler.core.time.formatMinuteOfDay
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.schedule.domain.toRepeatSummary
import com.ringfence.silentscheduler.settings.domain.AVAILABLE_DURATION_MINUTES
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the widget's display state from the exact same repositories the Dashboard
 * uses (see DashboardViewModel.buildState) — a single point-in-time snapshot each
 * time the widget is asked to redraw, rather than a continuously ticking StateFlow,
 * since a widget only redraws when something explicitly asks it to (see
 * WidgetRefresher and WidgetTickScheduler).
 */
@Singleton
class WidgetStateProvider @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val quickSilenceRepository: QuickSilenceRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    suspend fun buildState(): WidgetUiState {
        // Self-heal first, same as the Dashboard's own ticker — the widget is often
        // the only thing showing when the app itself hasn't been reopened since a
        // missed revert alarm, so it must not trust a stale "still silent" flag.
        quickSilenceRepository.reconcileIfExpired()

        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val schedules = scheduleRepository.observeSchedules().first()
        val quickSilence = quickSilenceRepository.observeState().first()
        val settings = settingsRepository.observeSettings().first()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val dndAccessGranted = notificationManager?.isNotificationPolicyAccessGranted ?: false

        val occurrencesById = schedules
            .filter { it.isEnabled && it.repeatDays.isNotEmpty() }
            .associate { it.id to RecurringScheduleCalculator.nextOccurrence(it, now) }

        val activeSchedule = schedules.firstOrNull { schedule ->
            val occ = occurrencesById[schedule.id] ?: return@firstOrNull false
            !occ.start.isAfter(now) && occ.end.isAfter(now)
        }

        val chips = AVAILABLE_DURATION_MINUTES.map { minutes ->
            WidgetChip(
                minutes = minutes,
                label = if (minutes < 60) "${minutes}m" else "${minutes / 60}h",
                isDefault = minutes == settings.defaultDurationMinutes
            )
        }

        return when {
            quickSilence.isActive -> {
                // Which chip (if any) started this session — the elapsed duration
                // only matches a chip's own minutes when the session was actually
                // started at one of those presets, which covers every path that
                // reaches quick silence in this app (chip tap, "Silent now" sheet,
                // or its own default-duration fallback).
                val elapsedMinutes = ((quickSilence.endTimeMillis - quickSilence.startTimeMillis) / 60_000L).toInt()
                silentState(
                    // Matches the literal label used everywhere else a quick session
                    // is named (QuickSilenceRepositoryImpl, DashboardViewModel) — not
                    // a string resource there either, so kept consistent rather than
                    // mixed.
                    label = "Quick silence",
                    endEpochMillis = quickSilence.endTimeMillis,
                    zone = zone,
                    chips = chips,
                    activeChipMinutes = elapsedMinutes,
                    dndAccessGranted = dndAccessGranted
                )
            }
            activeSchedule != null -> {
                val occ = occurrencesById.getValue(activeSchedule.id)
                silentState(
                    label = activeSchedule.label,
                    endEpochMillis = occ.end.atZone(zone).toInstant().toEpochMilli(),
                    zone = zone,
                    chips = chips,
                    // A schedule isn't started from a duration chip, so none should
                    // read as selected — tapping one now would start a separate,
                    // overlapping quick silence, not "reselect" anything about it.
                    activeChipMinutes = null,
                    dndAccessGranted = dndAccessGranted
                )
            }
            else -> {
                val soonest = schedules
                    .mapNotNull { schedule -> occurrencesById[schedule.id]?.let { schedule to it } }
                    .minByOrNull { (_, occ) -> occ.start }
                val (bigText, subText) = if (soonest != null) {
                    val (schedule, _) = soonest
                    formatMinuteOfDay(schedule.startMinuteOfDay) to context.getString(
                        R.string.widget_next_format,
                        schedule.label,
                        schedule.repeatDays.toRepeatSummary()
                    )
                } else {
                    context.getString(R.string.widget_placeholder_value) to context.getString(
                        if (schedules.isNotEmpty()) R.string.widget_all_off else R.string.widget_no_schedules
                    )
                }
                WidgetUiState(
                    silent = false,
                    kicker = context.getString(R.string.widget_status_sound_on),
                    bigText = bigText,
                    subText = subText,
                    footerText = context.getString(R.string.widget_footer_sound_on),
                    dndAccessGranted = dndAccessGranted,
                    chips = chips
                )
            }
        }
    }

    private fun silentState(
        label: String,
        endEpochMillis: Long,
        zone: ZoneId,
        chips: List<WidgetChip>,
        activeChipMinutes: Int?,
        dndAccessGranted: Boolean
    ): WidgetUiState {
        val remainingSeconds = ((endEpochMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        val endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(endEpochMillis), zone)
        val endMinuteOfDay = endTime.hour * 60 + endTime.minute
        return WidgetUiState(
            silent = true,
            kicker = context.getString(R.string.widget_status_silent),
            bigText = formatActiveCountdown(remainingSeconds),
            subText = context.getString(R.string.widget_until_format, label, formatMinuteOfDay(endMinuteOfDay)),
            footerText = context.getString(R.string.widget_footer_silent),
            dndAccessGranted = dndAccessGranted,
            // Reflects whichever duration is actually running right now, not the
            // Settings-level default — the point is to show the user which chip
            // they actually picked, not to keep advertising a default that no
            // longer applies once a session is already underway.
            chips = chips.map { it.copy(isDefault = it.minutes == activeChipMinutes) }
        )
    }
}

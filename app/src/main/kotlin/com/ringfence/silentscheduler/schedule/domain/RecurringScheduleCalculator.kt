package com.ringfence.silentscheduler.schedule.domain

import java.time.LocalDateTime

data class ScheduleOccurrence(
    val start: LocalDateTime,
    val end: LocalDateTime
)

/**
 * Pure time math for recurring schedules — no Android APIs, so it's fully
 * unit-testable without mocking a clock (callers pass [now] explicitly).
 *
 * endMinuteOfDay <= startMinuteOfDay means the window crosses midnight; its end
 * LocalDateTime then falls on the day after its start.
 *
 * Search range is day -1 through +7 relative to [now]'s date: -1 catches an
 * overnight window that started yesterday and is still running (its end, not its
 * start, is what determines whether it's still relevant), and +7 guarantees at
 * least one full week is covered so every possible repeat-day combination matches.
 * Because the search picks the earliest occurrence whose *end* is still in the
 * future (not whose start is in the future), calling this while already inside a
 * window correctly returns that in-progress window — which is exactly what's
 * needed to re-arm alarms correctly after a reboot or reinstall mid-window.
 */
object RecurringScheduleCalculator {

    fun nextOccurrence(schedule: Schedule, now: LocalDateTime): ScheduleOccurrence {
        require(schedule.repeatDays.isNotEmpty()) { "Schedule ${schedule.id} has no repeat days" }

        val isOvernight = schedule.endMinuteOfDay <= schedule.startMinuteOfDay

        for (dayOffset in -1..7) {
            val startDate = now.toLocalDate().plusDays(dayOffset.toLong())
            if (startDate.dayOfWeek !in schedule.repeatDays) continue

            val start = startDate.atStartOfDay().plusMinutes(schedule.startMinuteOfDay.toLong())
            val endDate = if (isOvernight) startDate.plusDays(1) else startDate
            val end = endDate.atStartOfDay().plusMinutes(schedule.endMinuteOfDay.toLong())

            if (end.isAfter(now)) {
                return ScheduleOccurrence(start, end)
            }
        }

        error("No upcoming occurrence found within 8 days for schedule ${schedule.id}")
    }
}

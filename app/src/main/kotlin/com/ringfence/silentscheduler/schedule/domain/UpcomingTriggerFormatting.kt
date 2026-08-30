package com.ringfence.silentscheduler.schedule.domain

import com.ringfence.silentscheduler.core.time.formatDurationMinutes
import com.ringfence.silentscheduler.core.time.formatMinuteOfDay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val ABSOLUTE_THRESHOLD_HOURS = 12L

/**
 * H-16 / H-16b: a live "Next in Xh Ym" countdown is only useful inside the next
 * half-day — a trigger 62 hours out just reads as noise. Past [ABSOLUTE_THRESHOLD_HOURS]
 * this switches to an absolute time ("Tomorrow at 9:30 AM", "Next Monday at 9:30 AM").
 */
fun formatUpcomingTrigger(now: LocalDateTime, occurrenceStart: LocalDateTime): String {
    val untilStart = Duration.between(now, occurrenceStart)
    if (untilStart.toHours() < ABSOLUTE_THRESHOLD_HOURS) {
        return "Next in ${formatDurationMinutes(untilStart.toMinutes())}"
    }
    val time = formatMinuteOfDay(occurrenceStart.hour * 60 + occurrenceStart.minute)
    return when (ChronoUnit.DAYS.between(now.toLocalDate(), occurrenceStart.toLocalDate())) {
        0L -> "Today at $time"
        1L -> "Tomorrow at $time"
        else -> "Next ${occurrenceStart.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} at $time"
    }
}

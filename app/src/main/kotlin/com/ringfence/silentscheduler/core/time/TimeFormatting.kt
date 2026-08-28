package com.ringfence.silentscheduler.core.time

/** e.g. 795 -> "1:15 PM" */
fun formatMinuteOfDay(minuteOfDay: Int): String {
    val hour24 = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "%d:%02d %s".format(hour12, minute, amPm)
}

/**
 * Escalates granularity as the duration grows, rather than showing e.g. "168h 0m"
 * for a week away: minutes -> hours+minutes -> days+hours (24h+) -> weeks+days (7d+).
 */
fun formatDurationMinutes(totalMinutes: Long): String {
    val minutes = totalMinutes.coerceAtLeast(0)
    val weeks = minutes / (7 * 24 * 60)
    val daysRemainder = (minutes % (7 * 24 * 60)) / (24 * 60)
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    val mins = minutes % 60

    return when {
        weeks > 0 -> "${weeks}w ${daysRemainder}d"
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

/**
 * Live per-second countdown clock, e.g. "29:58" or "1:29:58" once past an hour.
 * Used by the Quick Silence tab, which ticks every second — [formatDurationMinutes]'s
 * minute granularity would show a static "29m" for the whole last minute.
 */
fun formatCountdownClock(remainingSeconds: Long): String {
    val totalSeconds = remainingSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

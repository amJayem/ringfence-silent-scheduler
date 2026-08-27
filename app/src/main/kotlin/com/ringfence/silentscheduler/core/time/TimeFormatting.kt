package com.ringfence.silentscheduler.core.time

/** e.g. 795 -> "1:15 PM" */
fun formatMinuteOfDay(minuteOfDay: Int): String {
    val hour24 = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "%d:%02d %s".format(hour12, minute, amPm)
}

/** e.g. 84 minutes -> "1h 24m"; 24 minutes -> "24m" */
fun formatDurationMinutes(totalMinutes: Long): String {
    val minutes = totalMinutes.coerceAtLeast(0)
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
}

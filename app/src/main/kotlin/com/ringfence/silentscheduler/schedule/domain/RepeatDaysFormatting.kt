package com.ringfence.silentscheduler.schedule.domain

import java.time.DayOfWeek

val WEEKDAYS = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
)
val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** e.g. all 7 -> "Every day"; Mon-Fri -> "Weekdays"; else "Mon, Wed, Fri" */
fun Set<DayOfWeek>.toRepeatSummary(): String = when {
    size == 7 -> "Every day"
    this == WEEKDAYS -> "Weekdays"
    this == WEEKENDS -> "Weekends"
    isEmpty() -> "Never"
    else -> sortedBy { it.value }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
}

package com.ringfence.silentscheduler.schedule.domain

import java.time.DayOfWeek

/**
 * @param startMinuteOfDay minutes since midnight (0-1439)
 * @param endMinuteOfDay minutes since midnight (0-1439); endMinuteOfDay <= startMinuteOfDay
 * means the window crosses midnight (e.g. 23:00-07:00)
 */
data class Schedule(
    val id: String,
    val label: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val repeatDays: Set<DayOfWeek>,
    val isEnabled: Boolean
)

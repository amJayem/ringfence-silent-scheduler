package com.ringfence.silentscheduler.schedule.domain

import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import java.time.DayOfWeek

/**
 * @param startMinuteOfDay minutes since midnight (0-1439)
 * @param endMinuteOfDay minutes since midnight (0-1439); endMinuteOfDay <= startMinuteOfDay
 * means the window crosses midnight (e.g. 23:00-07:00)
 * @param silenceStyle per-schedule override of the global default (Settings); defaults to
 * [SilenceStyle.FULL_SILENT] so schedules saved before this field existed keep behaving the
 * way the proto's default int value (0) already maps them.
 */
data class Schedule(
    val id: String,
    val label: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val repeatDays: Set<DayOfWeek>,
    val isEnabled: Boolean,
    val silenceStyle: SilenceStyle = SilenceStyle.FULL_SILENT
)

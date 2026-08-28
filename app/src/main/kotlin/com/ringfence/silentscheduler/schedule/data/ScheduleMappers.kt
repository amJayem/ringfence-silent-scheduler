package com.ringfence.silentscheduler.schedule.data

import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.schedule.data.proto.ScheduleProto
import com.ringfence.silentscheduler.schedule.data.proto.scheduleProto
import com.ringfence.silentscheduler.schedule.domain.Schedule
import java.time.DayOfWeek

fun ScheduleProto.toDomain(): Schedule = Schedule(
    id = id,
    label = label,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    repeatDays = repeatDaysList.map { DayOfWeek.of(it) }.toSet(),
    isEnabled = isEnabled,
    silenceStyle = if (silenceStyle == 1) SilenceStyle.VIBRATE_ONLY else SilenceStyle.FULL_SILENT
)

fun Schedule.toProto(): ScheduleProto {
    val domain = this
    return scheduleProto {
        id = domain.id
        label = domain.label
        startMinuteOfDay = domain.startMinuteOfDay
        endMinuteOfDay = domain.endMinuteOfDay
        repeatDays.addAll(domain.repeatDays.map { it.value })
        isEnabled = domain.isEnabled
        silenceStyle = if (domain.silenceStyle == SilenceStyle.VIBRATE_ONLY) 1 else 0
    }
}

package com.ringfence.silentscheduler.onboarding

import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.WEEKDAYS
import java.time.DayOfWeek
import java.util.UUID

/**
 * A one-tap starting point offered on the post-permissions onboarding tour, so a new
 * user has at least one real, editable schedule instead of a blank Dashboard. Times are
 * generic placeholders picked to be broadly plausible, not personalized — the user is
 * expected to open and retime each one afterward from the Dashboard, same as any other
 * schedule.
 */
enum class ScheduleTemplate(val titleRes: Int, val descriptionRes: Int) {
    WORK_HOURS(R.string.onboarding_template_work_title, R.string.onboarding_template_work_desc),
    PRAYER_TIMES(R.string.onboarding_template_prayer_title, R.string.onboarding_template_prayer_desc),
    FOCUS_BLOCK(R.string.onboarding_template_focus_title, R.string.onboarding_template_focus_desc)
}

private val EVERY_DAY = DayOfWeek.entries.toSet()

/** Materializes this template into the real, ready-to-save [Schedule](s) it represents. */
fun ScheduleTemplate.toSchedules(): List<Schedule> = when (this) {
    ScheduleTemplate.WORK_HOURS -> listOf(
        newSchedule(
            label = "Work hours",
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 17 * 60,
            repeatDays = WEEKDAYS
        )
    )
    ScheduleTemplate.FOCUS_BLOCK -> listOf(
        newSchedule(
            label = "Focus",
            startMinuteOfDay = 10 * 60,
            endMinuteOfDay = 11 * 60,
            repeatDays = EVERY_DAY
        )
    )
    ScheduleTemplate.PRAYER_TIMES -> listOf(
        newSchedule("Fajr", 5 * 60, 5 * 60 + 20, EVERY_DAY),
        newSchedule("Duhr", 13 * 60, 13 * 60 + 20, EVERY_DAY),
        newSchedule("Asr", 16 * 60 + 30, 16 * 60 + 50, EVERY_DAY),
        newSchedule("Magrib", 18 * 60 + 30, 18 * 60 + 50, EVERY_DAY),
        newSchedule("Esha", 20 * 60, 20 * 60 + 20, EVERY_DAY)
    )
}

private fun newSchedule(
    label: String,
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    repeatDays: Set<DayOfWeek>
) = Schedule(
    id = UUID.randomUUID().toString(),
    label = label,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    repeatDays = repeatDays,
    isEnabled = true,
    silenceStyle = SilenceStyle.VIBRATE_ONLY
)

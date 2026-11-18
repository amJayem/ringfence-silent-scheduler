package com.ringfence.silentscheduler.settings.domain

/** Unit for the "review your schedules" reminder's custom interval. */
enum class ReminderUnit { DAYS, WEEKS, MONTHS }

/**
 * Approximate — a month is treated as 30 days. This reminder is a soft nudge to
 * reconsider schedules that may no longer fit the user's routine, not a precisely
 * timed alarm, so calendar-exact month arithmetic isn't worth the complexity here.
 */
fun ReminderUnit.toDays(amount: Int): Int = amount * when (this) {
    ReminderUnit.DAYS -> 1
    ReminderUnit.WEEKS -> 7
    ReminderUnit.MONTHS -> 30
}

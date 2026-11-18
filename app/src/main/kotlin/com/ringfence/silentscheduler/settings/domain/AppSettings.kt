package com.ringfence.silentscheduler.settings.domain

import com.ringfence.silentscheduler.core.locale.AppLanguage
import com.ringfence.silentscheduler.core.notification.NotificationStyle
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.theme.ThemeOverride

/** The 15m/30m/1h/2h choices for "DEFAULT SILENT DURATION" (DESIGN_NOTES.md). */
val AVAILABLE_DURATION_MINUTES = listOf(15, 30, 60, 120)
const val DEFAULT_DURATION_MINUTES = 30

/** Default custom amount shown the first time the reminder's interval picker opens. */
const val DEFAULT_REVIEW_REMINDER_AMOUNT = 1

data class AppSettings(
    val defaultDurationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val silenceStyle: SilenceStyle = SilenceStyle.FULL_SILENT,
    val revertPolicy: RevertPolicy = RevertPolicy.RESTORE,
    val notificationStyle: NotificationStyle = NotificationStyle.BANNER,
    val themeOverride: ThemeOverride = ThemeOverride.SYSTEM,
    val languageOverride: AppLanguage = AppLanguage.ENGLISH,
    // Off by default (R-style optional nudge, not a core feature): periodically
    // reminds the user to check whether their schedules still fit their routine,
    // e.g. after a daily timetable changes. See ReviewReminderScheduler.
    val reviewReminderEnabled: Boolean = false,
    val reviewReminderAmount: Int = DEFAULT_REVIEW_REMINDER_AMOUNT,
    val reviewReminderUnit: ReminderUnit = ReminderUnit.MONTHS
)

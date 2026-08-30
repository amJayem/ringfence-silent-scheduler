package com.ringfence.silentscheduler.settings.domain

import com.ringfence.silentscheduler.core.notification.NotificationStyle
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.theme.ThemeOverride

/** The 15m/30m/1h/2h choices for "DEFAULT SILENT DURATION" (DESIGN_NOTES.md). */
val AVAILABLE_DURATION_MINUTES = listOf(15, 30, 60, 120)
const val DEFAULT_DURATION_MINUTES = 30

data class AppSettings(
    val defaultDurationMinutes: Int = DEFAULT_DURATION_MINUTES,
    val silenceStyle: SilenceStyle = SilenceStyle.FULL_SILENT,
    val revertPolicy: RevertPolicy = RevertPolicy.RESTORE,
    val notificationStyle: NotificationStyle = NotificationStyle.BANNER,
    val themeOverride: ThemeOverride = ThemeOverride.SYSTEM
)

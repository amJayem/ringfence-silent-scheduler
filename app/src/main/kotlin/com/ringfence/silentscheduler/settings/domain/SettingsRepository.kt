package com.ringfence.silentscheduler.settings.domain

import com.ringfence.silentscheduler.core.notification.NotificationStyle
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.theme.ThemeOverride
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun setDefaultDurationMinutes(minutes: Int)
    suspend fun setSilenceStyle(style: SilenceStyle)
    suspend fun setNotificationStyle(style: NotificationStyle)
    suspend fun setThemeOverride(override: ThemeOverride)
}

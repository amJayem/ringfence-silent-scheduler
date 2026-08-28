package com.ringfence.silentscheduler.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ringfence.silentscheduler.core.notification.NotificationStyle
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.theme.ThemeOverride
import com.ringfence.silentscheduler.settings.domain.AppSettings
import com.ringfence.silentscheduler.settings.domain.DEFAULT_DURATION_MINUTES
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private object Keys {
    val DEFAULT_DURATION_MINUTES = intPreferencesKey("settings_default_duration_minutes")
    val SILENCE_STYLE = stringPreferencesKey("settings_silence_style")
    val NOTIFICATION_STYLE = stringPreferencesKey("settings_notification_style")
    val THEME_OVERRIDE = stringPreferencesKey("settings_theme_override")
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            defaultDurationMinutes = prefs[Keys.DEFAULT_DURATION_MINUTES] ?: DEFAULT_DURATION_MINUTES,
            silenceStyle = prefs[Keys.SILENCE_STYLE].toEnumOrDefault(SilenceStyle.FULL_SILENT),
            notificationStyle = prefs[Keys.NOTIFICATION_STYLE].toEnumOrDefault(NotificationStyle.BANNER),
            themeOverride = prefs[Keys.THEME_OVERRIDE].toEnumOrDefault(ThemeOverride.SYSTEM)
        )
    }

    override suspend fun setDefaultDurationMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DEFAULT_DURATION_MINUTES] = minutes }
    }

    override suspend fun setSilenceStyle(style: SilenceStyle) {
        dataStore.edit { it[Keys.SILENCE_STYLE] = style.name }
    }

    override suspend fun setNotificationStyle(style: NotificationStyle) {
        dataStore.edit { it[Keys.NOTIFICATION_STYLE] = style.name }
    }

    override suspend fun setThemeOverride(override: ThemeOverride) {
        dataStore.edit { it[Keys.THEME_OVERRIDE] = override.name }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { stored -> runCatching { enumValueOf<T>(stored) }.getOrNull() } ?: default

package com.ringfence.silentscheduler.settings.ui

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.core.notification.NotificationStyle
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.theme.ThemeOverride
import com.ringfence.silentscheduler.settings.domain.AppSettings
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _isDndAccessGranted = MutableStateFlow(notificationManager.isNotificationPolicyAccessGranted)
    val isDndAccessGranted: StateFlow<Boolean> = _isDndAccessGranted.asStateFlow()

    /** DND access can only change from system Settings, never while this screen is focused — re-check on resume. */
    fun refreshDndStatus() {
        _isDndAccessGranted.value = notificationManager.isNotificationPolicyAccessGranted
    }

    fun setDefaultDurationMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDefaultDurationMinutes(minutes) }
    }

    fun setSilenceStyle(style: SilenceStyle) {
        viewModelScope.launch { settingsRepository.setSilenceStyle(style) }
    }

    fun setNotificationStyle(style: NotificationStyle) {
        viewModelScope.launch { settingsRepository.setNotificationStyle(style) }
    }

    fun setThemeOverride(override: ThemeOverride) {
        viewModelScope.launch { settingsRepository.setThemeOverride(override) }
    }
}

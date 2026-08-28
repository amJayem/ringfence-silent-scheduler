package com.ringfence.silentscheduler.quicksilence.data

import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.ringfence.silentscheduler.core.notification.SilenceNotifier
import com.ringfence.silentscheduler.core.ringer.RingerModeController
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceState
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val QUICK_SILENCE_LABEL = "Quick silence"

private object Keys {
    val IS_ACTIVE = booleanPreferencesKey("quick_silence_is_active")
    val START_TIME = longPreferencesKey("quick_silence_start_time")
    val END_TIME = longPreferencesKey("quick_silence_end_time")
    val PREVIOUS_RINGER_MODE = intPreferencesKey("quick_silence_previous_ringer_mode")
}

@Singleton
class QuickSilenceRepositoryImpl @Inject constructor(
    private val ringerModeController: RingerModeController,
    private val dataStore: DataStore<Preferences>,
    private val alarmScheduler: SilenceAlarmScheduler,
    private val settingsRepository: SettingsRepository,
    private val silenceNotifier: SilenceNotifier
) : QuickSilenceRepository {

    override fun observeState(): Flow<QuickSilenceState> = dataStore.data.map { prefs ->
        QuickSilenceState(
            isActive = prefs[Keys.IS_ACTIVE] ?: false,
            startTimeMillis = prefs[Keys.START_TIME] ?: 0L,
            endTimeMillis = prefs[Keys.END_TIME] ?: 0L
        )
    }

    override suspend fun startSilence(durationMillis: Long) {
        val settings = settingsRepository.observeSettings().first()
        val previousMode = ringerModeController.currentMode
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMillis
        dataStore.edit { prefs ->
            prefs[Keys.IS_ACTIVE] = true
            prefs[Keys.START_TIME] = startTime
            prefs[Keys.END_TIME] = endTime
            prefs[Keys.PREVIOUS_RINGER_MODE] = previousMode
        }
        // Requires ACCESS_NOTIFICATION_POLICY (already granted before this screen is
        // reachable). Not wrapped defensively here — CLAUDE.md's revoked-permission
        // banner belongs on the Dashboard (step 7), not swallowed silently here.
        ringerModeController.silence(settings.silenceStyle)
        silenceNotifier.notifySilenceStarted(QUICK_SILENCE_LABEL, settings.notificationStyle)
        alarmScheduler.scheduleRevert(endTime)
    }

    override suspend fun revertSilence() {
        val previousMode = dataStore.data.first()[Keys.PREVIOUS_RINGER_MODE] ?: AudioManager.RINGER_MODE_NORMAL
        ringerModeController.setMode(previousMode)
        dataStore.edit { prefs ->
            prefs[Keys.IS_ACTIVE] = false
        }
        val notificationStyle = settingsRepository.observeSettings().first().notificationStyle
        silenceNotifier.notifySilenceEnded(QUICK_SILENCE_LABEL, notificationStyle)
        alarmScheduler.cancelRevert()
    }
}

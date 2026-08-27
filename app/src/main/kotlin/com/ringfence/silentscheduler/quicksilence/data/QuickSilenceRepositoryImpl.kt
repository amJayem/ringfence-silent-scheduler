package com.ringfence.silentscheduler.quicksilence.data

import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.ringfence.silentscheduler.core.ringer.RingerModeController
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private object Keys {
    val IS_ACTIVE = booleanPreferencesKey("quick_silence_is_active")
    val END_TIME = longPreferencesKey("quick_silence_end_time")
    val PREVIOUS_RINGER_MODE = intPreferencesKey("quick_silence_previous_ringer_mode")
}

@Singleton
class QuickSilenceRepositoryImpl @Inject constructor(
    private val ringerModeController: RingerModeController,
    private val dataStore: DataStore<Preferences>,
    private val alarmScheduler: SilenceAlarmScheduler
) : QuickSilenceRepository {

    override fun observeState(): Flow<QuickSilenceState> = dataStore.data.map { prefs ->
        QuickSilenceState(
            isActive = prefs[Keys.IS_ACTIVE] ?: false,
            endTimeMillis = prefs[Keys.END_TIME] ?: 0L
        )
    }

    override suspend fun startSilence(durationMillis: Long) {
        val previousMode = ringerModeController.currentMode
        val endTime = System.currentTimeMillis() + durationMillis
        dataStore.edit { prefs ->
            prefs[Keys.IS_ACTIVE] = true
            prefs[Keys.END_TIME] = endTime
            prefs[Keys.PREVIOUS_RINGER_MODE] = previousMode
        }
        // Requires ACCESS_NOTIFICATION_POLICY (already granted before this screen is
        // reachable). Not wrapped defensively here — CLAUDE.md's revoked-permission
        // banner belongs on the Dashboard (step 7), not swallowed silently here.
        ringerModeController.silence()
        alarmScheduler.scheduleRevert(endTime)
    }

    override suspend fun revertSilence() {
        val previousMode = dataStore.data.first()[Keys.PREVIOUS_RINGER_MODE] ?: AudioManager.RINGER_MODE_NORMAL
        ringerModeController.setMode(previousMode)
        dataStore.edit { prefs ->
            prefs[Keys.IS_ACTIVE] = false
        }
        alarmScheduler.cancelRevert()
    }
}

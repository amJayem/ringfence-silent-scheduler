package com.ringfence.silentscheduler.quicksilence.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ringfence.silentscheduler.core.data.toEnumOrDefault
import com.ringfence.silentscheduler.core.notification.SilenceNotifier
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.core.ringer.toFriendlyRingerModeName
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
    val REVERT_POLICY = stringPreferencesKey("quick_silence_revert_policy")
}

@Singleton
class QuickSilenceRepositoryImpl @Inject constructor(
    private val silencerCoordinator: SilencerCoordinator,
    private val dataStore: DataStore<Preferences>,
    private val alarmScheduler: SilenceAlarmScheduler,
    private val settingsRepository: SettingsRepository,
    private val silenceNotifier: SilenceNotifier
) : QuickSilenceRepository {

    override fun observeState(): Flow<QuickSilenceState> = dataStore.data.map { prefs ->
        QuickSilenceState(
            isActive = prefs[Keys.IS_ACTIVE] ?: false,
            startTimeMillis = prefs[Keys.START_TIME] ?: 0L,
            endTimeMillis = prefs[Keys.END_TIME] ?: 0L,
            revertPolicy = prefs[Keys.REVERT_POLICY].toEnumOrDefault(RevertPolicy.RESTORE)
        )
    }

    override suspend fun startSilence(durationMinutes: Int) {
        val settings = settingsRepository.observeSettings().first()
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMinutes * 60_000L
        dataStore.edit { prefs ->
            prefs[Keys.IS_ACTIVE] = true
            prefs[Keys.START_TIME] = startTime
            prefs[Keys.END_TIME] = endTime
            // Captured now, not read again at revert time (R-09/R-07): a later
            // Settings change must not retroactively change an already-running session.
            prefs[Keys.REVERT_POLICY] = settings.revertPolicy.name
        }
        // Requires ACCESS_NOTIFICATION_POLICY (already granted before this screen is
        // reachable). Not wrapped defensively here — CLAUDE.md's revoked-permission
        // banner belongs on the Dashboard (step 7), not swallowed silently here.
        silencerCoordinator.onWindowStart(settings.silenceStyle)
        silenceNotifier.notifySilenceStarted(QUICK_SILENCE_LABEL, settings.notificationStyle)
        alarmScheduler.scheduleRevert(endTime)
    }

    override suspend fun revertSilence() {
        val policy = dataStore.data.first()[Keys.REVERT_POLICY].toEnumOrDefault(RevertPolicy.RESTORE)
        dataStore.edit { prefs ->
            prefs[Keys.IS_ACTIVE] = false
        }
        val restoredMode = silencerCoordinator.onWindowEnd(policy)
        // null means another window (a schedule) is still silencing — the phone isn't
        // actually un-silenced yet, so no "sound is back" notification should fire.
        if (restoredMode != null) {
            val notificationStyle = settingsRepository.observeSettings().first().notificationStyle
            silenceNotifier.notifySilenceEnded(QUICK_SILENCE_LABEL, notificationStyle, restoredMode.toFriendlyRingerModeName())
        }
        alarmScheduler.cancelRevert()
    }
}

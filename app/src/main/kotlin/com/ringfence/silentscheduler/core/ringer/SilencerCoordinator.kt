package com.ringfence.silentscheduler.core.ringer

import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private object Keys {
    val ACTIVE_COUNT = intPreferencesKey("silencer_active_count")
    val GLOBAL_PRIOR_MODE = intPreferencesKey("silencer_global_prior_mode")
}

/**
 * Single point of truth for "what was the ringer before Silent Scheduler started
 * silencing it," shared by every overlapping window — recurring schedules and quick
 * silence alike (spec R-10/R-18). Without this, each window captured its own "prior
 * mode" independently: a window that started while another was already silencing
 * would capture a mode Silent Scheduler itself had just set, and later restore to
 * *that* instead of what the phone was really on before any of this began.
 *
 * The fix is a shared counter: only the window that takes the count from 0 to 1
 * captures the real prior mode; only the window that takes it back to 0 (necessarily
 * the last to end, which implicitly satisfies P-01's "latest end wins") applies its
 * own [RevertPolicy] against that one captured mode. Windows that start or end while
 * others are still active just apply their own [SilenceStyle] (start) or do nothing
 * to the shared state (end) — some other still-active window owns the eventual revert.
 */
@Singleton
class SilencerCoordinator @Inject constructor(
    private val ringerModeController: RingerModeController,
    private val dataStore: DataStore<Preferences>
) {
    /** Non-null only while at least one window is currently silencing. */
    fun observeGlobalPriorMode(): Flow<Int?> = dataStore.data.map { prefs ->
        if ((prefs[Keys.ACTIVE_COUNT] ?: 0) > 0) prefs[Keys.GLOBAL_PRIOR_MODE] else null
    }

    /** Call when a window (schedule occurrence or quick session) starts silencing. */
    suspend fun onWindowStart(style: SilenceStyle) {
        dataStore.edit { prefs ->
            val count = prefs[Keys.ACTIVE_COUNT] ?: 0
            if (count == 0) {
                prefs[Keys.GLOBAL_PRIOR_MODE] = ringerModeController.currentMode
            }
            prefs[Keys.ACTIVE_COUNT] = count + 1
        }
        ringerModeController.silence(style)
    }

    /**
     * Call when a window ends. Only actually changes the ringer if this was the last
     * active window; returns the mode it restored to, or null if other windows are
     * still silencing — in which case nothing changed and no "sound is back"
     * notification should be posted, since the phone is still silent.
     */
    suspend fun onWindowEnd(policy: RevertPolicy): Int? {
        var restoredMode: Int? = null
        dataStore.edit { prefs ->
            val count = ((prefs[Keys.ACTIVE_COUNT] ?: 1) - 1).coerceAtLeast(0)
            if (count == 0) {
                val priorMode = prefs[Keys.GLOBAL_PRIOR_MODE]
                restoredMode = when (policy) {
                    RevertPolicy.RESTORE -> priorMode ?: AudioManager.RINGER_MODE_NORMAL
                    RevertPolicy.SOUND -> AudioManager.RINGER_MODE_NORMAL
                }
                prefs.remove(Keys.GLOBAL_PRIOR_MODE)
            }
            prefs[Keys.ACTIVE_COUNT] = count
        }
        restoredMode?.let { ringerModeController.setMode(it) }
        return restoredMode
    }
}

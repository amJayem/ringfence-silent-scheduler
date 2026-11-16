package com.ringfence.silentscheduler.core.ringer

import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ringfence.silentscheduler.widget.WidgetTickScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What [SilencerCoordinator.onWindowEnd] actually did. */
sealed class WindowEndResult {
    /** This window wasn't one of the active ones (already ended, or never started) — nothing changed. */
    data object NotActive : WindowEndResult()

    /** This window is over, but another one is still silencing — the ringer was left alone. */
    data object StillSilenced : WindowEndResult()

    /** This was the last active window; the ringer was put back to [mode]. */
    data class Restored(val mode: Int) : WindowEndResult()
}

/**
 * The persisted bookkeeping behind [SilencerCoordinator], kept as pure functions over
 * a Preferences snapshot so each one runs inside a single DataStore transaction (and
 * can be unit-tested without Android).
 *
 * Every silence window — each schedule occurrence, and the quick silence session —
 * owns one boolean "claim" key that is true exactly while that window is silencing.
 * "How many windows are active" is always *counted from those claims*, never stored
 * separately: an earlier design kept a separate counter next to the per-window
 * flags, and every path that updated one without the other (two starts for one
 * window, two ends for one window, a flag cleared without the counter, a start
 * racing the process-start reconciliation) left the counter wrong — which showed up
 * as the phone staying silent after a window ended, or being un-silenced while
 * another window was still running.
 */
internal object SilencerState {
    private const val SCHEDULE_CLAIM_PREFIX = "schedule_active_"

    /** True while a quick silence session is running (also read by QuickSilenceRepository as "is active"). */
    val QUICK_SILENCE_CLAIM = booleanPreferencesKey("quick_silence_is_active")

    /** The ringer mode captured when the first of the currently active windows started. */
    val GLOBAL_PRIOR_MODE = intPreferencesKey("silencer_global_prior_mode")

    /** Written by builds before claims were counted directly; no longer read, removed on the next write. */
    private val LEGACY_ACTIVE_COUNT = intPreferencesKey("silencer_active_count")

    fun scheduleClaim(scheduleId: String) = booleanPreferencesKey(SCHEDULE_CLAIM_PREFIX + scheduleId)

    private fun isClaim(key: Preferences.Key<*>) =
        key.name == QUICK_SILENCE_CLAIM.name || key.name.startsWith(SCHEDULE_CLAIM_PREFIX)

    fun activeWindowCount(prefs: Preferences): Int =
        prefs.asMap().count { (key, value) -> value == true && isClaim(key) }

    fun activeScheduleIds(prefs: Preferences): Set<String> =
        prefs.asMap()
            .filter { (key, value) -> value == true && key.name.startsWith(SCHEDULE_CLAIM_PREFIX) }
            .keys.map { it.name.removePrefix(SCHEDULE_CLAIM_PREFIX) }.toSet()

    enum class StartOutcome { ALREADY_ACTIVE, STARTED, STARTED_FROM_IDLE }

    /** @param currentMode only invoked when this is the first active window (its result becomes the prior mode). */
    fun start(prefs: MutablePreferences, claim: Preferences.Key<Boolean>, currentMode: () -> Int): StartOutcome {
        prefs.remove(LEGACY_ACTIVE_COUNT)
        if (prefs[claim] == true) return StartOutcome.ALREADY_ACTIVE
        val wasIdle = activeWindowCount(prefs) == 0
        if (wasIdle) prefs[GLOBAL_PRIOR_MODE] = currentMode()
        prefs[claim] = true
        return if (wasIdle) StartOutcome.STARTED_FROM_IDLE else StartOutcome.STARTED
    }

    fun end(prefs: MutablePreferences, claim: Preferences.Key<Boolean>, policy: RevertPolicy): WindowEndResult {
        prefs.remove(LEGACY_ACTIVE_COUNT)
        if (prefs[claim] != true) return WindowEndResult.NotActive
        prefs.remove(claim)
        if (activeWindowCount(prefs) > 0) return WindowEndResult.StillSilenced
        val priorMode = prefs[GLOBAL_PRIOR_MODE]
        prefs.remove(GLOBAL_PRIOR_MODE)
        return WindowEndResult.Restored(
            when (policy) {
                RevertPolicy.RESTORE -> priorMode ?: AudioManager.RINGER_MODE_NORMAL
                RevertPolicy.SOUND -> AudioManager.RINGER_MODE_NORMAL
            }
        )
    }
}

/**
 * Single point of truth for "what was the ringer before Silent Scheduler started
 * silencing it," shared by every overlapping window — recurring schedules and quick
 * silence alike (spec R-10/R-18). Without this, each window captured its own "prior
 * mode" independently: a window that started while another was already silencing
 * would capture a mode Silent Scheduler itself had just set, and later restore to
 * *that* instead of what the phone was really on before any of this began.
 *
 * Only the window that starts while nothing else is active captures the real prior
 * mode; only the window whose end leaves nothing else active (necessarily the last to
 * end, which implicitly satisfies P-01's "latest end wins") applies its own
 * [RevertPolicy] against that one captured mode. Starting or ending the same window
 * twice is a no-op the second time — see [SilencerState] for why that matters.
 */
@Singleton
class SilencerCoordinator @Inject constructor(
    private val ringerModeController: RingerModeController,
    private val dataStore: DataStore<Preferences>,
    private val widgetTickScheduler: WidgetTickScheduler
) {
    /** Non-null only while at least one window is currently silencing. */
    fun observeGlobalPriorMode(): Flow<Int?> = dataStore.data.map { prefs ->
        if (SilencerState.activeWindowCount(prefs) > 0) prefs[SilencerState.GLOBAL_PRIOR_MODE] else null
    }

    /** Whether any window (schedule or quick silence) is silencing right now. */
    suspend fun isAnyWindowActive(): Boolean = SilencerState.activeWindowCount(dataStore.data.first()) > 0

    /** Ids of the schedules currently holding a claim — including any whose schedule has since been deleted. */
    suspend fun activeScheduleIds(): Set<String> = SilencerState.activeScheduleIds(dataStore.data.first())

    /**
     * Call when a window (schedule occurrence or quick session) starts silencing.
     *
     * @return true if this call is what started the window. False means nothing
     * changed: either this window was already active (a duplicate start — e.g. the
     * alarm and a reconciliation pass both noticing the same window), or the system
     * refused to change the ringer (Do Not Disturb access revoked), in which case
     * the claim is rolled back so nothing reports a silence that isn't happening.
     */
    suspend fun onWindowStart(claim: Preferences.Key<Boolean>, style: SilenceStyle): Boolean {
        var outcome = SilencerState.StartOutcome.ALREADY_ACTIVE
        dataStore.edit { prefs ->
            outcome = SilencerState.start(prefs, claim) { ringerModeController.currentMode }
        }
        if (outcome == SilencerState.StartOutcome.ALREADY_ACTIVE) return false
        if (!ringerModeController.silence(style)) {
            // Released without touching the ringer: it never changed, so there is
            // nothing to restore, only the claim (and a just-captured prior mode) to undo.
            dataStore.edit { prefs ->
                prefs.remove(claim)
                if (SilencerState.activeWindowCount(prefs) == 0) prefs.remove(SilencerState.GLOBAL_PRIOR_MODE)
            }
            return false
        }
        // The widget's countdown needs to keep visibly ticking down while a window
        // runs — only start the tick loop when going from idle (already running
        // for any subsequent overlapping window).
        if (outcome == SilencerState.StartOutcome.STARTED_FROM_IDLE) widgetTickScheduler.start()
        return true
    }

    /**
     * Call when a window ends. Only actually changes the ringer if this was the last
     * active window — see [WindowEndResult]. A "sound is back" notification should
     * only ever be posted for [WindowEndResult.Restored].
     */
    suspend fun onWindowEnd(claim: Preferences.Key<Boolean>, policy: RevertPolicy): WindowEndResult {
        var result: WindowEndResult = WindowEndResult.NotActive
        dataStore.edit { prefs -> result = SilencerState.end(prefs, claim, policy) }
        val restored = result as? WindowEndResult.Restored ?: return result
        // Nothing is silencing anymore — stop waking the device every minute for a
        // countdown that no longer exists.
        widgetTickScheduler.stop()
        // If the system refuses the change (DND access revoked mid-window) the phone
        // is still silent — don't let the caller announce that sound is back.
        return if (ringerModeController.setMode(restored.mode)) restored else WindowEndResult.StillSilenced
    }
}

package com.ringfence.silentscheduler.quicksilence.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.data.toEnumOrDefault
import com.ringfence.silentscheduler.core.notification.SilenceEndAction
import com.ringfence.silentscheduler.core.notification.SilenceNotifier
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.core.ringer.SilencerState
import com.ringfence.silentscheduler.core.ringer.WindowEndResult
import com.ringfence.silentscheduler.core.ringer.toFriendlyRingerModeName
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceState
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import com.ringfence.silentscheduler.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Fixed, not derived from the (locale-dependent, and elsewhere user-renamable per
// schedule) display label — see ScheduleTriggerHandler's scheduleId-keyed equivalent
// for why a notification's id must never be computed from text that can change out
// from under an already-posted notification.
private const val QUICK_SILENCE_SESSION_KEY = "quick_silence"

private object Keys {
    // The same key SilencerCoordinator counts as this session's claim on the ringer —
    // "is quick silence active" and "is quick silence one of the windows currently
    // silencing" are one fact, so they are one key, only ever written by the coordinator.
    val IS_ACTIVE = SilencerState.QUICK_SILENCE_CLAIM
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
    private val silenceNotifier: SilenceNotifier,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationContext private val context: Context
) : QuickSilenceRepository {

    private val quickSilenceLabel: String
        get() = context.getString(R.string.quick_silence_label)

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
            prefs[Keys.START_TIME] = startTime
            prefs[Keys.END_TIME] = endTime
            // Captured now, not read again at revert time (R-09/R-07): a later
            // Settings change must not retroactively change an already-running session.
            prefs[Keys.REVERT_POLICY] = settings.revertPolicy.name
        }
        // Starting while a session is already running (a second widget chip tap, or
        // the sheet opened again) replaces that session's end time rather than
        // stacking a second window on top of it: the coordinator reports the claim
        // as already held and leaves its bookkeeping alone, so the single revert
        // that eventually runs still restores the ringer. Counting it twice used to
        // leave the phone silent after the session ended.
        val wasAlreadyActive = dataStore.data.first()[Keys.IS_ACTIVE] == true
        if (!wasAlreadyActive && !silencerCoordinator.onWindowStart(Keys.IS_ACTIVE, settings.silenceStyle)) {
            // The system refused the ringer change (DND access revoked) — nothing is
            // silenced, so don't post a notification or arm a revert for it.
            widgetRefresher.refresh()
            return
        }
        silenceNotifier.notifySilenceStarted(QUICK_SILENCE_SESSION_KEY, quickSilenceLabel, settings.notificationStyle, endTime, SilenceEndAction.QuickSilence)
        alarmScheduler.scheduleRevert(endTime)
        widgetRefresher.refresh()
    }

    override suspend fun revertSilence() {
        val policy = dataStore.data.first()[Keys.REVERT_POLICY].toEnumOrDefault(RevertPolicy.RESTORE)
        // Safe to call when no session is running (the revert alarm, the Dashboard's
        // own expiry check and the widget's can all notice the same expired session
        // within moments of each other): only the call that actually releases the
        // claim gets Restored/StillSilenced, every other one gets NotActive and
        // changes nothing. Previously each duplicate decremented the shared count
        // again, un-silencing a schedule that was still running or forcing the
        // ringer to Normal regardless of what it had been before.
        when (val result = silencerCoordinator.onWindowEnd(Keys.IS_ACTIVE, policy)) {
            is WindowEndResult.Restored -> {
                val notificationStyle = settingsRepository.observeSettings().first().notificationStyle
                silenceNotifier.notifySilenceEnded(QUICK_SILENCE_SESSION_KEY, quickSilenceLabel, notificationStyle, result.mode.toFriendlyRingerModeName())
            }
            // Another window is still silencing (no "sound is back" to announce), or
            // this session had already ended — either way its own ongoing
            // notification must not be left showing a countdown.
            WindowEndResult.StillSilenced -> silenceNotifier.cancelActiveNotification(QUICK_SILENCE_SESSION_KEY)
            WindowEndResult.NotActive -> {
                silenceNotifier.cancelActiveNotification(QUICK_SILENCE_SESSION_KEY)
                return
            }
        }
        alarmScheduler.cancelRevert()
        widgetRefresher.refresh()
    }

    override suspend fun reconcileIfExpired() {
        val prefs = dataStore.data.first()
        val isActive = prefs[Keys.IS_ACTIVE] ?: false
        val endTime = prefs[Keys.END_TIME] ?: 0L
        if (!isActive) return
        if (endTime <= System.currentTimeMillis()) {
            revertSilence()
        } else {
            // Still running: make sure its revert alarm exists. A reboot, force-stop
            // or app update clears it while the session itself stays marked active,
            // and nothing else re-arms it — the phone then stayed silent past the
            // end time until the app happened to be opened. Re-setting an alarm
            // that is still pending just replaces it with an identical one.
            alarmScheduler.scheduleRevert(endTime)
        }
    }
}

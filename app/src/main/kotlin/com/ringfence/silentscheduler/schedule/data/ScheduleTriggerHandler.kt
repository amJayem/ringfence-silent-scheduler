package com.ringfence.silentscheduler.schedule.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ringfence.silentscheduler.core.notification.SilenceNotifier
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.core.ringer.toFriendlyRingerModeName
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared by [ScheduleTriggerReceiver] (fired by AlarmManager at the natural end
 * time, passing "now" as the re-arm reference), the Dashboard's manual "End
 * silence now" action (which must pass the occurrence's own natural end time
 * instead — see [ScheduleAlarmScheduler.scheduleNextOccurrence]), and
 * [SchedulingScheduleRepository] (disabling/deleting a currently-active schedule).
 *
 * Depends on the concrete [ScheduleRepositoryImpl], not the [com.ringfence.silentscheduler.schedule.domain.ScheduleRepository]
 * interface — that interface resolves to [SchedulingScheduleRepository], which itself
 * depends on this class, so depending on the interface here would be a Hilt
 * dependency cycle.
 *
 * Ringer capture/restore is delegated to [SilencerCoordinator], shared with quick
 * silence — this schedule only tracks *whether it is one of the currently active
 * windows* (for its own idempotency), never a captured mode of its own (R-18).
 */
@Singleton
class ScheduleTriggerHandler @Inject constructor(
    private val repository: ScheduleRepositoryImpl,
    private val silencerCoordinator: SilencerCoordinator,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val preferencesDataStore: DataStore<Preferences>,
    private val settingsRepository: SettingsRepository,
    private val silenceNotifier: SilenceNotifier
) {
    /**
     * Idempotent: if this schedule is already tracked as one of the active windows,
     * this is a no-op. That matters because [SchedulingScheduleRepository] calls this
     * synchronously when re-enabling a schedule whose window is already active (so the
     * toggle feels instant instead of waiting on AlarmManager's async delivery) — the
     * real alarm still fires moments later and would otherwise double-count this
     * schedule against the shared active-window count, breaking the eventual revert.
     */
    suspend fun handleStart(scheduleId: String) {
        val key = activeKey(scheduleId)
        if (preferencesDataStore.data.first()[key] == true) {
            Log.i(TAG, "START $scheduleId: already active, skipping duplicate silence")
            return
        }
        val schedule = repository.observeSchedules().first().find { it.id == scheduleId }
        val notificationStyle = settingsRepository.observeSettings().first().notificationStyle
        preferencesDataStore.edit { prefs -> prefs[key] = true }
        silencerCoordinator.onWindowStart(schedule?.silenceStyle ?: SilenceStyle.FULL_SILENT)
        silenceNotifier.notifySilenceStarted(schedule?.label ?: "Schedule", notificationStyle)
        Log.i(TAG, "START $scheduleId: now silencing")
    }

    suspend fun handleEnd(scheduleId: String, referenceTimeForRearm: LocalDateTime) {
        val key = activeKey(scheduleId)
        if (preferencesDataStore.data.first()[key] == true) {
            preferencesDataStore.edit { prefs -> prefs.remove(key) }
            val schedule = repository.observeSchedules().first().find { it.id == scheduleId }
            val restoredMode = silencerCoordinator.onWindowEnd(schedule?.revertPolicy ?: RevertPolicy.RESTORE)
            Log.i(TAG, "END $scheduleId: restoredMode=$restoredMode (null means another window is still active)")
            // Only this schedule's own end notification fires, and only if the phone
            // actually went un-silent — if another window is still silencing,
            // restoredMode is null and nothing should claim "sound is back".
            if (restoredMode != null) {
                val notificationStyle = settingsRepository.observeSettings().first().notificationStyle
                silenceNotifier.notifySilenceEnded(scheduleLabel(scheduleId), notificationStyle, restoredMode.toFriendlyRingerModeName())
            }
        } else {
            Log.i(TAG, "END $scheduleId: was not tracked active, nothing to revert")
        }

        // Cancels any still-pending natural end alarm for today's occurrence — matters
        // for the manual early-end path, where that alarm hasn't fired yet.
        alarmScheduler.cancelOccurrence(scheduleId)

        val schedule = repository.observeSchedules().first().find { it.id == scheduleId }
        if (schedule != null && schedule.isEnabled) {
            alarmScheduler.scheduleNextOccurrence(schedule, referenceTimeForRearm)
        } else {
            Log.i(TAG, "END $scheduleId: schedule missing or disabled, not re-arming")
        }
    }

    /**
     * Ends this schedule's window early without touching alarms or scheduling a next
     * occurrence, since the caller is disabling or deleting the schedule, not ending a
     * still-recurring window. Used by [SchedulingScheduleRepository]: previously,
     * disabling an active schedule only canceled its future end alarm, leaving the
     * phone stuck silent with nothing left to ever revert it.
     */
    suspend fun revertIfCurrentlySilencing(scheduleId: String) {
        val key = activeKey(scheduleId)
        if (preferencesDataStore.data.first()[key] != true) return
        preferencesDataStore.edit { prefs -> prefs.remove(key) }
        val schedule = repository.observeSchedules().first().find { it.id == scheduleId }
        val restoredMode = silencerCoordinator.onWindowEnd(schedule?.revertPolicy ?: RevertPolicy.RESTORE)
        Log.i(TAG, "revertIfCurrentlySilencing $scheduleId: restoredMode=$restoredMode")
    }

    private suspend fun scheduleLabel(scheduleId: String): String =
        repository.observeSchedules().first().find { it.id == scheduleId }?.label ?: "Schedule"

    /** Tracks only "is this schedule one of the active windows" — the captured mode itself lives in [SilencerCoordinator]. */
    private fun activeKey(scheduleId: String) = booleanPreferencesKey("schedule_active_$scheduleId")

    private companion object {
        const val TAG = "ScheduleTriggerHandler"
    }
}

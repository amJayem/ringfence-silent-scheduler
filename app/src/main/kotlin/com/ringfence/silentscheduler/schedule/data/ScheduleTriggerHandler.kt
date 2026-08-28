package com.ringfence.silentscheduler.schedule.data

import android.media.AudioManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ringfence.silentscheduler.core.notification.SilenceNotifier
import com.ringfence.silentscheduler.core.ringer.RingerModeController
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
 */
@Singleton
class ScheduleTriggerHandler @Inject constructor(
    private val repository: ScheduleRepositoryImpl,
    private val ringerModeController: RingerModeController,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val preferencesDataStore: DataStore<Preferences>,
    private val settingsRepository: SettingsRepository,
    private val silenceNotifier: SilenceNotifier
) {
    /**
     * Idempotent: if this schedule already has a stored previous-mode snapshot, it's
     * already mid-silence, so this is a no-op. That matters because
     * [SchedulingScheduleRepository] calls this synchronously when re-enabling a
     * schedule whose window is already active (so the toggle feels instant instead
     * of waiting on AlarmManager's async delivery) — the real alarm still fires
     * moments later and would otherwise overwrite the correct previous mode with
     * "silent" (the mode this very call just set), breaking the eventual revert.
     */
    suspend fun handleStart(scheduleId: String) {
        val key = previousModeKey(scheduleId)
        if (preferencesDataStore.data.first().contains(key)) {
            Log.i(TAG, "START $scheduleId: already active, skipping duplicate silence")
            return
        }
        val settings = settingsRepository.observeSettings().first()
        val modeBeforeSilencing = ringerModeController.currentMode
        preferencesDataStore.edit { prefs -> prefs[key] = modeBeforeSilencing }
        ringerModeController.silence(settings.silenceStyle)
        silenceNotifier.notifySilenceStarted(scheduleLabel(scheduleId), settings.notificationStyle)
        Log.i(TAG, "START $scheduleId: captured previous mode=$modeBeforeSilencing, now SILENT")
    }

    suspend fun handleEnd(scheduleId: String, referenceTimeForRearm: LocalDateTime) {
        val previousMode = preferencesDataStore.data.first()[previousModeKey(scheduleId)]
            ?: AudioManager.RINGER_MODE_NORMAL
        ringerModeController.setMode(previousMode)
        preferencesDataStore.edit { prefs -> prefs.remove(previousModeKey(scheduleId)) }
        val notificationStyle = settingsRepository.observeSettings().first().notificationStyle
        silenceNotifier.notifySilenceEnded(scheduleLabel(scheduleId), notificationStyle)
        Log.i(TAG, "END $scheduleId: restored mode=$previousMode")

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
     * Reverts the ringer mode if this schedule is the one currently silencing the
     * phone (i.e. it has a stored pre-silence snapshot from its START firing) —
     * without touching alarms or scheduling a next occurrence, since the caller is
     * disabling or deleting the schedule, not ending a still-recurring window.
     * Used by [SchedulingScheduleRepository]: previously, disabling an active
     * schedule only canceled its future end alarm, leaving the phone silenced with
     * nothing left to ever revert it.
     */
    suspend fun revertIfCurrentlySilencing(scheduleId: String) {
        val key = previousModeKey(scheduleId)
        val previousMode = preferencesDataStore.data.first()[key] ?: return
        ringerModeController.setMode(previousMode)
        preferencesDataStore.edit { prefs -> prefs.remove(key) }
        Log.i(TAG, "revertIfCurrentlySilencing $scheduleId: restored mode=$previousMode")
    }

    private suspend fun scheduleLabel(scheduleId: String): String =
        repository.observeSchedules().first().find { it.id == scheduleId }?.label ?: "Schedule"

    private fun previousModeKey(scheduleId: String) = intPreferencesKey("schedule_prev_ringer_mode_$scheduleId")

    private companion object {
        const val TAG = "ScheduleTriggerHandler"
    }
}

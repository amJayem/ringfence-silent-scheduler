package com.ringfence.silentscheduler.schedule.data

import android.media.AudioManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ringfence.silentscheduler.core.ringer.RingerModeController
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared by [ScheduleTriggerReceiver] (fired by AlarmManager at the natural end
 * time, passing "now" as the re-arm reference) and the Dashboard's manual "End
 * silence now" action (which must pass the occurrence's own natural end time
 * instead — see [ScheduleAlarmScheduler.scheduleNextOccurrence]).
 */
@Singleton
class ScheduleTriggerHandler @Inject constructor(
    private val repository: ScheduleRepository,
    private val ringerModeController: RingerModeController,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val preferencesDataStore: DataStore<Preferences>
) {
    suspend fun handleStart(scheduleId: String) {
        val modeBeforeSilencing = ringerModeController.currentMode
        preferencesDataStore.edit { prefs -> prefs[previousModeKey(scheduleId)] = modeBeforeSilencing }
        ringerModeController.silence()
        Log.i(TAG, "START $scheduleId: captured previous mode=$modeBeforeSilencing, now SILENT")
    }

    suspend fun handleEnd(scheduleId: String, referenceTimeForRearm: LocalDateTime) {
        val previousMode = preferencesDataStore.data.first()[previousModeKey(scheduleId)]
            ?: AudioManager.RINGER_MODE_NORMAL
        ringerModeController.setMode(previousMode)
        preferencesDataStore.edit { prefs -> prefs.remove(previousModeKey(scheduleId)) }
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

    private fun previousModeKey(scheduleId: String) = intPreferencesKey("schedule_prev_ringer_mode_$scheduleId")

    private companion object {
        const val TAG = "ScheduleTriggerHandler"
    }
}

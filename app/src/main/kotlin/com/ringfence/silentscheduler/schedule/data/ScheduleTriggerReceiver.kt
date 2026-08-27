package com.ringfence.silentscheduler.schedule.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ringfence.silentscheduler.core.ringer.RingerModeController
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires at a schedule's start (silence + remember the pre-silence ringer mode) and
 * end (restore that mode, then arm the following week's occurrence). Injecting the
 * public [ScheduleRepository] here is safe — this receiver only reads schedules and
 * calls the alarm scheduler directly; it never writes back through the repository,
 * so there's no risk of re-triggering [SchedulingScheduleRepository]'s CRUD hooks.
 */
@AndroidEntryPoint
class ScheduleTriggerReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var ringerModeController: RingerModeController

    @Inject
    lateinit var alarmScheduler: ScheduleAlarmScheduler

    @Inject
    lateinit var preferencesDataStore: DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        val action = intent.action ?: return
        Log.i(TAG, "onReceive: action=$action scheduleId=$scheduleId")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_START -> handleStart(scheduleId)
                    ACTION_END -> handleEnd(scheduleId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleStart(scheduleId: String) {
        val modeBeforeSilencing = ringerModeController.currentMode
        preferencesDataStore.edit { prefs ->
            prefs[previousModeKey(scheduleId)] = modeBeforeSilencing
        }
        ringerModeController.silence()
        Log.i(TAG, "START $scheduleId: captured previous mode=$modeBeforeSilencing, now SILENT")
    }

    private suspend fun handleEnd(scheduleId: String) {
        val previousMode = preferencesDataStore.data.first()[previousModeKey(scheduleId)]
            ?: AudioManager.RINGER_MODE_NORMAL
        ringerModeController.setMode(previousMode)
        preferencesDataStore.edit { prefs -> prefs.remove(previousModeKey(scheduleId)) }
        Log.i(TAG, "END $scheduleId: restored mode=$previousMode")

        val schedule = repository.observeSchedules().first().find { it.id == scheduleId }
        if (schedule != null && schedule.isEnabled) {
            alarmScheduler.scheduleNextOccurrence(schedule)
        } else {
            Log.i(TAG, "END $scheduleId: schedule missing or disabled, not re-arming")
        }
    }

    private fun previousModeKey(scheduleId: String) = intPreferencesKey("schedule_prev_ringer_mode_$scheduleId")

    companion object {
        const val ACTION_START = "com.ringfence.silentscheduler.schedule.ACTION_START"
        const val ACTION_END = "com.ringfence.silentscheduler.schedule.ACTION_END"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        private const val TAG = "ScheduleTriggerReceiver"
    }
}

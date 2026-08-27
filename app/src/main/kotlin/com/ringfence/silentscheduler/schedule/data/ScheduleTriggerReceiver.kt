package com.ringfence.silentscheduler.schedule.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/** Thin AlarmManager entry point — all the actual logic lives in [ScheduleTriggerHandler]. */
@AndroidEntryPoint
class ScheduleTriggerReceiver : BroadcastReceiver() {

    @Inject
    lateinit var handler: ScheduleTriggerHandler

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        val action = intent.action ?: return
        Log.i(TAG, "onReceive: action=$action scheduleId=$scheduleId")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_START -> handler.handleStart(scheduleId)
                    ACTION_END -> handler.handleEnd(scheduleId, LocalDateTime.now())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.ringfence.silentscheduler.schedule.ACTION_START"
        const val ACTION_END = "com.ringfence.silentscheduler.schedule.ACTION_END"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        private const val TAG = "ScheduleTriggerReceiver"
    }
}

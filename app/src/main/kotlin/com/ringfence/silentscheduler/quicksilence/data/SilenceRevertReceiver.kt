package com.ringfence.silentscheduler.quicksilence.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fired by AlarmManager to revert the ringer mode even if the app has been killed
 * since Quick Silence was started — the DataStore-persisted previous ringer mode is
 * what makes this reliable across process death.
 */
@AndroidEntryPoint
class SilenceRevertReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: QuickSilenceRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                repository.revertSilence()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

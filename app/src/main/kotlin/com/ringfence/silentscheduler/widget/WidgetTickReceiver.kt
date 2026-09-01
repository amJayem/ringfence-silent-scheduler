package com.ringfence.silentscheduler.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fired every minute by [WidgetTickScheduler] while at least one window is active. */
@AndroidEntryPoint
class WidgetTickReceiver : BroadcastReceiver() {

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                widgetRefresher.refresh()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

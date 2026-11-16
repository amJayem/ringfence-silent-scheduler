package com.ringfence.silentscheduler.schedule.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.widget.WidgetTickScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fired periodically by [ScheduleReconciliationScheduler], independent of app state. */
@AndroidEntryPoint
class ScheduleReconciliationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var quickSilenceRepository: QuickSilenceRepository

    @Inject
    lateinit var silencerCoordinator: SilencerCoordinator

    @Inject
    lateinit var widgetTickScheduler: WidgetTickScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Quick silence too: with no home-screen widget placed there is no
                // per-minute tick doing this, so this is the only background check
                // that notices a quick silence whose revert alarm was lost.
                quickSilenceRepository.reconcileIfExpired()
                repository.reconcileAlarms()
                // Same backstop RingfenceApp.onCreate already does on process start,
                // run here too: WidgetTickScheduler is only ever (re)started from that
                // one-time process-start check or a window's own 0->1 transition — if
                // its own alarm is the one an OEM battery manager drops while a window
                // is genuinely still active, nothing re-arms it again until the app
                // happens to be reopened, leaving the widget's countdown stale.
                if (silencerCoordinator.isAnyWindowActive()) {
                    widgetTickScheduler.start()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

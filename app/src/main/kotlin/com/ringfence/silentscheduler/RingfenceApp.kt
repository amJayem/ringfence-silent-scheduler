package com.ringfence.silentscheduler

import android.app.Application
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.widget.WidgetRefresher
import com.ringfence.silentscheduler.widget.WidgetTickScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RingfenceApp : Application() {

    @Inject
    lateinit var quickSilenceRepository: QuickSilenceRepository

    @Inject
    lateinit var silencerCoordinator: SilencerCoordinator

    @Inject
    lateinit var widgetTickScheduler: WidgetTickScheduler

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    override fun onCreate() {
        super.onCreate()
        // "Always reverts... on next launch" (see onboarding copy): if the revert
        // alarm was lost (force-stop, Doze, reboot) while a Quick Silence session was
        // running, the phone would otherwise stay muted forever with nothing left to
        // notice. Checked once here so it's fixed the moment the process starts, not
        // only once the Dashboard happens to be opened.
        CoroutineScope(Dispatchers.Default).launch {
            quickSilenceRepository.reconcileIfExpired()
            // The widget's own minute-tick alarm (WidgetTickScheduler) doesn't survive
            // a reboot or force-stop, same as every other AlarmManager alarm in this
            // app — re-arm it here if something is still genuinely active, and redraw
            // the widget immediately in case it went stale while the process was dead.
            if (silencerCoordinator.observeGlobalPriorMode().first() != null) {
                widgetTickScheduler.start()
            }
            widgetRefresher.refresh()
        }
    }
}

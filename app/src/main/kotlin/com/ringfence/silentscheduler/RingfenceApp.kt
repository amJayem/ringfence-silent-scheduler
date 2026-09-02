package com.ringfence.silentscheduler

import android.app.Application
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.widget.WidgetRefresher
import com.ringfence.silentscheduler.widget.WidgetTickScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltAndroidApp
class RingfenceApp : Application() {

    @Inject
    lateinit var quickSilenceRepository: QuickSilenceRepository

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

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
            // SilencerCoordinator's shared active-window count only reaches zero (and
            // so only ever restores the ringer) when every window that incremented it
            // also decrements it — a process death mid-transaction, or any other path
            // that starts a window without going through its matching end, leaves the
            // count stuck above zero and the phone silenced with no way back. Recompute
            // it from what's actually true right now and correct any drift.
            val now = LocalDateTime.now()
            val activeScheduleCount = scheduleRepository.observeSchedules().first().count { schedule ->
                schedule.isEnabled && schedule.repeatDays.isNotEmpty() &&
                    RecurringScheduleCalculator.nextOccurrence(schedule, now).let { occ ->
                        !occ.start.isAfter(now) && occ.end.isAfter(now)
                    }
            }
            val quickSilenceActive = quickSilenceRepository.observeState().first().isActive
            silencerCoordinator.reconcile(activeScheduleCount + if (quickSilenceActive) 1 else 0)
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

package com.ringfence.silentscheduler

import android.app.Application
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.data.ScheduleReconciliationScheduler
import com.ringfence.silentscheduler.schedule.data.ScheduleTriggerHandler
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
    lateinit var scheduleTriggerHandler: ScheduleTriggerHandler

    @Inject
    lateinit var widgetTickScheduler: WidgetTickScheduler

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    @Inject
    lateinit var scheduleReconciliationScheduler: ScheduleReconciliationScheduler

    override fun onCreate() {
        super.onCreate()
        // Unconditional and independent of anything being active right now — unlike
        // WidgetTickScheduler's minute tick (only armed while a window is already
        // silencing, so it can catch a lost *end* alarm but never a lost *start*),
        // this is the backstop for a schedule's start alarm itself being dropped
        // (Doze, an OEM battery manager, a revoked exact-alarm permission), which
        // would otherwise skip an entire window with no DND, no notification, and no
        // widget indicator until the app happened to be reopened.
        scheduleReconciliationScheduler.start()
        // "Always reverts... on next launch" (see onboarding copy): if the revert
        // alarm was lost (force-stop, Doze, reboot) while a Quick Silence session was
        // running, the phone would otherwise stay muted forever with nothing left to
        // notice. Checked once here so it's fixed the moment the process starts, not
        // only once the Dashboard happens to be opened.
        CoroutineScope(Dispatchers.Default).launch {
            quickSilenceRepository.reconcileIfExpired()
            // Same fix as the line above, but per-schedule: clears any schedule's own
            // "active" flag (and its stuck notification) left behind by a lost end
            // alarm — see the doc comment on reconcileStaleActiveFlags for why this
            // matters beyond just the notification.
            scheduleTriggerHandler.reconcileStaleActiveFlags()
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

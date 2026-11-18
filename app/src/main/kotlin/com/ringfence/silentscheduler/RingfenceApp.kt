package com.ringfence.silentscheduler

import android.app.Application
import com.ringfence.silentscheduler.core.reminder.ReviewReminderScheduler
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.data.ScheduleReconciliationScheduler
import com.ringfence.silentscheduler.schedule.data.ScheduleTriggerHandler
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import com.ringfence.silentscheduler.widget.WidgetRefresher
import com.ringfence.silentscheduler.widget.WidgetTickScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RingfenceApp : Application() {

    @Inject
    lateinit var quickSilenceRepository: QuickSilenceRepository

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

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var reviewReminderScheduler: ReviewReminderScheduler

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
        // Reactive, not a one-time check: re-arms (or cancels) the "review your
        // schedules" reminder whenever its enabled/amount/unit settings actually
        // change, and once on every process start (a collector's first emission is
        // the Flow's current value) — which is also what re-arms it after a reboot,
        // since BootRescheduleReceiver firing starts this same Application first.
        // distinctUntilChanged so merely opening Settings without touching this
        // section never resets an already-ticking countdown.
        CoroutineScope(Dispatchers.Default).launch {
            settingsRepository.observeSettings()
                .map { Triple(it.reviewReminderEnabled, it.reviewReminderAmount, it.reviewReminderUnit) }
                .distinctUntilChanged()
                .collect { (enabled, amount, unit) ->
                    if (enabled) reviewReminderScheduler.scheduleNext(amount, unit) else reviewReminderScheduler.cancel()
                }
        }
        // "Always reverts... on next launch" (see onboarding copy): if the revert
        // alarm was lost (force-stop, Doze, reboot) while a Quick Silence session was
        // running, the phone would otherwise stay muted forever with nothing left to
        // notice. Checked once here so it's fixed the moment the process starts, not
        // only once the Dashboard happens to be opened.
        CoroutineScope(Dispatchers.Default).launch {
            quickSilenceRepository.reconcileIfExpired()
            // Same fix as the line above, but per-schedule: clears any schedule's own
            // window that is still holding the ringer after a lost end alarm is ended
            // (ringer restored, notification cleared) — see reconcileStaleActiveFlags.
            scheduleTriggerHandler.reconcileStaleActiveFlags()
            // The widget's own minute-tick alarm (WidgetTickScheduler) doesn't survive
            // a reboot or force-stop, same as every other AlarmManager alarm in this
            // app — re-arm it here if something is still genuinely active, and redraw
            // the widget immediately in case it went stale while the process was dead.
            //
            // Deliberately no separate "recount the active windows" step here: an
            // earlier version recomputed a stored counter from the clock at this
            // point, and when the process had been started *by* a schedule's own
            // start alarm that recount raced the alarm's handler, counted the window
            // twice, and left the phone silent after the window ended. The count is
            // now derived from the per-window claims themselves (see SilencerState),
            // which the two reconcile calls above already keep truthful.
            if (silencerCoordinator.isAnyWindowActive()) {
                widgetTickScheduler.start()
            }
            widgetRefresher.refresh()
        }
    }
}

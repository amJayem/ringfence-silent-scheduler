package com.ringfence.silentscheduler.schedule.data

import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.widget.WidgetRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the plain DataStore-backed [ScheduleRepositoryImpl] so every CRUD change
 * also keeps AlarmManager — and, where relevant, the live ringer mode — in sync.
 * Callers (ViewModels) go through the [ScheduleRepository] interface and never need
 * to remember to call the alarm scheduler or trigger handler themselves.
 */
@Singleton
class SchedulingScheduleRepository @Inject constructor(
    private val delegate: ScheduleRepositoryImpl,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val triggerHandler: ScheduleTriggerHandler,
    private val widgetRefresher: WidgetRefresher
) : ScheduleRepository {

    override fun observeSchedules(): Flow<List<Schedule>> = delegate.observeSchedules()

    // Re-arming alarms alone isn't enough: it only ever schedules a *future*
    // occurrence, so it can't catch a schedule that's still stuck marked active from
    // a *past* window whose own end alarm was the one that got lost (the process
    // kill that clears AlarmManager's queue doesn't discriminate between a schedule's
    // start and end alarm — either can be the one missing) — the phone stays silenced
    // indefinitely with nothing left to ever revert it, exactly the class of bug
    // quickSilenceRepository.reconcileIfExpired() already exists to catch for quick
    // silence. Checking each schedule's real current-or-next occurrence against "now"
    // and routing to handleStart/handleEnd (both already idempotent no-ops when
    // nothing is actually wrong) closes the same gap here.
    override suspend fun reconcileAlarms() {
        // First: anything still holding the ringer after its window is over (including
        // a schedule that has since been disabled or deleted) is ended properly.
        triggerHandler.reconcileStaleActiveFlags()
        val now = LocalDateTime.now()
        delegate.observeSchedules().first()
            .filter { it.isEnabled && it.repeatDays.isNotEmpty() }
            .forEach { schedule ->
                // occurrenceFor (not the raw calculator): a window the user ended early
                // must not be mistaken for a missed start and silenced again.
                if (triggerHandler.occurrenceFor(schedule, now).isActiveAt(now)) {
                    // Mid-window right now: re-arm this occurrence's end alarm and make
                    // sure it's actually silencing (a no-op if a lost start alarm isn't
                    // actually this schedule's problem today).
                    alarmScheduler.scheduleNextOccurrence(schedule)
                    triggerHandler.handleStart(schedule.id)
                } else {
                    // Not in a window right now — this reverts if the schedule was
                    // still tracked active from a past window (the stuck-silent case)
                    // and re-arms the real next occurrence either way.
                    triggerHandler.handleNaturalEnd(schedule.id)
                }
            }
    }

    override suspend fun addOrUpdateSchedule(schedule: Schedule) {
        delegate.addOrUpdateSchedule(schedule)
        rearm(schedule)
        widgetRefresher.refresh()
    }

    override suspend fun deleteSchedule(id: String) {
        // disable() must run first: it needs to still find this schedule by id (for its
        // label and revert policy) to correctly revert the ringer and update the
        // notification if it's currently silencing — deleting it from the store first
        // would leave nothing to look up.
        disable(id)
        delegate.deleteSchedule(id)
        widgetRefresher.refresh()
    }

    override suspend fun setEnabled(id: String, isEnabled: Boolean) {
        delegate.setEnabled(id, isEnabled)
        val schedule = delegate.observeSchedules().first().find { it.id == id }
        if (schedule == null) {
            disable(id)
        } else {
            rearm(schedule)
        }
        widgetRefresher.refresh()
    }

    private suspend fun rearm(schedule: Schedule) {
        if (!schedule.isEnabled || schedule.repeatDays.isEmpty()) {
            disable(schedule.id)
            return
        }

        // Saving or re-enabling a schedule is the user saying "apply this as it is
        // now" — an earlier "End silence now" on its previous shape no longer applies.
        triggerHandler.clearEndedEarly(schedule.id)
        alarmScheduler.scheduleNextOccurrence(schedule)

        val now = LocalDateTime.now()
        if (RecurringScheduleCalculator.nextOccurrence(schedule, now).isActiveAt(now)) {
            // The window is already in progress (re-enabling mid-window, or a
            // brand-new schedule created to cover right now): silence immediately
            // instead of waiting on AlarmManager's async delivery — that round trip
            // can take a few seconds, which felt like a lag on the enable toggle.
            // triggerHandler.handleStart is idempotent, so the real alarm firing
            // moments later is a harmless no-op.
            triggerHandler.handleStart(schedule.id)
        } else {
            // The schedule was edited while it was silencing so that "now" is no
            // longer inside its window: the alarms above were just replaced with the
            // new times, so the old end alarm will never fire — end it here.
            triggerHandler.revertIfCurrentlySilencing(schedule.id)
        }
    }

    /**
     * Cancels future alarms AND reverts the ringer mode right now if this schedule
     * happened to be the one currently silencing the phone — previously, disabling
     * or deleting an active schedule only canceled its future end alarm, leaving
     * the phone stuck silent with nothing left to ever revert it.
     */
    private suspend fun disable(scheduleId: String) {
        alarmScheduler.cancelOccurrence(scheduleId)
        triggerHandler.revertIfCurrentlySilencing(scheduleId)
        triggerHandler.clearEndedEarly(scheduleId)
    }
}

package com.ringfence.silentscheduler.schedule.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ringfence.silentscheduler.core.notification.SilenceEndAction
import com.ringfence.silentscheduler.core.notification.SilenceNotifier
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilencerCoordinator
import com.ringfence.silentscheduler.core.ringer.SilencerState
import com.ringfence.silentscheduler.core.ringer.WindowEndResult
import com.ringfence.silentscheduler.core.ringer.toFriendlyRingerModeName
import com.ringfence.silentscheduler.schedule.domain.RecurringScheduleCalculator
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleOccurrence
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import com.ringfence.silentscheduler.widget.WidgetRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared by [ScheduleTriggerReceiver] (fired by AlarmManager at a window's start and
 * natural end), the manual "End silence" actions on the Dashboard, widget and
 * notification ([endEarly]), and [SchedulingScheduleRepository] (reconciliation, and
 * disabling/deleting a currently-active schedule).
 *
 * Depends on the concrete [ScheduleRepositoryImpl], not the [com.ringfence.silentscheduler.schedule.domain.ScheduleRepository]
 * interface — that interface resolves to [SchedulingScheduleRepository], which itself
 * depends on this class, so depending on the interface here would be a Hilt
 * dependency cycle.
 *
 * Ringer capture/restore is delegated to [SilencerCoordinator], shared with quick
 * silence — a schedule holds only its own claim there ("is one of the currently
 * active windows"), never a captured mode of its own (R-18).
 */
@Singleton
class ScheduleTriggerHandler @Inject constructor(
    private val repository: ScheduleRepositoryImpl,
    private val silencerCoordinator: SilencerCoordinator,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val preferencesDataStore: DataStore<Preferences>,
    private val settingsRepository: SettingsRepository,
    private val silenceNotifier: SilenceNotifier,
    private val widgetRefresher: WidgetRefresher
) {
    /**
     * Idempotent: if this schedule is already one of the active windows, this is a
     * no-op. That matters because [SchedulingScheduleRepository] calls this
     * synchronously when re-enabling a schedule whose window is already active (so the
     * toggle feels instant instead of waiting on AlarmManager's async delivery) and
     * again from its periodic reconciliation — the real alarm still fires as well, and
     * all of them can arrive within moments of each other. The "already active" check
     * and the claim are one DataStore transaction inside [SilencerCoordinator], so
     * only one of them ever starts the window.
     *
     * Also a no-op when the schedule no longer exists, is disabled, or isn't actually
     * inside one of its windows right now (a start alarm delivered late enough that
     * the window it belonged to is already over) — silencing in any of those cases
     * would have nothing left that is guaranteed to un-silence it.
     */
    suspend fun handleStart(scheduleId: String) {
        val schedule = repository.observeSchedules().first().find { it.id == scheduleId }
        if (schedule == null || !schedule.isEnabled || schedule.repeatDays.isEmpty()) {
            Log.i(TAG, "START $scheduleId: schedule missing, disabled or has no days — not silencing")
            return
        }
        val now = LocalDateTime.now()
        val occurrence = occurrenceFor(schedule, now)
        if (!occurrence.isActiveAt(now)) {
            Log.i(TAG, "START $scheduleId: not inside a window right now (or the user ended it early) — not silencing")
            return
        }
        val notificationStyle = settingsRepository.observeSettings().first().notificationStyle
        if (!silencerCoordinator.onWindowStart(SilencerState.scheduleClaim(scheduleId), schedule.silenceStyle)) {
            Log.i(TAG, "START $scheduleId: already active (or ringer change refused), nothing to do")
            return
        }
        silenceNotifier.notifySilenceStarted(
            scheduleId,
            schedule.label,
            notificationStyle,
            occurrence.end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            SilenceEndAction.Schedule(scheduleId)
        )
        Log.i(TAG, "START $scheduleId: now silencing")
        widgetRefresher.refresh()
    }

    /**
     * The window's own end: fired by AlarmManager at its natural end time, and by the
     * periodic reconciliation for any schedule that isn't inside a window. Ends the
     * window if it was still active and arms the next occurrence either way.
     */
    suspend fun handleNaturalEnd(scheduleId: String) {
        val schedule = repository.observeSchedules().first().find { it.id == scheduleId }
        val now = LocalDateTime.now()
        val reference = effectiveReference(scheduleId, now)
        if (schedule != null && schedule.isArmable()) {
            val occurrence = RecurringScheduleCalculator.nextOccurrence(schedule, reference)
            if (occurrence.isActiveAt(now)) {
                val realEndMillis = occurrence.end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (realEndMillis > System.currentTimeMillis()) {
                    // An end alarm armed for a time that no longer matches the clock
                    // (the time zone or system time changed after it was set): the
                    // window is genuinely still running, so keep silencing and just
                    // re-arm its real end.
                    Log.i(TAG, "END $scheduleId: window is still in progress, re-arming instead of ending")
                    alarmScheduler.scheduleNextOccurrence(schedule, reference)
                    return
                }
                // The window's real end has passed but the wall clock reads a time
                // inside it again: the hour that repeats when daylight saving ends
                // (a 1:15-1:45 window finishes, then the clock goes back to 1:00).
                // Mark this occurrence as over until the wall clock passes its end,
                // exactly like a manual early end — otherwise every check would see
                // "inside the window, not silencing", re-arm alarms that are already
                // in the past, and start and end the same window over and over for
                // the rest of the repeated hour.
                preferencesDataStore.edit { prefs -> prefs[endedEarlyKey(scheduleId)] = occurrence.end.toString() }
                finishAndRearm(scheduleId, schedule, occurrence.end)
                return
            }
        }
        finishAndRearm(scheduleId, schedule, reference)
    }

    /**
     * The user ended this schedule's silence themselves (Dashboard button, widget, or
     * the notification's "End silence" action).
     *
     * If the schedule is inside a window right now, that occurrence's natural end is
     * remembered (see [observeEndedEarly]) so the rest of it counts as over everywhere
     * — otherwise the next reconciliation pass would see "enabled schedule, inside its
     * window, not silencing" and treat it as a missed start. The marker is written
     * *before* the window is released so no such pass can slip in between. If it isn't
     * inside a window (a stale notification tapped after the fact), nothing is
     * skipped: the next occurrence still runs normally.
     */
    suspend fun endEarly(scheduleId: String) {
        val schedule = repository.observeSchedules().first().find { it.id == scheduleId }
        val now = LocalDateTime.now()
        var reference = effectiveReference(scheduleId, now)
        if (schedule != null && schedule.isArmable()) {
            val occurrence = RecurringScheduleCalculator.nextOccurrence(schedule, reference)
            if (occurrence.isActiveAt(now)) {
                reference = occurrence.end
                preferencesDataStore.edit { prefs -> prefs[endedEarlyKey(scheduleId)] = reference.toString() }
            }
        }
        finishAndRearm(scheduleId, schedule, reference)
    }

    private suspend fun finishAndRearm(scheduleId: String, schedule: Schedule?, referenceTimeForRearm: LocalDateTime) {
        endWindow(scheduleId, schedule)

        // Cancels any still-pending natural end alarm for today's occurrence — matters
        // for the manual early-end path, where that alarm hasn't fired yet.
        alarmScheduler.cancelOccurrence(scheduleId)

        if (schedule != null && schedule.isArmable()) {
            alarmScheduler.scheduleNextOccurrence(schedule, referenceTimeForRearm)
        } else {
            Log.i(TAG, "END $scheduleId: schedule missing or disabled, not re-arming")
        }
        widgetRefresher.refresh()
    }

    /**
     * For each schedule the user ended early: the natural end of the occurrence they
     * ended. Pass the entry for a schedule to [RecurringScheduleCalculator.nextOccurrence]
     * wherever "is it active now / when is it next" is derived from the clock.
     * Entries whose time has passed are harmless (they no longer skip anything) and
     * are removed the next time the schedule is saved, toggled or deleted.
     */
    fun observeEndedEarly(): Flow<Map<String, LocalDateTime>> = preferencesDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(ENDED_EARLY_PREFIX) || value !is String) return@mapNotNull null
            val until = runCatching { LocalDateTime.parse(value) }.getOrNull() ?: return@mapNotNull null
            key.name.removePrefix(ENDED_EARLY_PREFIX) to until
        }.toMap()
    }

    /** This schedule's current-or-next occurrence, skipping one the user ended early. */
    suspend fun occurrenceFor(schedule: Schedule, now: LocalDateTime): ScheduleOccurrence =
        RecurringScheduleCalculator.nextOccurrence(schedule, effectiveReference(schedule.id, now))

    /**
     * Forgets an early end. Called when the schedule is saved, re-enabled, disabled
     * or deleted: the marker describes one occurrence of the schedule *as it was*, and
     * must not go on suppressing a window whose times the user has since changed.
     */
    suspend fun clearEndedEarly(scheduleId: String) {
        val key = endedEarlyKey(scheduleId)
        if (preferencesDataStore.data.first().contains(key)) {
            preferencesDataStore.edit { prefs -> prefs.remove(key) }
        }
    }

    private suspend fun effectiveReference(scheduleId: String, now: LocalDateTime): LocalDateTime {
        val until = observeEndedEarly().first()[scheduleId]
        return if (until != null && until.isAfter(now)) until else now
    }

    private fun Schedule.isArmable() = isEnabled && repeatDays.isNotEmpty()

    private fun endedEarlyKey(scheduleId: String) = stringPreferencesKey(ENDED_EARLY_PREFIX + scheduleId)

    /**
     * Ends this schedule's window early without touching alarms or scheduling a next
     * occurrence, since the caller is disabling or deleting the schedule, not ending a
     * still-recurring window. Used by [SchedulingScheduleRepository]: previously,
     * disabling an active schedule only canceled its future end alarm, leaving the
     * phone stuck silent with nothing left to ever revert it. Must be called before
     * the schedule is removed from the store (its label and revert policy are read
     * from there).
     */
    suspend fun revertIfCurrentlySilencing(scheduleId: String) {
        endWindow(scheduleId, repository.observeSchedules().first().find { it.id == scheduleId })
    }

    /**
     * Run on app process start (see [com.ringfence.silentscheduler.RingfenceApp]), on
     * every widget redraw, and from the periodic background reconciliation — mirrors
     * [com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository.reconcileIfExpired]
     * for schedules. A schedule's own end-of-window path (delivered by AlarmManager via
     * [ScheduleTriggerReceiver]) can be lost or late — Doze, a revoked exact-alarm
     * permission, a reboot, a process death — leaving this schedule still holding its
     * claim on the ringer after its window is over. Each such window is ended here
     * exactly as its end alarm would have ended it: the claim is released, the ringer
     * is restored if nothing else is still silencing, and the notification is cleared.
     * (An earlier version only cleared the flag and the notification, which left the
     * phone silent with nothing remaining that would ever restore it.)
     *
     * Iterates the claims rather than the schedule list so a claim whose schedule no
     * longer exists is released too.
     */
    suspend fun reconcileStaleActiveFlags() {
        val now = LocalDateTime.now()
        val activeIds = silencerCoordinator.activeScheduleIds()
        if (activeIds.isEmpty()) return
        val schedulesById = repository.observeSchedules().first().associateBy { it.id }
        activeIds.forEach { scheduleId ->
            val schedule = schedulesById[scheduleId]
            val stillActive = schedule != null && schedule.isEnabled && schedule.repeatDays.isNotEmpty() &&
                RecurringScheduleCalculator.nextOccurrence(schedule, now).isActiveAt(now)
            if (!stillActive) {
                Log.i(TAG, "reconcileStaleActiveFlags $scheduleId: window already over, ending it now")
                endWindow(scheduleId, schedule)
            }
        }
    }

    /**
     * The one place a schedule's window is released. [SilencerCoordinator.onWindowEnd]
     * decides atomically whether this call is the one that ends it, so any number of
     * callers (end alarm, manual end, reconciliation, disable/delete) can race here
     * and exactly one of them restores the ringer and announces it.
     */
    private suspend fun endWindow(scheduleId: String, schedule: Schedule?) {
        // Fetched before touching the claim or the ringer, and the notification call
        // below is made the very next suspend hop after the ringer revert — this got
        // cut to the minimum after a real device showed the OS freezing/killing this
        // broadcast-triggered process moments after the ringer was already reverted
        // but before a later notifySilenceEnded() call ran, leaving the "still
        // silencing" notification stuck with a negative countdown for hours.
        val notificationStyle = settingsRepository.observeSettings().first().notificationStyle
        val result = silencerCoordinator.onWindowEnd(
            SilencerState.scheduleClaim(scheduleId),
            schedule?.revertPolicy ?: RevertPolicy.RESTORE
        )
        Log.i(TAG, "END $scheduleId: $result")
        when (result) {
            // Only announced if the phone actually went un-silent.
            is WindowEndResult.Restored -> silenceNotifier.notifySilenceEnded(
                scheduleId,
                schedule?.label ?: FALLBACK_LABEL,
                notificationStyle,
                result.mode.toFriendlyRingerModeName()
            )
            // Another window is still silencing, or this one had already ended (a
            // prior end could have been killed before clearing its notification) —
            // either way this schedule's own "silence is active" notification is
            // stale. Harmless when nothing is posted.
            WindowEndResult.StillSilenced, WindowEndResult.NotActive ->
                silenceNotifier.cancelActiveNotification(scheduleId)
        }
    }

    private companion object {
        const val TAG = "ScheduleTriggerHandler"
        const val FALLBACK_LABEL = "Schedule"
        const val ENDED_EARLY_PREFIX = "schedule_ended_early_until_"
    }
}

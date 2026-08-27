package com.ringfence.silentscheduler.schedule.data

import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val triggerHandler: ScheduleTriggerHandler
) : ScheduleRepository {

    override fun observeSchedules(): Flow<List<Schedule>> = delegate.observeSchedules()

    override suspend fun addOrUpdateSchedule(schedule: Schedule) {
        delegate.addOrUpdateSchedule(schedule)
        rearm(schedule)
    }

    override suspend fun deleteSchedule(id: String) {
        delegate.deleteSchedule(id)
        disable(id)
    }

    override suspend fun setEnabled(id: String, isEnabled: Boolean) {
        delegate.setEnabled(id, isEnabled)
        val schedule = delegate.observeSchedules().first().find { it.id == id }
        if (schedule == null) {
            disable(id)
            return
        }
        rearm(schedule)
    }

    private suspend fun rearm(schedule: Schedule) {
        if (schedule.isEnabled) {
            alarmScheduler.scheduleNextOccurrence(schedule)
        } else {
            disable(schedule.id)
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
    }
}

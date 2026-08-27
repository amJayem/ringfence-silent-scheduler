package com.ringfence.silentscheduler.schedule.data

import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the plain DataStore-backed [ScheduleRepositoryImpl] so every CRUD change
 * also keeps AlarmManager in sync — callers (ViewModels) go through the
 * [ScheduleRepository] interface and never need to remember to call the alarm
 * scheduler themselves.
 */
@Singleton
class SchedulingScheduleRepository @Inject constructor(
    private val delegate: ScheduleRepositoryImpl,
    private val alarmScheduler: ScheduleAlarmScheduler
) : ScheduleRepository {

    override fun observeSchedules(): Flow<List<Schedule>> = delegate.observeSchedules()

    override suspend fun addOrUpdateSchedule(schedule: Schedule) {
        delegate.addOrUpdateSchedule(schedule)
        rearm(schedule)
    }

    override suspend fun deleteSchedule(id: String) {
        delegate.deleteSchedule(id)
        alarmScheduler.cancelOccurrence(id)
    }

    override suspend fun setEnabled(id: String, isEnabled: Boolean) {
        delegate.setEnabled(id, isEnabled)
        val schedule = delegate.observeSchedules().first().find { it.id == id } ?: return
        rearm(schedule)
    }

    private fun rearm(schedule: Schedule) {
        if (schedule.isEnabled) {
            alarmScheduler.scheduleNextOccurrence(schedule)
        } else {
            alarmScheduler.cancelOccurrence(schedule.id)
        }
    }
}

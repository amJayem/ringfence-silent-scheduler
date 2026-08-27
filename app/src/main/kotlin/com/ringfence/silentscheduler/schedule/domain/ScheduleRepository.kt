package com.ringfence.silentscheduler.schedule.domain

import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeSchedules(): Flow<List<Schedule>>
    suspend fun addOrUpdateSchedule(schedule: Schedule)
    suspend fun deleteSchedule(id: String)
    suspend fun setEnabled(id: String, isEnabled: Boolean)
}

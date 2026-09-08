package com.ringfence.silentscheduler.schedule.domain

import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeSchedules(): Flow<List<Schedule>>
    suspend fun addOrUpdateSchedule(schedule: Schedule)
    suspend fun deleteSchedule(id: String)
    suspend fun setEnabled(id: String, isEnabled: Boolean)

    /**
     * Re-arms every enabled schedule's next-occurrence alarm. AlarmManager only
     * clears its queue on a real reboot (handled separately by a boot receiver) or
     * an OS/OEM process kill — but a process kill doesn't fire any broadcast the app
     * can listen for, so a schedule whose alarm was lost that way would otherwise
     * stay silently unarmed until someone happens to edit or re-toggle it. Calling
     * this whenever the app is opened closes that gap without waiting on either.
     */
    suspend fun reconcileAlarms()
}

package com.ringfence.silentscheduler.schedule.data

import androidx.datastore.core.DataStore
import com.ringfence.silentscheduler.schedule.data.proto.ScheduleListProto
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<ScheduleListProto>
) : ScheduleRepository {

    override fun observeSchedules(): Flow<List<Schedule>> = dataStore.data.map { list ->
        list.schedulesList.map { it.toDomain() }
    }

    override suspend fun addOrUpdateSchedule(schedule: Schedule) {
        dataStore.updateData { current ->
            val withoutExisting = current.schedulesList.filterNot { it.id == schedule.id }
            current.toBuilder()
                .clearSchedules()
                .addAllSchedules(withoutExisting + schedule.toProto())
                .build()
        }
    }

    override suspend fun deleteSchedule(id: String) {
        dataStore.updateData { current ->
            current.toBuilder()
                .clearSchedules()
                .addAllSchedules(current.schedulesList.filterNot { it.id == id })
                .build()
        }
    }

    override suspend fun setEnabled(id: String, isEnabled: Boolean) {
        dataStore.updateData { current ->
            val updated = current.schedulesList.map {
                if (it.id == id) it.toBuilder().setIsEnabled(isEnabled).build() else it
            }
            current.toBuilder().clearSchedules().addAllSchedules(updated).build()
        }
    }

    // This is the plain DataStore-backed layer with no AlarmManager access by
    // design (see SchedulingScheduleRepository, the only implementation actually
    // bound to ScheduleRepository) — a no-op here, since there's nothing this class
    // itself could re-arm.
    override suspend fun reconcileAlarms() {}
}

package com.ringfence.silentscheduler.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Build-order step 4/5 verification harness only — exercises DataStore CRUD from a
 * throwaway UI so persistence-after-force-close can be checked on-device before the
 * real Dashboard (step 7) exists.
 */
@HiltViewModel
class ScheduleDebugViewModel @Inject constructor(
    private val repository: ScheduleRepository
) : ViewModel() {

    val schedules: StateFlow<List<Schedule>> = repository.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(schedule: Schedule) {
        viewModelScope.launch {
            repository.addOrUpdateSchedule(schedule)
        }
    }

    fun toggleEnabled(schedule: Schedule) {
        viewModelScope.launch {
            repository.setEnabled(schedule.id, !schedule.isEnabled)
        }
    }

    fun delete(schedule: Schedule) {
        viewModelScope.launch {
            repository.deleteSchedule(schedule.id)
        }
    }
}

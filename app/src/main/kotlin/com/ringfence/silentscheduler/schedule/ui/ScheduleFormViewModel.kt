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

/** Backs both the "add schedule" and "edit schedule" nav destinations. */
@HiltViewModel
class ScheduleFormViewModel @Inject constructor(
    private val repository: ScheduleRepository
) : ViewModel() {

    val schedules: StateFlow<List<Schedule>> = repository.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(schedule: Schedule) {
        viewModelScope.launch { repository.addOrUpdateSchedule(schedule) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteSchedule(id) }
    }
}

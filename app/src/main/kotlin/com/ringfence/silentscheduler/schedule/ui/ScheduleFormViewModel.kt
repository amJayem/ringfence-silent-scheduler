package com.ringfence.silentscheduler.schedule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs both the "add schedule" and "edit schedule" nav destinations. */
@HiltViewModel
class ScheduleFormViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val schedules: StateFlow<List<Schedule>> = repository.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pre-fills a brand-new schedule's silence style with the Settings-wide default. */
    val defaultSilenceStyle: StateFlow<SilenceStyle> = settingsRepository.observeSettings()
        .map { it.silenceStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SilenceStyle.FULL_SILENT)

    /** Pre-fills a brand-new schedule's revert policy with the Settings-wide default (R-05). */
    val defaultRevertPolicy: StateFlow<RevertPolicy> = settingsRepository.observeSettings()
        .map { it.revertPolicy }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RevertPolicy.RESTORE)

    fun save(schedule: Schedule) {
        viewModelScope.launch { repository.addOrUpdateSchedule(schedule) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteSchedule(id) }
    }
}

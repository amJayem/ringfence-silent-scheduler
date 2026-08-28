package com.ringfence.silentscheduler.quicksilence.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceState
import com.ringfence.silentscheduler.settings.domain.DEFAULT_DURATION_MINUTES
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickSilenceViewModel @Inject constructor(
    private val repository: QuickSilenceRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val state: StateFlow<QuickSilenceState> = repository.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickSilenceState())

    /** Seeds the Silent now sheet's initial duration selection. */
    val defaultDurationMinutes: StateFlow<Int> = settingsRepository.observeSettings()
        .map { it.defaultDurationMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_DURATION_MINUTES)

    fun startSilence(minutes: Int) {
        viewModelScope.launch {
            repository.startSilence(minutes)
        }
    }

    fun cancelEarly() {
        viewModelScope.launch {
            repository.revertSilence()
        }
    }
}

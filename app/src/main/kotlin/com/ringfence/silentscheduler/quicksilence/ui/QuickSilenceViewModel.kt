package com.ringfence.silentscheduler.quicksilence.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceState
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickSilenceViewModel @Inject constructor(
    private val repository: QuickSilenceRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val state: StateFlow<QuickSilenceState> = repository.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickSilenceState())

    /** Uses the Settings "DEFAULT SILENT DURATION" value — this screen has no duration picker of its own. */
    fun startSilence() {
        viewModelScope.launch {
            val minutes = settingsRepository.observeSettings().first().defaultDurationMinutes
            repository.startSilence(minutes * 60_000L)
        }
    }

    fun cancelEarly() {
        viewModelScope.launch {
            repository.revertSilence()
        }
    }
}

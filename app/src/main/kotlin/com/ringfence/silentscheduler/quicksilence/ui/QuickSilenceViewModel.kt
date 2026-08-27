package com.ringfence.silentscheduler.quicksilence.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.quicksilence.domain.DEFAULT_QUICK_SILENCE_DURATION_MILLIS
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickSilenceViewModel @Inject constructor(
    private val repository: QuickSilenceRepository
) : ViewModel() {

    val state: StateFlow<QuickSilenceState> = repository.observeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickSilenceState())

    /**
     * Fixed short duration for build-order step 3 verification only. This becomes
     * the Settings default-duration value (15m/30m/1h/2h, per DESIGN_NOTES.md) once
     * the Settings screen (step 8) exists.
     */
    fun startSilence(durationMillis: Long = DEFAULT_QUICK_SILENCE_DURATION_MILLIS) {
        viewModelScope.launch {
            repository.startSilence(durationMillis)
        }
    }

    fun cancelEarly() {
        viewModelScope.launch {
            repository.revertSilence()
        }
    }
}

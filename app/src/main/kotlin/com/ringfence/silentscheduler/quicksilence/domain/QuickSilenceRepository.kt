package com.ringfence.silentscheduler.quicksilence.domain

import kotlinx.coroutines.flow.Flow

interface QuickSilenceRepository {
    fun observeState(): Flow<QuickSilenceState>
    suspend fun startSilence(durationMillis: Long)
    suspend fun revertSilence()
}

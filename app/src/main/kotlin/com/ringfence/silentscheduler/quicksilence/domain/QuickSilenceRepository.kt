package com.ringfence.silentscheduler.quicksilence.domain

import kotlinx.coroutines.flow.Flow

interface QuickSilenceRepository {
    fun observeState(): Flow<QuickSilenceState>
    suspend fun startSilence(durationMinutes: Int)
    suspend fun revertSilence()

    /**
     * Self-heal for a missed revert alarm (force-stop, Doze, reboot): a session
     * whose end time has already passed but is still marked active is reverted
     * exactly as if the alarm had fired. Safe to call anytime — a no-op when
     * nothing is active or the active session hasn't actually expired yet.
     */
    suspend fun reconcileIfExpired()
}

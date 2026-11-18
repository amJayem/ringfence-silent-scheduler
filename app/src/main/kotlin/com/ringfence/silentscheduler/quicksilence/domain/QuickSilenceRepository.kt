package com.ringfence.silentscheduler.quicksilence.domain

import kotlinx.coroutines.flow.Flow

interface QuickSilenceRepository {
    fun observeState(): Flow<QuickSilenceState>
    suspend fun startSilence(durationMinutes: Int)
    suspend fun revertSilence()

    /**
     * Self-heal for a missed revert alarm (force-stop, Doze, reboot): a session
     * whose end time has already passed but is still marked active is reverted
     * exactly as if the alarm had fired, and a session that is still running gets
     * its revert alarm re-armed in case that alarm was the thing lost. Safe to call
     * anytime — a no-op when nothing is active.
     */
    suspend fun reconcileIfExpired()
}

package com.ringfence.silentscheduler.quicksilence.domain

import com.ringfence.silentscheduler.core.ringer.RevertPolicy

/**
 * @param revertPolicy captured from the Settings default when the session started
 * (R-09: quick silence has no per-session picker of its own, and a later Settings
 * change must not retroactively change an already-running session's revert policy).
 */
data class QuickSilenceState(
    val isActive: Boolean = false,
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val revertPolicy: RevertPolicy = RevertPolicy.RESTORE
)

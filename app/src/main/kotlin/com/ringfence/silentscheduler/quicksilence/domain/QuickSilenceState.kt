package com.ringfence.silentscheduler.quicksilence.domain

data class QuickSilenceState(
    val isActive: Boolean = false,
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L
)

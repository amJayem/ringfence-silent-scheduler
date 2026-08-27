package com.ringfence.silentscheduler.quicksilence.domain

data class QuickSilenceState(
    val isActive: Boolean = false,
    val endTimeMillis: Long = 0L
)

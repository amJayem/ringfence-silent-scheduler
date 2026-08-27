package com.ringfence.silentscheduler.quicksilence.domain

/**
 * Placeholder until Settings (step 8) wires the real default-duration setting
 * (15m/30m/1h/2h per DESIGN_NOTES.md). Shared by the Quick Silence tab and the
 * Dashboard's idle-state "Silent now" shortcut so both stay in sync.
 */
const val DEFAULT_QUICK_SILENCE_DURATION_MILLIS = 30_000L

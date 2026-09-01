package com.ringfence.silentscheduler.widget

/** One of the four duration chips shown on the 2x2 size (W-07/W-08). */
data class WidgetChip(
    val minutes: Int,
    val label: String,
    /** Highlighted per the design only while sound is on — a chip's "default" state
     * has nothing to show while a session is already running. */
    val isDefault: Boolean
)

data class WidgetUiState(
    val silent: Boolean,
    val kicker: String,
    val bigText: String,
    val subText: String,
    val footerText: String,
    val dndAccessGranted: Boolean,
    val chips: List<WidgetChip>
)

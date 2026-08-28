package com.ringfence.silentscheduler.core.ringer

import android.media.AudioManager

/** User-facing choice from Settings ("SILENCE STYLE") for what "silenced" means. */
enum class SilenceStyle {
    FULL_SILENT,
    VIBRATE_ONLY;

    fun toRingerMode(): Int = when (this) {
        FULL_SILENT -> AudioManager.RINGER_MODE_SILENT
        VIBRATE_ONLY -> AudioManager.RINGER_MODE_VIBRATE
    }
}

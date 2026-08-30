package com.ringfence.silentscheduler.core.ringer

import android.media.AudioManager

/**
 * Friendly name for a restored ringer mode, used in the "sound restored" notification
 * body. The mode restored on revert is whatever was captured before silencing started
 * (see [RingerModeController]) — it is not always Normal, so the notification must
 * name the mode it actually set, not assume Normal (R-19).
 */
fun Int.toFriendlyRingerModeName(): String = when (this) {
    AudioManager.RINGER_MODE_SILENT -> "Silent"
    AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
    else -> "Normal"
}

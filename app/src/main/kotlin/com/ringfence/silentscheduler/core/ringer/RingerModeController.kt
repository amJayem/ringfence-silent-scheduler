package com.ringfence.silentscheduler.core.ringer

import android.content.Context
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around AudioManager's ringer mode, shared by Quick Silence and
 * recurring schedules. Each caller is expected to snapshot [currentMode] itself
 * before calling [silence] and restore that exact snapshot later via [setMode] —
 * that per-caller snapshot (rather than a single global "previous mode") is what
 * makes overlapping silence windows compose correctly: whichever window was
 * outermost (started first) is the one whose restore actually reaches the user's
 * original setting, since inner/later windows only ever restore back to SILENT.
 */
@Singleton
class RingerModeController @Inject constructor(
    @ApplicationContext context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val currentMode: Int
        get() = audioManager.ringerMode

    /**
     * @return false if the system refused the change. Moving the ringer into or out
     * of Silent throws a SecurityException once Do Not Disturb access has been
     * revoked — and most callers are alarm-triggered broadcast receivers running
     * with no UI, where an uncaught exception is a background crash rather than
     * something the user can act on. The app's own entry screen re-checks DND access
     * on every resume and asks for it again, so reporting failure here (instead of
     * throwing) is what lets callers avoid claiming a silence that never happened.
     */
    fun setMode(mode: Int): Boolean = try {
        audioManager.ringerMode = mode
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "Ringer mode change refused — Do Not Disturb access is missing", e)
        false
    }

    fun silence(style: SilenceStyle): Boolean = setMode(style.toRingerMode())

    private companion object {
        const val TAG = "RingerModeController"
    }
}

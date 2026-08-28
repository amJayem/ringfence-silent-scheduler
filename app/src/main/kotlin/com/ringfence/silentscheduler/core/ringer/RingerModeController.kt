package com.ringfence.silentscheduler.core.ringer

import android.content.Context
import android.media.AudioManager
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

    fun setMode(mode: Int) {
        audioManager.ringerMode = mode
    }

    fun silence(style: SilenceStyle) {
        audioManager.ringerMode = style.toRingerMode()
    }
}

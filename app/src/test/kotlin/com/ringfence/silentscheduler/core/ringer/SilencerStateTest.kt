package com.ringfence.silentscheduler.core.ringer

import android.media.AudioManager
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.ringfence.silentscheduler.core.ringer.SilencerState.StartOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SilencerStateTest {

    private val quick = SilencerState.QUICK_SILENCE_CLAIM
    private val scheduleA = SilencerState.scheduleClaim("a")
    private val scheduleB = SilencerState.scheduleClaim("b")

    @Test
    fun `first window captures the prior mode and its end restores it`() {
        val prefs = mutablePreferencesOf()

        assertEquals(StartOutcome.STARTED_FROM_IDLE, SilencerState.start(prefs, scheduleA) { AudioManager.RINGER_MODE_VIBRATE })
        assertEquals(1, SilencerState.activeWindowCount(prefs))

        assertEquals(
            WindowEndResult.Restored(AudioManager.RINGER_MODE_VIBRATE),
            SilencerState.end(prefs, scheduleA, RevertPolicy.RESTORE)
        )
        assertEquals(0, SilencerState.activeWindowCount(prefs))
        assertNull(prefs[SilencerState.GLOBAL_PRIOR_MODE])
    }

    @Test
    fun `starting the same window twice counts it once`() {
        val prefs = mutablePreferencesOf()
        SilencerState.start(prefs, scheduleA) { AudioManager.RINGER_MODE_NORMAL }

        // e.g. the start alarm and a reconciliation pass both noticing the same window
        assertEquals(StartOutcome.ALREADY_ACTIVE, SilencerState.start(prefs, scheduleA) { AudioManager.RINGER_MODE_SILENT })

        // ...so the single end that follows still restores the ringer, to the mode
        // captured by the first start rather than the one the app itself had set.
        assertEquals(
            WindowEndResult.Restored(AudioManager.RINGER_MODE_NORMAL),
            SilencerState.end(prefs, scheduleA, RevertPolicy.RESTORE)
        )
    }

    @Test
    fun `ending the same window twice restores once and never touches another window`() {
        val prefs = mutablePreferencesOf()
        SilencerState.start(prefs, scheduleA) { AudioManager.RINGER_MODE_NORMAL }
        SilencerState.start(prefs, quick) { error("prior mode must only be captured from idle") }

        assertEquals(WindowEndResult.StillSilenced, SilencerState.end(prefs, quick, RevertPolicy.RESTORE))
        // A duplicate end (revert alarm + expiry check) must not release schedule A.
        assertEquals(WindowEndResult.NotActive, SilencerState.end(prefs, quick, RevertPolicy.RESTORE))
        assertEquals(setOf("a"), SilencerState.activeScheduleIds(prefs))

        assertEquals(
            WindowEndResult.Restored(AudioManager.RINGER_MODE_NORMAL),
            SilencerState.end(prefs, scheduleA, RevertPolicy.RESTORE)
        )
    }

    @Test
    fun `ending a window that never started changes nothing`() {
        val prefs = mutablePreferencesOf()

        assertEquals(WindowEndResult.NotActive, SilencerState.end(prefs, scheduleA, RevertPolicy.SOUND))
    }

    @Test
    fun `overlapping windows restore only when the last one ends, using its policy`() {
        val prefs = mutablePreferencesOf()
        SilencerState.start(prefs, scheduleA) { AudioManager.RINGER_MODE_VIBRATE }
        assertEquals(StartOutcome.STARTED, SilencerState.start(prefs, scheduleB) { error("not idle") })

        assertEquals(WindowEndResult.StillSilenced, SilencerState.end(prefs, scheduleA, RevertPolicy.RESTORE))
        assertEquals(
            WindowEndResult.Restored(AudioManager.RINGER_MODE_NORMAL),
            SilencerState.end(prefs, scheduleB, RevertPolicy.SOUND)
        )
    }

    @Test
    fun `a new window after everything ended captures a fresh prior mode`() {
        val prefs = mutablePreferencesOf()
        SilencerState.start(prefs, scheduleA) { AudioManager.RINGER_MODE_VIBRATE }
        SilencerState.end(prefs, scheduleA, RevertPolicy.RESTORE)

        assertEquals(StartOutcome.STARTED_FROM_IDLE, SilencerState.start(prefs, quick) { AudioManager.RINGER_MODE_NORMAL })
        assertEquals(
            WindowEndResult.Restored(AudioManager.RINGER_MODE_NORMAL),
            SilencerState.end(prefs, quick, RevertPolicy.RESTORE)
        )
    }
}

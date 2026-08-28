package com.ringfence.silentscheduler.core.ringer

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceStyleTest {

    @Test
    fun `full silent maps to the silent ringer mode`() {
        assertEquals(AudioManager.RINGER_MODE_SILENT, SilenceStyle.FULL_SILENT.toRingerMode())
    }

    @Test
    fun `vibrate only maps to the vibrate ringer mode`() {
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, SilenceStyle.VIBRATE_ONLY.toRingerMode())
    }
}

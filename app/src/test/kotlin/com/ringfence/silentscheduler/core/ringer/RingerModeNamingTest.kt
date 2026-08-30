package com.ringfence.silentscheduler.core.ringer

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class RingerModeNamingTest {

    @Test
    fun `silent mode names Silent`() {
        assertEquals("Silent", AudioManager.RINGER_MODE_SILENT.toFriendlyRingerModeName())
    }

    @Test
    fun `vibrate mode names Vibrate`() {
        assertEquals("Vibrate", AudioManager.RINGER_MODE_VIBRATE.toFriendlyRingerModeName())
    }

    @Test
    fun `normal mode names Normal`() {
        assertEquals("Normal", AudioManager.RINGER_MODE_NORMAL.toFriendlyRingerModeName())
    }
}

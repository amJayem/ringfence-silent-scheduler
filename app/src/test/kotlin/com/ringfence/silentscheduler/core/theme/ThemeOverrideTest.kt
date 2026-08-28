package com.ringfence.silentscheduler.core.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeOverrideTest {

    @Test
    fun `light override ignores the system setting`() {
        assertFalse(ThemeOverride.LIGHT.resolveIsDark(systemInDarkTheme = true))
        assertFalse(ThemeOverride.LIGHT.resolveIsDark(systemInDarkTheme = false))
    }

    @Test
    fun `dark override ignores the system setting`() {
        assertTrue(ThemeOverride.DARK.resolveIsDark(systemInDarkTheme = true))
        assertTrue(ThemeOverride.DARK.resolveIsDark(systemInDarkTheme = false))
    }

    @Test
    fun `system override follows the system setting`() {
        assertTrue(ThemeOverride.SYSTEM.resolveIsDark(systemInDarkTheme = true))
        assertFalse(ThemeOverride.SYSTEM.resolveIsDark(systemInDarkTheme = false))
    }
}

package com.ringfence.silentscheduler.core.theme

/** User-facing choice from Settings ("APPEARANCE") for which color scheme to use. */
enum class ThemeOverride {
    LIGHT,
    DARK,
    SYSTEM;

    fun resolveIsDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemInDarkTheme
    }
}

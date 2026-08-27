package com.ringfence.silentscheduler.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// onPrimary/onSecondaryContainer aren't derivable from the sampled surface colors —
// they're set explicitly here because Material3's baseline defaults assume a
// different primary tone than ours and pick the wrong contrast color otherwise (seen
// on-device: the Switch thumb turned a muddy dark color in dark mode because
// unset onPrimary fell back to Material's baseline dark-mode default instead of the
// white the design actually uses in both themes).
private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    secondaryContainer = AccentSoftLight,
    onSecondaryContainer = AccentLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    secondaryContainer = AccentSoftDark,
    onSecondaryContainer = AccentDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

/**
 * Theme follows the OS setting by default; Settings (step 8) adds a manual
 * Light/Dark/System override on top of [useDarkTheme].
 */
@Composable
fun RingfenceTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = RingfenceTypography,
        content = content
    )
}

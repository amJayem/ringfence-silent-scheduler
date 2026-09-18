package com.ringfence.silentscheduler.core.theme

import androidx.compose.ui.graphics.Color

// v2 "Lux" token set — see the "Silent Scheduler v2 (Lux)" design doc, section 1
// (Design tokens). v2 replaces the whole visual layer; these values are the canonical
// source now, taken directly from the doc's token tables rather than sampled.

val BackgroundLight = Color(0xFFF5F2ED)
val SurfaceLight = Color(0xFFFFFFFF)
val Surface2Light = Color(0xFFEFEAE2)
val OnSurfaceLight = Color(0xFF191826)
val OnSurfaceVariantLight = Color(0xFF191826).copy(alpha = 0.58f) // textDim
val OnSurfaceFaintLight = Color(0xFF191826).copy(alpha = 0.36f) // textFaint
val OutlineLight = Color(0xFF191826).copy(alpha = 0.09f) // divider
val AccentLight = Color(0xFF4B49A8)
val AccentSoftLight = Color(0xFF4B49A8).copy(alpha = 0.11f)

val BackgroundDark = Color(0xFF0B0B12)
val SurfaceDark = Color(0xFF15151F)
val Surface2Dark = Color(0xFF20202C)
val OnSurfaceDark = Color(0xFFF2F1F6)
val OnSurfaceVariantDark = Color(0xFFF2F1F6).copy(alpha = 0.60f) // textDim
val OnSurfaceFaintDark = Color(0xFFF2F1F6).copy(alpha = 0.36f) // textFaint
val OutlineDark = Color(0xFFFFFFFF).copy(alpha = 0.08f) // divider
val AccentDark = Color(0xFF9B99F0)
val AccentSoftDark = Color(0xFF9B99F0).copy(alpha = 0.16f)

// User feedback asked for a clearly red destructive action rather than the design's
// own muted dusty rose — a deliberate departure from the source doc, not sampled.
val DangerRedLight = Color(0xFFD32F2F)
val DangerRedDark = Color(0xFFFF6E68)

/** Hero-panel-only tokens (doc section 1, "Hero panel (both themes)") — not part of MaterialTheme's ColorScheme, so kept as a small standalone holder instead of overloading it with app-specific fields. */
data class HeroPalette(
    val gradient: List<Color>,
    val onHero: Color,
    val onHeroDim: Color,
    val onHeroFaint: Color,
    val ringTrack: Color,
    val heroInk: Color
)

val LightHeroPalette = HeroPalette(
    gradient = listOf(Color(0xFF5A57C8), Color(0xFF413F9E), Color(0xFF2E2C74)),
    onHero = Color.White,
    onHeroDim = Color.White.copy(alpha = 0.70f),
    onHeroFaint = Color.White.copy(alpha = 0.74f),
    ringTrack = Color.White.copy(alpha = 0.20f),
    heroInk = Color(0xFF2E2C74)
)

val DarkHeroPalette = HeroPalette(
    gradient = listOf(Color(0xFF4F4DB8), Color(0xFF343279), Color(0xFF20204F)),
    onHero = Color.White,
    onHeroDim = Color.White.copy(alpha = 0.70f),
    onHeroFaint = Color.White.copy(alpha = 0.74f),
    ringTrack = Color.White.copy(alpha = 0.17f),
    heroInk = Color(0xFF1E1D4A)
)

package com.ringfence.silentscheduler.core.theme

import androidx.compose.ui.graphics.Color

// Sampled with a pixel color-picker directly from "Silent Scheduler Android-selection.png"
// (the Claude Design canvas export), not eyeballed — see DESIGN_NOTES.md for the sampling
// method. Covers the 4 screens seen so far (Dashboard light/dark, Onboarding, Settings-dark);
// screens not yet provided (Add/Edit Schedule, light Settings) may introduce new tokens.

val AccentLight = Color(0xFF7C86C6)
val AccentSoftLight = Color(0xFFECEDF5)
val BackgroundLight = Color(0xFFF4F3F1)
val SurfaceLight = Color(0xFFFFFFFF)
// The design's --surface2: a muted neutral fill (distinct from Surface, not
// accent-tinted like AccentSoft) used for flat controls like the Quick Silence
// stepper's +/- buttons. Taken directly from design_handoff_silent_scheduler_android's
// SilentApp.dc.html :root/[data-theme] CSS variables, not sampled.
val Surface2Light = Color(0xFFEAE8E4)
val OnSurfaceLight = Color(0xFF17171A)
val OnSurfaceVariantLight = Color(0xFF8B8B8B)
val OutlineLight = Color(0xFFE7E7E8)

val AccentDark = Color(0xFF8D96D4)
val AccentSoftDark = Color(0xFF292A38)
val BackgroundDark = Color(0xFF101013)
val SurfaceDark = Color(0xFF1A1A1F)
val Surface2Dark = Color(0xFF25252C)
val OnSurfaceDark = Color(0xFFFCFCFC)
val OnSurfaceVariantDark = Color(0xFF8B8B8B)
val OutlineDark = Color(0xFF48494B)

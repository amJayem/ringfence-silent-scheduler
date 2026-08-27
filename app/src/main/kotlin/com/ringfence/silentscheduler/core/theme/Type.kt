package com.ringfence.silentscheduler.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The design locks mono numerals (IBM Plex Mono) for countdowns and time labels.
// No font file has been supplied yet, so this falls back to the system monospace
// family; swap in a real IBM Plex Mono font resource under res/font/ later.
val NumeralFontFamily = FontFamily.Monospace

val RingfenceTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NumeralFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp
    )
)

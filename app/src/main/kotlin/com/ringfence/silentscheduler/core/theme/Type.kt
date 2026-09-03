package com.ringfence.silentscheduler.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ringfence.silentscheduler.R

// Real bundled font files (res/font/manrope.xml, res/font/ibm_plex_mono.xml),
// matching the source design's own declared typefaces exactly (see
// SilentApp.dc.html's Google Fonts <link> tag): Manrope for general UI text,
// IBM Plex Mono for every time, countdown, numeric value, and uppercase section
// label. Each is a font-family XML resource covering multiple weights (Manrope is a
// single variable-font file addressed per weight via fontVariationSettings; IBM
// Plex Mono ships as separate static Regular/Medium/SemiBold files) — referencing
// the resource once here is enough for Compose to pick the right weight whenever a
// TextStyle using this family also sets a fontWeight.
val UiFontFamily = FontFamily(Font(R.font.manrope))
val NumeralFontFamily = FontFamily(Font(R.font.ibm_plex_mono))

private val defaultType = Typography()

val RingfenceTypography = Typography(
    displayLarge = defaultType.displayLarge.copy(
        fontFamily = NumeralFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp
    ),
    displayMedium = defaultType.displayMedium.copy(fontFamily = UiFontFamily),
    displaySmall = defaultType.displaySmall.copy(fontFamily = UiFontFamily),
    headlineLarge = defaultType.headlineLarge.copy(fontFamily = UiFontFamily),
    headlineMedium = defaultType.headlineMedium.copy(fontFamily = UiFontFamily),
    headlineSmall = defaultType.headlineSmall.copy(fontFamily = UiFontFamily),
    titleLarge = defaultType.titleLarge.copy(fontFamily = UiFontFamily),
    titleMedium = defaultType.titleMedium.copy(fontFamily = UiFontFamily),
    titleSmall = defaultType.titleSmall.copy(fontFamily = UiFontFamily),
    bodyLarge = defaultType.bodyLarge.copy(fontFamily = UiFontFamily),
    bodyMedium = defaultType.bodyMedium.copy(fontFamily = UiFontFamily),
    bodySmall = defaultType.bodySmall.copy(fontFamily = UiFontFamily),
    labelLarge = defaultType.labelLarge.copy(fontFamily = UiFontFamily),
    labelMedium = defaultType.labelMedium.copy(fontFamily = UiFontFamily),
    labelSmall = defaultType.labelSmall.copy(fontFamily = UiFontFamily)
)

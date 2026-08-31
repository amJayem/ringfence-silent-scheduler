package com.ringfence.silentscheduler.core.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every filled pill CTA in the design (design_handoff_silent_scheduler_android's
 * SilentApp.dc.html) is taller and heavier-weight than Material3's own [Button]
 * defaults (40dp height, labelLarge text) — measured on-device, the Dashboard's
 * main CTA was rendering at 40dp against the design's 54dp. The design actually
 * uses two sizes: 54dp/16sp for the single main action (Dashboard, onboarding), and
 * 52dp/15.5sp for CTAs that share a row with another button (editor Save, the Silent
 * Now sheet's custom-duration CTA) — both default to the 54dp size here, override
 * for the 52dp case.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    fontSize: TextUnit = 16.sp
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.height(height)
    ) {
        Text(text, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}

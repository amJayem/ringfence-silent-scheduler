package com.ringfence.silentscheduler.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TrackWidth = 44.dp
private val TrackHeight = 24.dp
private val ThumbSize = 18.dp
private val ThumbInset = 3.dp

/** S-05: no touch target below 44dp — the visual track is only 24dp tall, so the tappable area is padded out around it. */
private val MinTouchTarget = 44.dp

/**
 * Flat filled-thumb toggle matching the design source (no Material default checkmark
 * icon inside the thumb, no filled-track-with-icon look) — the stock M3 [Switch]
 * looked out of place, especially in light mode where its default outline styling
 * doesn't match the design's plain accent-filled track.
 */
@Composable
fun PillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "pillSwitchTrack"
    )
    val borderColor = if (checked) Color.Transparent else MaterialTheme.colorScheme.outline
    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillSwitchThumb"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - ThumbInset else ThumbInset,
        label = "pillSwitchOffset"
    )

    // Touch target (44dp square, S-05) is a separate outer box from the visual track
    // (44x24dp) so the tappable area is bigger than what's drawn, not the other way
    // around — the track's own dimensions stay exactly what the design specifies.
    Box(
        modifier = modifier
            .size(MinTouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(TrackWidth, TrackHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(trackColor)
                .border(1.dp, borderColor, RoundedCornerShape(percent = 50))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = thumbOffset)
                    .size(ThumbSize)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}

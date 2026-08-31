package com.ringfence.silentscheduler.quicksilence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.time.formatMinuteOfDay
import com.ringfence.silentscheduler.core.ui.PrimaryButton
import com.ringfence.silentscheduler.settings.domain.AVAILABLE_DURATION_MINUTES
import java.time.LocalTime

private const val STEP_MINUTES = 5
private const val MIN_MINUTES = 5
private const val MAX_MINUTES = 240

/**
 * Shared "Silent now" entry point — opened from both the Dashboard's idle status
 * card and the bottom-nav "Silent now" item (see DESIGN_NOTES.md / MainAppShell).
 * Previously these two entry points led to two different UIs (a duplicate full
 * screen for the nav item); the source design shows a single bottom sheet reachable
 * from either place, which is what this replaces both with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilentNowSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickSilenceViewModel = hiltViewModel()
) {
    val defaultMinutes by viewModel.defaultDurationMinutes.collectAsState()
    var selectedMinutes by rememberSaveable(defaultMinutes) { mutableIntStateOf(defaultMinutes) }
    val now = remember { LocalTime.now() }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.quick_silence_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.quick_silence_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AVAILABLE_DURATION_MINUTES.forEach { minutes ->
                    DurationChip(
                        minutes = minutes,
                        untilText = stringResource(
                            R.string.quick_silence_until_short,
                            formatMinuteOfDay(now.plusMinutes(minutes.toLong()).let { it.hour * 60 + it.minute })
                        ),
                        selected = selectedMinutes == minutes,
                        onClick = { selectedMinutes = minutes },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Matches design_handoff_silent_scheduler_android/SilentApp.dc.html's own
            // "Custom" row exactly: gap:12px between the label block and the stepper
            // group, gap:10px inside the stepper group — the previous version had
            // neither gap and leaned on one Text's own padding to fake spacing, which
            // is what fell apart once the value grew past 2 digits.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.quick_silence_custom_label), style = MaterialTheme.typography.titleMedium)
                    val untilMinuteOfDay = now.plusMinutes(selectedMinutes.toLong()).let { it.hour * 60 + it.minute }
                    Text(
                        stringResource(R.string.quick_silence_custom_until, formatMinuteOfDay(untilMinuteOfDay)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepperButton(
                        iconRes = R.drawable.ic_minus,
                        contentDescription = stringResource(R.string.quick_silence_decrease_cd),
                        onClick = { selectedMinutes = (selectedMinutes - STEP_MINUTES).coerceAtLeast(MIN_MINUTES) }
                    )
                    Text(
                        stringResource(R.string.quick_silence_minutes_format, selectedMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        // min-width:66px in the design — a floor, not a fixed width, so
                        // "240m" still grows instead of clipping at the top of the range.
                        modifier = Modifier.widthIn(min = 66.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    StepperButton(
                        iconRes = R.drawable.ic_plus,
                        contentDescription = stringResource(R.string.quick_silence_increase_cd),
                        onClick = { selectedMinutes = (selectedMinutes + STEP_MINUTES).coerceAtMost(MAX_MINUTES) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton(
                text = stringResource(R.string.quick_silence_start_custom_cta),
                onClick = {
                    viewModel.startSilence(selectedMinutes)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
                fontSize = 15.5.sp
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DurationChip(
    minutes: Int,
    untilText: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            durationLabel(minutes),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            untilText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // On a narrow screen four equal-width chips leave too little room for
            // "til H:MM AM" — letting it wrap was the actual bug: only the chips whose
            // text happened to be a character longer wrapped to a second line, leaving
            // the row jagged/uneven instead of a clean single-line grid on any screen.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StepperButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    // Design: 38x38, 12dp corner radius, flat surfaceVariant fill, no border — a bare
    // 32dp outlined circle previously, which also left less room to tap accurately.
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Icon(painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun durationLabel(minutes: Int): String = when (minutes) {
    15 -> stringResource(R.string.settings_duration_15m)
    30 -> stringResource(R.string.settings_duration_30m)
    60 -> stringResource(R.string.settings_duration_1h)
    else -> stringResource(R.string.settings_duration_2h)
}

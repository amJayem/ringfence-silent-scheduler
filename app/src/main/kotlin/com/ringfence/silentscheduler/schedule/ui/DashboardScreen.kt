package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.time.formatActiveCountdown
import com.ringfence.silentscheduler.core.ui.PillSwitch
import kotlinx.coroutines.delay

/** S-01: below this viewport height, the status ring and card compact so a schedule row still fits without scrolling. */
private const val COMPACT_HEIGHT_THRESHOLD_DP = 700

/**
 * Build-order step 7. Structure and copy match the Claude Design source (header,
 * status card, "RECURRING SCHEDULES" list, FAB); exact spacing/typography is a
 * best-effort match rather than pixel-measured.
 */
@Composable
fun DashboardScreen(
    onAddSchedule: () -> Unit,
    onEditSchedule: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSilentNow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    // S-01/S-02/S-03: derived from viewport height, not stored — a rotation or a
    // multi-window resize should reflect immediately, not stick to launch-time size.
    val compact = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_THRESHOLD_DP

    Box(modifier = modifier.fillMaxSize()) {
        // The header and status card used to sit in a fixed (non-scrolling) Column
        // above the schedule LazyColumn — if the card grew taller than the remaining
        // space (e.g. the active ring plus the R-13 already-silent panel), there was
        // no way to reach the rest of it. Folding everything into one LazyColumn makes
        // the whole page scroll together, so a tall status card is always reachable.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
        ) {
            item {
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.dashboard_brand_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.5.sp
                        )
                        Text(state.dayLabel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    val addScheduleCd = stringResource(R.string.action_add_schedule)
                    IconButton(onClick = onAddSchedule) {
                        Icon(Icons.Default.Add, contentDescription = addScheduleCd)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_tune), contentDescription = stringResource(R.string.action_settings))
                    }
                }

                Spacer(Modifier.height(if (compact) 12.dp else 16.dp))

                StatusCard(
                    state = state,
                    compact = compact,
                    onEndNow = { viewModel.endActiveNow() },
                    onSilentNow = onSilentNow,
                    onTurnSoundOn = { viewModel.turnSoundOnForActiveSchedule() }
                )

                Spacer(Modifier.height(if (compact) 16.dp else 24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.dashboard_section_recurring),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.dashboard_count_format, state.enabledCount, state.totalCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
            }

            items(state.rows, key = { it.schedule.id }) { row ->
                ScheduleRow(
                    row = row,
                    onToggle = { viewModel.toggleEnabled(row.schedule) },
                    onClick = { onEditSchedule(row.schedule.id) }
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }

        FloatingActionButton(
            onClick = onAddSchedule,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(60.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_schedule))
        }
    }
}

@Composable
private fun StatusCard(
    state: DashboardUiState,
    compact: Boolean,
    onEndNow: () -> Unit,
    onSilentNow: () -> Unit,
    onTurnSoundOn: () -> Unit
) {
    // S-01: card padding 24dp -> 16dp and inner gap 16dp -> 12dp when compact.
    val cardPadding = if (compact) 16.dp else 24.dp
    val gap = if (compact) 12.dp else 16.dp

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(cardPadding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val active = state.active
            when {
                active != null -> {
                    ActiveCountdownRing(
                        startEpochMillis = active.startEpochMillis,
                        endEpochMillis = active.endEpochMillis,
                        compact = compact
                    )
                    Spacer(Modifier.height(gap / 2))
                    Text(
                        active.untilText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(gap))
                    Button(
                        onClick = onEndNow,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dashboard_end_silence_now))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.dashboard_end_silence_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (active.alreadySilentWarning) {
                        Spacer(Modifier.height(gap * 0.75f))
                        AlreadySilentPanel(
                            // R-15: quick silence has no schedule to attach an override to.
                            onTurnSoundOn = onTurnSoundOn.takeIf { active.source is ActiveSource.FromSchedule }
                        )
                    }
                }
                state.idleUntilText != null -> {
                    Text(state.idleRemainingText.orEmpty(), style = MaterialTheme.typography.displayLarge)
                    Text(
                        state.idleUntilText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(gap))
                    Button(
                        onClick = onSilentNow,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dashboard_silent_now))
                    }
                }
                else -> {
                    Text(stringResource(R.string.dashboard_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.dashboard_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * H-04/H-04b/H-05: ticks every second (not just on the ViewModel's coarser 30s
 * refresh) and animates the ring arc 900ms linear between ticks, per the design's
 * motion spec. Kept local to the Composable rather than in the ViewModel's uiState
 * so a live countdown doesn't force the whole schedule list to recompose every second.
 */
@Composable
private fun ActiveCountdownRing(
    startEpochMillis: Long,
    endEpochMillis: Long,
    compact: Boolean
) {
    var remainingSeconds by remember(startEpochMillis, endEpochMillis) {
        mutableLongStateOf((endEpochMillis - System.currentTimeMillis()) / 1000)
    }
    LaunchedEffect(startEpochMillis, endEpochMillis) {
        while (true) {
            remainingSeconds = ((endEpochMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    val totalSeconds = ((endEpochMillis - startEpochMillis) / 1000).coerceAtLeast(1)
    val targetFraction = (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "silenceRingProgress"
    )
    // S-01: ~78% size when compact (180dp -> 140dp), same ratio the spec gives for its own 188->147px ring.
    ProgressRing(ringSize = if (compact) 140.dp else 180.dp, progressFraction = animatedFraction) {
        Text(
            stringResource(R.string.dashboard_silent_status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(formatActiveCountdown(remainingSeconds.coerceAtLeast(0)), style = MaterialTheme.typography.displayLarge)
    }
}

/**
 * R-13: shown when this window's own policy is RESTORE and the phone was already
 * silent/vibrate before it started — without this, correctly staying silent (instead
 * of turning sound on) looks like the app is broken. R-14: schedule-driven windows get
 * a one-tap override to flip that one schedule to always-sound; quick sessions (no
 * schedule to attach the override to, R-15) get the explanation only.
 */
@Composable
private fun AlreadySilentPanel(onTurnSoundOn: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.dashboard_already_silent_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            if (onTurnSoundOn != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onTurnSoundOn, shape = RoundedCornerShape(percent = 50)) {
                    Text(stringResource(R.string.dashboard_turn_sound_on), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Accent arc over a faint full-circle track, depleting as [progressFraction] (time remaining) drops to 0. */
@Composable
private fun ProgressRing(
    ringSize: Dp,
    progressFraction: Float,
    content: @Composable ColumnScope.() -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val accentColor = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progressFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun ScheduleRow(
    row: ScheduleRowUiState,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.schedule.label, style = MaterialTheme.typography.titleMedium)
                    if (row.isActiveNow) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                stringResource(R.string.dashboard_now_badge),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Text(
                    "${row.timeRangeText} · ${row.repeatText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    row.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PillSwitch(checked = row.schedule.isEnabled, onCheckedChange = { onToggle() })
        }
    }
}

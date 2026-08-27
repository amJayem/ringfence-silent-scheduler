package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ringfence.silentscheduler.R

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
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
        ) {
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

            Spacer(Modifier.height(16.dp))

            StatusCard(
                state = state,
                onEndNow = { viewModel.endActiveNow() },
                onSilentNow = { viewModel.silentNow() }
            )

            Spacer(Modifier.height(24.dp))

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

            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.rows, key = { it.schedule.id }) { row ->
                    ScheduleRow(
                        row = row,
                        onToggle = { viewModel.toggleEnabled(row.schedule) },
                        onClick = { onEditSchedule(row.schedule.id) }
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
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
    onEndNow: () -> Unit,
    onSilentNow: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val active = state.active
            when {
                active != null -> {
                    Text(
                        stringResource(R.string.dashboard_silent_status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(active.remainingText, style = MaterialTheme.typography.displayLarge)
                    Text(
                        active.untilText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
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
                }
                state.idleUntilText != null -> {
                    Text(state.idleRemainingText.orEmpty(), style = MaterialTheme.typography.displayLarge)
                    Text(
                        state.idleUntilText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
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
            Switch(checked = row.schedule.isEnabled, onCheckedChange = { onToggle() })
        }
    }
}

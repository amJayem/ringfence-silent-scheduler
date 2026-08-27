package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Build-order step 4 verification harness only — not the real Dashboard (step 7) or
 * Add/Edit Schedule screen (step 5), and not styled to the Claude Design source.
 * Exists purely to prove DataStore CRUD persists correctly after a force-close.
 */
@Composable
fun ScheduleDebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleDebugViewModel = hiltViewModel()
) {
    val schedules by viewModel.schedules.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("< Back to Quick Silence")
        }

        Text(
            text = "Step 4 debug: Schedule DataStore CRUD",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Button(onClick = { viewModel.addTestSchedule() }) {
            Text("Add test schedule")
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(schedules, key = { it.id }) { schedule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(schedule.label, style = MaterialTheme.typography.bodyLarge)
                        val days = schedule.repeatDays.sortedBy { it.value }.joinToString { it.name.take(3) }
                        Text(
                            "${schedule.startMinuteOfDay}–${schedule.endMinuteOfDay} min · $days",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = schedule.isEnabled,
                        onCheckedChange = { viewModel.toggleEnabled(schedule) }
                    )
                    TextButton(onClick = { viewModel.delete(schedule) }) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

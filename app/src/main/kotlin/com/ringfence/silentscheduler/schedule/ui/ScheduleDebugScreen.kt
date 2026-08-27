package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ringfence.silentscheduler.schedule.domain.Schedule

/**
 * Build-order step 4/5 verification harness only — not the real Dashboard (step 7),
 * and not styled to the Claude Design source. Exists to prove DataStore CRUD works
 * end to end, including through the real Add/Edit Schedule screen.
 */
@Composable
fun ScheduleDebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleDebugViewModel = hiltViewModel()
) {
    val schedules by viewModel.schedules.collectAsState()

    // null = not editing, Schedule(...) = editing that row, a placeholder empty
    // Schedule id "" = adding new. Simplified stand-in for the real Dashboard -> Add/
    // Edit navigation that step 7 will introduce.
    var editingTarget by remember { mutableStateOf<Schedule?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    if (isAddingNew || editingTarget != null) {
        ScheduleEditScreen(
            initial = editingTarget,
            onSave = {
                viewModel.save(it)
                isAddingNew = false
                editingTarget = null
            },
            onCancel = {
                isAddingNew = false
                editingTarget = null
            },
            modifier = modifier
        )
        return
    }

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
            text = "Step 4/5 debug: Schedule CRUD",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Button(onClick = { isAddingNew = true }) {
            Text("+ Add schedule")
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(schedules, key = { it.id }) { schedule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingTarget = schedule }
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

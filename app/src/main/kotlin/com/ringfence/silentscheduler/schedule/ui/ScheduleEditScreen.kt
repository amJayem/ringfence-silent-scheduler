package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.time.formatMinuteOfDay
import com.ringfence.silentscheduler.schedule.domain.Schedule
import java.time.DayOfWeek
import java.util.UUID

/**
 * Build-order step 5. Not yet styled to the Claude Design source — that screen
 * wasn't included in the images provided. Functionally complete (label, start/end
 * time pickers, day-of-week repeat, enabled toggle); revisit visuals once the real
 * design is available (see DESIGN_NOTES.md).
 */
@Composable
fun ScheduleEditScreen(
    initial: Schedule?,
    onSave: (Schedule) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Keyed on `initial` (not a bare `remember`): the caller navigates here before its
    // schedule list has necessarily loaded from DataStore, so `initial` can arrive
    // null on the first composition and flip to the real Schedule a moment later.
    // An unkeyed remember would freeze on that first (blank) value and never pick up
    // the real one.
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var startMinuteOfDay by remember(initial) { mutableIntStateOf(initial?.startMinuteOfDay ?: 9 * 60) }
    var endMinuteOfDay by remember(initial) { mutableIntStateOf(initial?.endMinuteOfDay ?: 10 * 60) }
    var repeatDays by remember(initial) { mutableStateOf(initial?.repeatDays ?: emptySet()) }
    var isEnabled by remember(initial) { mutableStateOf(initial?.isEnabled ?: true) }

    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            if (initial != null && onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Text(
            text = if (initial == null) "Add schedule" else "Edit schedule",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { editingStart = true }) {
                Text("Start: ${formatMinuteOfDay(startMinuteOfDay)}")
            }
            TextButton(onClick = { editingEnd = true }) {
                Text("End: ${formatMinuteOfDay(endMinuteOfDay)}")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        LazyRow {
            items(DayOfWeek.entries.toList()) { day ->
                FilterChip(
                    modifier = Modifier.padding(end = 4.dp),
                    selected = day in repeatDays,
                    onClick = {
                        repeatDays = if (day in repeatDays) repeatDays - day else repeatDays + day
                    },
                    label = { Text(day.name.take(1)) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enabled", modifier = Modifier.weight(1f))
            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
        }

        Spacer(Modifier.height(24.dp))

        if (repeatDays.isEmpty()) {
            Text(
                "Select at least one day",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = {
                onSave(
                    Schedule(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        label = label.ifBlank { "Untitled" },
                        startMinuteOfDay = startMinuteOfDay,
                        endMinuteOfDay = endMinuteOfDay,
                        repeatDays = repeatDays,
                        isEnabled = isEnabled
                    )
                )
            },
            enabled = repeatDays.isNotEmpty(),
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }

    if (editingStart) {
        TimeOfDayPickerDialog(
            initialMinuteOfDay = startMinuteOfDay,
            onDismiss = { editingStart = false },
            onConfirm = { startMinuteOfDay = it; editingStart = false }
        )
    }
    if (editingEnd) {
        TimeOfDayPickerDialog(
            initialMinuteOfDay = endMinuteOfDay,
            onDismiss = { editingEnd = false },
            onConfirm = { endMinuteOfDay = it; editingEnd = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeOfDayPickerDialog(
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = false
    )
    // Defaults to the dial; a toggle switches to TimeInput (numeric keyboard entry)
    // without losing the selection — both read/write the same TimePickerState.
    var useKeyboardInput by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (useKeyboardInput) {
                    TimeInput(state = state)
                } else {
                    TimePicker(state = state)
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { useKeyboardInput = !useKeyboardInput }) {
                        Icon(
                            painter = painterResource(
                                if (useKeyboardInput) R.drawable.ic_schedule else R.drawable.ic_keyboard
                            ),
                            contentDescription = if (useKeyboardInput) "Switch to dial" else "Switch to keyboard entry"
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.time.formatDurationMinutes
import com.ringfence.silentscheduler.core.time.formatMinuteOfDay
import com.ringfence.silentscheduler.core.time.minutesBetween
import com.ringfence.silentscheduler.core.ui.RadioOptionRow
import com.ringfence.silentscheduler.core.ui.SegmentedControl
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.WEEKDAYS
import com.ringfence.silentscheduler.schedule.domain.WEEKENDS
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/** Sunday-first order, matching the design's "S M T W T F S" day-chip row. */
private val WEEK_ORDER = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
)

/**
 * Build-order step 5, restyled against the "Edit window" screen in the Claude
 * Design source (see DESIGN_NOTES.md): Label field, Start/End time boxes with a
 * computed duration caption, a Sunday-first day-of-week picker plus quick-select
 * presets, a per-schedule Silence Style override, and a destructive Delete action.
 * The "Enabled" toggle from the original build-order version is intentionally gone —
 * the source design has no such control here; enabling/disabling a schedule is the
 * Dashboard row switch's job.
 */
@Composable
fun ScheduleEditScreen(
    initial: Schedule?,
    onSave: (Schedule) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    formViewModel: ScheduleFormViewModel = hiltViewModel()
) {
    // Keyed on `initial` (not a bare `remember`): the caller navigates here before its
    // schedule list has necessarily loaded from DataStore, so `initial` can arrive
    // null on the first composition and flip to the real Schedule a moment later.
    // An unkeyed remember would freeze on that first (blank) value and never pick up
    // the real one.
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }

    // For a brand-new schedule, default to the next round half-hour from now (e.g.
    // 1:17 -> 1:30) rather than a fixed 9-10 AM, so the picker opens somewhere near
    // what the user probably wants.
    val defaultStartMinuteOfDay = remember(initial) {
        val now = LocalTime.now()
        val roundedUp = ((now.hour * 60 + now.minute + 29) / 30) * 30
        roundedUp % (24 * 60)
    }
    var startMinuteOfDay by remember(initial) {
        mutableIntStateOf(initial?.startMinuteOfDay ?: defaultStartMinuteOfDay)
    }
    var endMinuteOfDay by remember(initial) {
        mutableIntStateOf(initial?.endMinuteOfDay ?: (defaultStartMinuteOfDay + 60) % (24 * 60))
    }
    var repeatDays by remember(initial) { mutableStateOf(initial?.repeatDays ?: emptySet()) }
    val isEnabled = remember(initial) { initial?.isEnabled ?: true }

    val defaultSilenceStyle by formViewModel.defaultSilenceStyle.collectAsState()
    var silenceStyle by remember(initial, defaultSilenceStyle) {
        mutableStateOf(initial?.silenceStyle ?: defaultSilenceStyle)
    }

    val defaultRevertPolicy by formViewModel.defaultRevertPolicy.collectAsState()
    var revertPolicy by remember(initial, defaultRevertPolicy) {
        mutableStateOf(initial?.revertPolicy ?: defaultRevertPolicy)
    }

    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    // E-11: the only case the source design treats as invalid — a window needs at
    // least two distinct clock times. minutesBetween() would otherwise interpret
    // start == end as a full 24h overnight window, which is silently wrong, not
    // a valid schedule.
    val isInvalidRange = startMinuteOfDay == endMinuteOfDay
    val canSave = !isInvalidRange && repeatDays.isNotEmpty()

    fun save() {
        onSave(
            Schedule(
                id = initial?.id ?: UUID.randomUUID().toString(),
                label = label.ifBlank { "Untitled" },
                startMinuteOfDay = startMinuteOfDay,
                endMinuteOfDay = endMinuteOfDay,
                repeatDays = repeatDays,
                isEnabled = isEnabled,
                silenceStyle = silenceStyle,
                revertPolicy = revertPolicy
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
            }
            Text(
                text = if (initial == null) {
                    stringResource(R.string.schedule_edit_add_title)
                } else {
                    stringResource(R.string.schedule_edit_edit_title)
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
        SectionLabel(stringResource(R.string.schedule_edit_label_section))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = { Text(stringResource(R.string.schedule_edit_label_placeholder)) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.schedule_edit_window_section))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimeBox(
                label = stringResource(R.string.schedule_edit_start),
                time = formatMinuteOfDay(startMinuteOfDay),
                onClick = { editingStart = true },
                modifier = Modifier.weight(1f)
            )
            TimeBox(
                label = stringResource(R.string.schedule_edit_end),
                time = formatMinuteOfDay(endMinuteOfDay),
                onClick = { editingEnd = true },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        if (isInvalidRange) {
            InvalidRangeCard()
        } else {
            Text(
                stringResource(
                    R.string.schedule_edit_duration_caption,
                    formatDurationMinutes(minutesBetween(startMinuteOfDay, endMinuteOfDay).toLong())
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.schedule_edit_repeat_section))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WEEK_ORDER.forEach { day ->
                DayChip(
                    label = day.name.take(1),
                    selected = day in repeatDays,
                    onClick = {
                        repeatDays = if (day in repeatDays) repeatDays - day else repeatDays + day
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickSelectChip(
                label = stringResource(R.string.schedule_edit_every_day),
                selected = repeatDays.size == 7,
                onClick = { repeatDays = DayOfWeek.entries.toSet() }
            )
            QuickSelectChip(
                label = stringResource(R.string.schedule_edit_weekdays),
                selected = repeatDays == WEEKDAYS,
                onClick = { repeatDays = WEEKDAYS }
            )
            QuickSelectChip(
                label = stringResource(R.string.schedule_edit_weekends),
                selected = repeatDays == WEEKENDS,
                onClick = { repeatDays = WEEKENDS }
            )
        }
        if (repeatDays.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.schedule_edit_days_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.schedule_edit_silence_style_section))
        Spacer(Modifier.height(8.dp))
        SegmentedControl(
            options = listOf(SilenceStyle.FULL_SILENT, SilenceStyle.VIBRATE_ONLY),
            selected = silenceStyle,
            labelFor = { style ->
                if (style == SilenceStyle.FULL_SILENT) {
                    stringResource(R.string.settings_silence_style_full)
                } else {
                    stringResource(R.string.settings_silence_style_vibrate)
                }
            },
            onSelect = { silenceStyle = it }
        )

        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.schedule_edit_revert_section))
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                RadioOptionRow(
                    title = stringResource(R.string.schedule_edit_revert_restore_title),
                    subtitle = stringResource(R.string.schedule_edit_revert_restore_subtitle),
                    selected = revertPolicy == RevertPolicy.RESTORE,
                    onClick = { revertPolicy = RevertPolicy.RESTORE }
                )
                RadioOptionRow(
                    title = stringResource(R.string.schedule_edit_revert_sound_title),
                    subtitle = stringResource(R.string.schedule_edit_revert_sound_subtitle),
                    selected = revertPolicy == RevertPolicy.SOUND,
                    onClick = { revertPolicy = RevertPolicy.SOUND }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (revertPolicy == RevertPolicy.SOUND) {
                stringResource(R.string.schedule_edit_revert_hint_sound)
            } else {
                stringResource(R.string.schedule_edit_revert_hint_restore)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (initial != null && onDelete != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(percent = 50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.schedule_edit_delete))
            }
        }

        Spacer(Modifier.height(24.dp))
        }

        EditorActionBar(
            onCancel = onCancel,
            onSave = ::save,
            saveEnabled = canSave,
            saveLabel = if (initial == null) {
                stringResource(R.string.schedule_edit_save_new)
            } else {
                stringResource(R.string.schedule_edit_save_edit)
            }
        )
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

/**
 * E-01b: fixed bottom bar (not scrolling with the form) so Save is always one
 * thumb-reach away — Cancel is outlined and narrower, Save is filled and wider
 * (visibly the primary action), matching the source design's flex:1 / flex:1.35 split.
 */
@Composable
private fun EditorActionBar(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saveLabel: String
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(stringResource(R.string.schedule_edit_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = saveEnabled,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(52.dp)
                ) {
                    Text(saveLabel)
                }
            }
        }
    }
}

/** E-11: shown instead of the duration caption when start == end; Save is disabled by the caller. */
@Composable
private fun InvalidRangeCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.schedule_edit_invalid_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                stringResource(R.string.schedule_edit_invalid_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TimeBox(
    label: String,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(time, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun QuickSelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
    // Defaults to TimeInput (numeric keyboard entry); a toggle switches to the dial
    // without losing the selection — both read/write the same TimePickerState.
    var useKeyboardInput by remember { mutableStateOf(true) }

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

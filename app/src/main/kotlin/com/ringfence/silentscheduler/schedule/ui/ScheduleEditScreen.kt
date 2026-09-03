package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.theme.NumeralFontFamily
import com.ringfence.silentscheduler.core.time.formatDurationMinutes
import com.ringfence.silentscheduler.core.time.formatMinuteOfDay
import com.ringfence.silentscheduler.core.time.minutesBetween
import com.ringfence.silentscheduler.core.ui.PrimaryButton
import com.ringfence.silentscheduler.core.ui.RadioOptionRow
import com.ringfence.silentscheduler.core.ui.SegmentedControl
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.domain.WEEKDAYS
import com.ringfence.silentscheduler.schedule.domain.WEEKENDS
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** E-04/E-05: which of the Start/End cards is the current accent-bordered target. */
private enum class TimeTarget { START, END }

// Matches the source design's own cardRadius/chipRadius constants for Android
// (SilentApp.dc.html: `cardRadius: ios ? 20 : 24, chipRadius: ios ? 13 : 16`).
private val CardRadius = 24.dp
private val ChipRadius = 16.dp
private val TagRadius = 6.dp

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
    // A brand-new schedule starts with today pre-selected rather than nothing — an
    // empty set only ever forced the user to clear the "Select at least one day"
    // error before Save was even reachable, for no benefit over a sensible default.
    var repeatDays by remember(initial) {
        mutableStateOf(initial?.repeatDays ?: setOf(LocalDate.now().dayOfWeek))
    }
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
    // E-04/E-05: which of Start/End is the "active target" — persists across the
    // dialog opening and closing, unlike editingStart/editingEnd, so the accent
    // border stays on whichever card the user last touched rather than reverting
    // to neutral the moment its dialog closes.
    var activeTarget by remember(initial) { mutableStateOf(TimeTarget.START) }

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
            shape = RoundedCornerShape(CardRadius),
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
                isActive = activeTarget == TimeTarget.START,
                onTap = { activeTarget = TimeTarget.START },
                onDoubleTap = { activeTarget = TimeTarget.START; editingStart = true },
                modifier = Modifier.weight(1f)
            )
            TimeBox(
                label = stringResource(R.string.schedule_edit_end),
                time = formatMinuteOfDay(endMinuteOfDay),
                isActive = activeTarget == TimeTarget.END,
                onTap = { activeTarget = TimeTarget.END },
                onDoubleTap = { activeTarget = TimeTarget.END; editingEnd = true },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
        // E-08/E-09/E-10: always rendered, but with no segment drawn while invalid
        // (E-11) — an empty track rather than hiding the bar entirely.
        TimeRangeBar(
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            isInvalid = isInvalidRange,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        if (isInvalidRange) {
            InvalidRangeCard()
        } else {
            val crossesMidnight = endMinuteOfDay < startMinuteOfDay
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (crossesMidnight) {
                    CrossesMidnightTag()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        R.string.schedule_edit_duration_caption,
                        formatDurationMinutes(minutesBetween(startMinuteOfDay, endMinuteOfDay).toLong())
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TimeSlotList(
            activeTarget = activeTarget,
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            onPick = { minuteOfDay ->
                if (activeTarget == TimeTarget.START) {
                    startMinuteOfDay = minuteOfDay
                } else {
                    endMinuteOfDay = minuteOfDay
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

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
                    Text(stringResource(R.string.schedule_edit_cancel), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                }
                PrimaryButton(
                    text = saveLabel,
                    onClick = onSave,
                    enabled = saveEnabled,
                    modifier = Modifier.weight(1.35f),
                    height = 52.dp,
                    fontSize = 15.5.sp
                )
            }
        }
    }
}

/** E-11: shown instead of the duration caption when start == end; Save is disabled by the caller. */
@Composable
private fun InvalidRangeCard() {
    Surface(
        shape = RoundedCornerShape(CardRadius),
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

/**
 * E-08/E-09/E-10: a midnight-to-midnight track showing the chosen window as an
 * accent segment, inset inside its own card per the source design — a 26dp track
 * (not a bare thin line) with a single center divider at noon, the segment inset
 * 4dp inside the track's own height rather than filling it edge to edge. An
 * overnight window (end before start) draws as two segments — one to the right
 * edge, one from the left edge — rather than one segment wrapping backwards, since
 * the track itself doesn't wrap. While invalid (E-11, start == end) no segment is
 * drawn at all; the track stays empty.
 */
@Composable
private fun TimeRangeBar(
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    isInvalid: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                drawRoundRect(
                    color = trackColor,
                    size = size,
                    cornerRadius = CornerRadius(7.dp.toPx())
                )

                // A single divider at the midpoint (noon) — not a tick per hour marker.
                drawLine(
                    color = dividerColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 1.dp.toPx()
                )

                if (!isInvalid) {
                    val inset = 4.dp.toPx()
                    val segmentTop = inset
                    val segmentHeight = size.height - inset * 2
                    fun drawSegment(fromFraction: Float, toFraction: Float) {
                        val fromX = fromFraction * size.width
                        val toX = toFraction * size.width
                        val segmentWidth = toX - fromX
                        if (segmentWidth > 0) {
                            // Capped at half the segment's own width too, not just its
                            // height — a short-duration window (e.g. 1h out of 24)
                            // otherwise rounds into a near-circular blob instead of a
                            // visibly pill-shaped bar.
                            val radius = minOf(segmentHeight / 2f, segmentWidth / 2f)
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset(fromX, segmentTop),
                                size = Size(segmentWidth, segmentHeight),
                                cornerRadius = CornerRadius(radius)
                            )
                        }
                    }
                    val startFraction = startMinuteOfDay / 1440f
                    val endFraction = endMinuteOfDay / 1440f
                    if (endFraction > startFraction) {
                        drawSegment(startFraction, endFraction)
                    } else {
                        // Crosses midnight — the track can't wrap, so it's two segments.
                        drawSegment(startFraction, 1f)
                        drawSegment(0f, endFraction)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 360, 720, 1080, 0).forEach { minuteOfDay ->
                    Text(
                        formatMinuteOfDay(minuteOfDay),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = NumeralFontFamily,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private const val MINUTES_PER_SLOT = 15
private const val SLOTS_PER_DAY = 24 * 60 / MINUTES_PER_SLOT

/**
 * E-06/E-07: an always-visible scrolling list of every 15-minute slot across all 24
 * hours — free choice, not a preset shortlist — rather than a modal. Tapping a row
 * writes to whichever of Start/End is the current active target; only the row
 * matching *that* target's own current value is highlighted and hinted, matching
 * the source design (a row matching the *other* field's value isn't marked here).
 */
@Composable
private fun TimeSlotList(
    activeTarget: TimeTarget,
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeMinuteOfDay = if (activeTarget == TimeTarget.START) startMinuteOfDay else endMinuteOfDay
    val startHint = stringResource(R.string.schedule_edit_start).lowercase()
    val endHint = stringResource(R.string.schedule_edit_end).lowercase()

    // Keeps the active target's current value in view whenever it changes — either
    // from switching which card is active, or picking a new value for it.
    LaunchedEffect(activeTarget, activeMinuteOfDay) {
        val targetIndex = (activeMinuteOfDay / MINUTES_PER_SLOT - 2).coerceIn(0, SLOTS_PER_DAY - 1)
        listState.scrollToItem(targetIndex)
    }

    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(172.dp)
    ) {
        LazyColumn(state = listState, modifier = Modifier.padding(6.dp)) {
            items(SLOTS_PER_DAY) { index ->
                val minuteOfDay = index * MINUTES_PER_SLOT
                val isActiveTargetValue = minuteOfDay == activeMinuteOfDay
                TimeSlotRow(
                    label = formatMinuteOfDay(minuteOfDay),
                    hint = if (isActiveTargetValue) {
                        if (activeTarget == TimeTarget.START) startHint else endHint
                    } else {
                        null
                    },
                    selected = isActiveTargetValue,
                    onClick = { onPick(minuteOfDay) }
                )
            }
        }
    }
}

@Composable
private fun TimeSlotRow(
    label: String,
    hint: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = NumeralFontFamily,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** E-10: shown next to the duration caption only for a window that wraps past midnight. */
@Composable
private fun CrossesMidnightTag() {
    // Design uses a small 6dp-rounded rect here (border-radius:6px), not a pill —
    // distinct from the fully-rounded chips/buttons elsewhere on this screen.
    Surface(
        shape = RoundedCornerShape(TagRadius),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            stringResource(R.string.schedule_edit_crosses_midnight),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Uppercase section headers ("LABEL", "WINDOW"...) are mono in the source design,
 * not the body sans — matching every other section label and time value. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = NumeralFontFamily,
        letterSpacing = 1.3.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** E-03/E-04/E-05: the card for whichever of Start/End was tapped most recently gets
 * a 1.5dp accent border instead of the neutral outline, so it's clear which one the
 * time dialog that just opened (or last closed) is writing to. A single tap only
 * makes this the active target for the always-visible list below; a double tap
 * additionally opens the numeric dialog, so one accidental tap doesn't pop a dialog
 * over what's meant to be a quick scroll-and-pick interaction. */
@Composable
private fun TimeBox(
    label: String,
    time: String,
    isActive: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = Color.Transparent,
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() })
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(time, style = MaterialTheme.typography.titleLarge, fontFamily = NumeralFontFamily)
        }
    }
}

/** Design draws day chips as a 46dp-tall rounded square (chipRadius, 16dp on
 * Android) — not a circle, despite the single-letter label suggesting one. */
@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(ChipRadius)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
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
    // Numeric entry only — the source design has no dial/clock-face picker at all,
    // so the earlier dial-view toggle here didn't correspond to anything designed.
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = false
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimeInput(state = state)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_edit_cancel)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                        Text(stringResource(R.string.schedule_edit_time_dialog_ok))
                    }
                }
            }
        }
    }
}

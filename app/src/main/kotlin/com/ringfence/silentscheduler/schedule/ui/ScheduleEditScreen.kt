package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 *
 * Tapping a Start/End time value edits it in place — hour and minute become typeable
 * right there in the card, à la a Samsung alarm's own time entry — rather than a
 * double-tap gesture or a separate modal dialog, neither of which anything on screen
 * hinted at for this app's elderly-skewing audience.
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

    // Inline-editing (tap the time itself to type it, à la a Samsung alarm's time
    // wheel) rather than a modal dialog over the whole screen — see TimeBox below.
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }
    // E-04/E-05: which of Start/End is the "active target" — persists across the
    // dialog opening and closing, unlike editingStart/editingEnd, so the accent
    // border stays on whichever card the user last touched rather than reverting
    // to neutral the moment its dialog closes.
    var activeTarget by remember(initial) { mutableStateOf(TimeTarget.START) }
    // The quick-pick list stays collapsed until the user has actually touched a
    // time box — activeTarget itself always has a value (defaulting to START), so
    // it can't be used on its own to tell "user picked a target" apart from "no
    // interaction yet."
    var hasSelectedTimeBox by remember(initial) { mutableStateOf(false) }

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

    // Tapping back while a field (a time box, the label) still holds focus otherwise
    // left the keyboard's own dismiss animation tied to the screen's exit transition
    // instead of starting right away, which read as a laggy, delayed close. Clearing
    // focus and hiding the keyboard explicitly, before onCancel ever runs, lets that
    // animation start immediately regardless of what the navigation transition does.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun cancelAndDismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
        onCancel()
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
            IconButton(onClick = ::cancelAndDismissKeyboard) {
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
                minuteOfDay = startMinuteOfDay,
                isActive = activeTarget == TimeTarget.START,
                isEditing = editingStart,
                onSelect = { activeTarget = TimeTarget.START; hasSelectedTimeBox = true },
                onStartEdit = {
                    activeTarget = TimeTarget.START
                    hasSelectedTimeBox = true
                    // Only one box edits at a time — force-closing End's editor here
                    // (rather than leaving it mounted) is safe because its value is
                    // kept live-synced below, not just committed once at the end.
                    editingEnd = false
                    editingStart = true
                },
                onValueChange = { startMinuteOfDay = it },
                onDone = { editingStart = false },
                modifier = Modifier.weight(1f)
            )
            TimeBox(
                label = stringResource(R.string.schedule_edit_end),
                minuteOfDay = endMinuteOfDay,
                isActive = activeTarget == TimeTarget.END,
                isEditing = editingEnd,
                onSelect = { activeTarget = TimeTarget.END; hasSelectedTimeBox = true },
                onStartEdit = {
                    activeTarget = TimeTarget.END
                    hasSelectedTimeBox = true
                    editingStart = false
                    editingEnd = true
                },
                onValueChange = { endMinuteOfDay = it },
                onDone = { editingEnd = false },
                modifier = Modifier.weight(1f)
            )
        }
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
        // Collapsed until the user actually touches Start or End: at rest, the
        // screen is just the two boxes and the duration line, not a tall list
        // nobody asked for yet. Selecting a box expands it into view right where
        // the quick-pick flow needs it.
        AnimatedVisibility(
            visible = hasSelectedTimeBox,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                TimeSlotList(
                    activeTarget = activeTarget,
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    onPick = { minuteOfDay ->
                        // Also closes that box's own inline editor (if it happened to
                        // be open): picking a row is itself a complete, decisive
                        // choice, and leaving the editor mounted afterward showed its
                        // own stale hour/minute digits — a keypad editor's typed
                        // fields only ever initialize once, from whatever the value
                        // was when it was opened, so an external update like this one
                        // never reached them while it stayed open.
                        if (activeTarget == TimeTarget.START) {
                            startMinuteOfDay = minuteOfDay
                            editingStart = false
                        } else {
                            endMinuteOfDay = minuteOfDay
                            editingEnd = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

        // Hidden while a Start/End box is being typed into inline: the on-screen
        // keyboard already covers this bar's usual position, and there's nothing
        // useful this pair of buttons could do mid-entry that tapping away from the
        // field (which commits it) or the keyboard's own Done action doesn't already.
        if (!editingStart && !editingEnd) {
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

/**
 * E-03/E-04/E-05: the card for whichever of Start/End was tapped most recently gets
 * a 1.5dp accent border instead of the neutral outline, so it's clear which one the
 * always-visible 15-minute list below is currently scrolled to.
 *
 * Tapping the label row only selects this box as the active target (scrolling the
 * quick-pick list to its value) — tapping the time value itself, inspired by a
 * Samsung alarm's own time picker, switches that value in place into precise
 * hour/minute entry, no modal dialog and no separate keyboard icon required. The
 * two tap targets don't conflict: Compose's nested `clickable` on the time text
 * consumes the tap there before it would otherwise reach the card's own `onSelect`.
 */
@Composable
private fun TimeBox(
    label: String,
    minuteOfDay: Int,
    isActive: Boolean,
    isEditing: Boolean,
    onSelect: () -> Unit,
    onStartEdit: () -> Unit,
    onValueChange: (Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = Color.Transparent,
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp, end = 16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isEditing) {
                InlineTimeEditor(
                    initialMinuteOfDay = minuteOfDay,
                    onValueChange = onValueChange,
                    onDone = onDone,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
            } else {
                Text(
                    formatMinuteOfDay(minuteOfDay),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = NumeralFontFamily,
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 6.dp)
                        .clickable(onClick = onStartEdit)
                )
            }
        }
    }
}

/**
 * Hour/minute/AM-PM entry inline in place of the plain time text — no modal, no
 * separate confirm button. Auto-advances (and, for minute, auto-finishes) the moment
 * a digit can't possibly be extended into a different valid value: a first hour
 * digit of 2-9 can't become a two-digit hour (20-99 don't exist), so it advances
 * immediately; a first minute digit of 6-9 can't become a two-digit minute (60-99
 * don't exist) either, so a lone "7" finishes as "07" rather than waiting for a
 * digit that will never come. Only 1 (hour) and 0-5 (minute) are genuinely
 * ambiguous first digits, so those wait for a possible second one.
 *
 * [onValueChange] fires live after every valid keystroke, not just once at the end —
 * deliberately, so that a schedule's Start and End are never both mid-edit with only
 * one of them holding the real, in-progress value: tapping straight from Start to
 * End (before Start's own edit ever reached a natural finishing point) closes Start's
 * editor immediately from the screen level, and without live-syncing that would have
 * silently discarded whatever had already been typed into it.
 */
@Composable
private fun InlineTimeEditor(
    initialMinuteOfDay: Int,
    onValueChange: (Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialHour24 = initialMinuteOfDay / 60
    var hour12 by remember { mutableIntStateOf(((initialHour24 + 11) % 12) + 1) }
    var isPm by remember { mutableStateOf(initialHour24 >= 12) }
    var hourField by remember { mutableStateOf(TextFieldValue(hour12.toString())) }
    var minuteField by remember {
        mutableStateOf(TextFieldValue((initialMinuteOfDay % 60).toString().padStart(2, '0')))
    }
    var hourFocused by remember { mutableStateOf(false) }
    var minuteFocused by remember { mutableStateOf(false) }
    var hasFocusedOnce by remember { mutableStateOf(false) }
    // Exists for the same reason noted below on the select-all effects: moving focus
    // *synchronously* inside onValueChange raced with the IME on-device — the
    // keystroke that triggered the auto-advance sometimes never actually committed
    // into the field, and focus ended up somewhere else entirely (observed: it fell
    // through to the Label field above, with the keyboard switching to that field's
    // alphabetic layout mid-digit-entry). Deferring the actual focus change into a
    // LaunchedEffect one recomposition later — after the triggering keystroke has
    // fully landed — avoids that race.
    var advanceToMinutePending by remember { mutableStateOf(false) }
    var finishPending by remember { mutableStateOf(false) }
    val hourFocusRequester = remember { FocusRequester() }
    val minuteFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun pushValue() {
        val minute = minuteField.text.toIntOrNull() ?: 0
        val hour24 = (hour12 % 12) + if (isPm) 12 else 0
        onValueChange(hour24 * 60 + minute)
    }

    // Same reasoning as the screen-level docs used to note for the old modal: the
    // select-all has to happen a recomposition after focus actually lands, not
    // synchronously inside onFocusChanged, or it loses a race with the IME on some
    // devices and the next keystroke lands beside the old digits instead of
    // replacing them.
    LaunchedEffect(hourFocused) {
        if (hourFocused) hourField = hourField.copy(selection = TextRange(0, hourField.text.length))
    }
    LaunchedEffect(minuteFocused) {
        if (minuteFocused) minuteField = minuteField.copy(selection = TextRange(0, minuteField.text.length))
    }
    LaunchedEffect(advanceToMinutePending) {
        if (advanceToMinutePending) {
            focusManager.clearFocus()
            minuteFocusRequester.requestFocus()
            advanceToMinutePending = false
        }
    }
    LaunchedEffect(finishPending) {
        if (finishPending) {
            focusManager.clearFocus()
            onDone()
            finishPending = false
        }
    }
    // Tapping this box already means "edit this time" — requiring a second tap just
    // to start typing would be a redundant extra step.
    LaunchedEffect(Unit) {
        hourFocusRequester.requestFocus()
        keyboardController?.show()
    }
    // Neither field holding focus means the user tapped away without an explicit
    // Done — exit inline mode the same as an explicit finish (the value is already
    // live-synced, so there's nothing left to flush). Guarded by hasFocusedOnce so
    // this doesn't fire on the first composition, before the LaunchedEffect above has
    // actually claimed focus yet.
    LaunchedEffect(hourFocused, minuteFocused) {
        if (hourFocused || minuteFocused) {
            hasFocusedOnce = true
        } else if (hasFocusedOnce) {
            onDone()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        InlineTimeDigitField(
            value = hourField,
            focused = hourFocused,
            focusRequester = hourFocusRequester,
            onFocusChange = { hourFocused = it },
            onValueChange = { new ->
                val digits = new.text.filter { it.isDigit() }.take(2)
                val parsed = digits.toIntOrNull()
                if (digits.isEmpty() || (parsed != null && parsed in 0..12)) {
                    hourField = new.copy(text = digits)
                    if (parsed != null && parsed in 1..12) {
                        hour12 = parsed
                        pushValue()
                    }
                    val singleDigitUnambiguous = digits.length == 1 && (parsed ?: 0) >= 2
                    if (digits.length == 2 || singleDigitUnambiguous) {
                        advanceToMinutePending = true
                    }
                }
            },
            imeAction = ImeAction.Next,
            onImeAction = { minuteFocusRequester.requestFocus() }
        )
        Text(":", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 2.dp))
        InlineTimeDigitField(
            value = minuteField,
            focused = minuteFocused,
            focusRequester = minuteFocusRequester,
            onFocusChange = { minuteFocused = it },
            onValueChange = { new ->
                val digits = new.text.filter { it.isDigit() }.take(2)
                val parsed = digits.toIntOrNull()
                if (digits.isEmpty() || (parsed != null && parsed in 0..59)) {
                    minuteField = new.copy(text = digits)
                    if (parsed != null) pushValue()
                    val singleDigitUnambiguous = digits.length == 1 && (parsed ?: 0) >= 6
                    if (digits.length == 2 || singleDigitUnambiguous) {
                        val finalMinute = (parsed ?: 0)
                        minuteField = minuteField.copy(text = finalMinute.toString().padStart(2, '0'))
                        finishPending = true
                    }
                }
            },
            imeAction = ImeAction.Done,
            onImeAction = { finishPending = true }
        )
        Spacer(Modifier.width(6.dp))
        InlineAmPmToggle(isPm = isPm, onChange = { isPm = it; pushValue() })
    }
}

@Composable
private fun InlineTimeDigitField(
    value: TextFieldValue,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            fontFamily = NumeralFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .width(44.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { innerTextField() }
        }
    )
}

@Composable
private fun InlineAmPmToggle(isPm: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        listOf(false, true).forEach { pm ->
            val selected = pm == isPm
            Text(
                text = if (pm) stringResource(R.string.schedule_edit_time_dialog_pm) else stringResource(R.string.schedule_edit_time_dialog_am),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onChange(pm) }
                    .padding(vertical = 1.dp)
            )
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


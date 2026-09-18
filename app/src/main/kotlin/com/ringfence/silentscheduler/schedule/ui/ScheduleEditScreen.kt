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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.LayoutDirection
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
    // The roller stays collapsed until the user has actually touched a time box —
    // activeTarget itself always has a value (defaulting to START), so it can't be
    // used on its own to tell "user picked a target" apart from "no interaction yet."
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
            // A tap anywhere on the screen that isn't itself a focusable/clickable
            // element (a time box, a button, empty space between sections) clears
            // focus and hides the keyboard — the "tap outside to dismiss" behavior a
            // plain Android View gets for free, which Compose doesn't: unlike a real
            // View, tapping an unrelated Compose clickable doesn't automatically take
            // focus away from whatever else currently holds it. This isn't a phone
            // setting to enable; every app has to implement it itself. No indication
            // (ripple) since this is a background dismiss action, not a button.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            )
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
                // The confirmed browse-and-scroll picker: hour/minute/AM-PM as a
                // rolling wheel, same idea as a Samsung alarm's own time picker —
                // replaced the old flat 15-minute list entirely. Tapping a box's time
                // value still opens the keypad exactly as before, unaffected by this.
                TimeRoller(
                    resetKey = activeTarget,
                    minuteOfDay = if (activeTarget == TimeTarget.START) startMinuteOfDay else endMinuteOfDay,
                    onValueChange = { minuteOfDay ->
                        // Same reasoning as the old list's onPick: also closes that
                        // box's own inline editor, since its typed fields only ever
                        // initialize once and wouldn't otherwise notice this update.
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

        // Merged with "When it ends" below into one section: both are about how this
        // schedule behaves, and giving each its own full all-caps header (matching
        // Label/Window/Repeat) overstated how distinct they really are. "When it
        // ends" is now a lighter sub-heading rather than a second SectionLabel.
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

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.schedule_edit_revert_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        // No border here (unlike the time boxes) — this card doesn't need to compete
        // for attention the way the primary Start/End controls do; a soft tonal fill
        // is enough to group the two options without another stroke on the screen.
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Each option's own subtitle already says what it does — a further
                // hint line repeating the same thing below the whole card was pure
                // duplication, removed rather than kept "just in case."
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

        if (initial != null && onDelete != null) {
            Spacer(Modifier.height(24.dp))
            // A plain text action, not another outlined pill: this is the least
            // frequent action on the screen, and it already reads as destructive
            // from its error color alone — it doesn't need its own border competing
            // with the Start/End boxes for attention.
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
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

// v2 "Lux" doc section 3 ("The rolling wheel picker"): exact geometry — the feel
// depends on these numbers matching precisely, not just approximately.
private val RollerItemHeight = 44.dp
private const val ROLLER_VISIBLE_ROWS = 5
private val RollerViewportHeight = RollerItemHeight * ROLLER_VISIBLE_ROWS // 220dp
private val RollerEdgePadding = RollerItemHeight * 2 // 88dp top/bottom, so row 0 can centre

/**
 * Rolling wheel time entry — hour, minute, AM/PM as three independently flingable
 * columns — the confirmed "browse and scroll" alternative to the keypad, replacing
 * the old flat 15-minute list.
 *
 * [resetKey] exists for the same reason [InlineTimeEditor]'s value has to be
 * live-synced rather than committed once at the end: this composable's own
 * hour/minute/AM-PM state must reinitialize from [minuteOfDay] when the *target*
 * changes (switching from editing Start to End) but must NOT reinitialize on every
 * scroll settle of its own — since each settle calls [onValueChange], which flows
 * back down as a new [minuteOfDay], and naively keying on that would fight the very
 * scroll position it just settled into. Wrapping everything in `key(resetKey)`
 * (typically the active Start/End target) disposes and recreates all of this
 * composable's remembered state — the three columns' scroll positions included —
 * only when that target actually changes.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TimeRoller(
    resetKey: Any,
    minuteOfDay: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    key(resetKey) {
        val initialHour24 = minuteOfDay / 60
        var hour12 by remember { mutableIntStateOf(((initialHour24 + 11) % 12) + 1) }
        var minute by remember { mutableIntStateOf(minuteOfDay % 60) }
        var isPm by remember { mutableStateOf(initialHour24 >= 12) }
        // Tracks the last value *this* roller itself produced, so an external change
        // to minuteOfDay (the keypad being used on the same box while the roller is
        // still visible) can be told apart from this roller's own scroll settles —
        // without it, hour12/minute/isPm above would never learn about a keypad edit,
        // since they only ever initialize once per resetKey.
        var lastPushed by remember { mutableIntStateOf(minuteOfDay) }

        fun push() {
            val hour24 = (hour12 % 12) + if (isPm) 12 else 0
            val value = hour24 * 60 + minute
            lastPushed = value
            onValueChange(value)
        }

        LaunchedEffect(minuteOfDay) {
            if (minuteOfDay != lastPushed) {
                val hour24 = minuteOfDay / 60
                hour12 = ((hour24 + 11) % 12) + 1
                minute = minuteOfDay % 60
                isPm = hour24 >= 12
                lastPushed = minuteOfDay
            }
        }

        // A fast fling on a short column (AM/PM's 2 rows) or one that starts already
        // at an end (hour's row 12, the last one) has nowhere left for that column's
        // own LazyColumn to put the leftover motion — by default it bubbles up to
        // this screen's outer scroll, dragging the whole page along with what was
        // meant to be a roller swipe. Claiming that leftover here, rather than
        // leaving it unconsumed, keeps every rough scroll on this roller contained
        // to the roller itself.
        val claimLeftoverScroll = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
                override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
            }
        }
        Box(modifier = modifier.nestedScroll(claimLeftoverScroll)) {
            // Doc section 3: selection band — left/right 14dp inset, the centre row's
            // height, radius 12, accentSoft, drawn once behind all three columns
            // (rather than per-column) so it reads as one shared picker.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(RollerItemHeight)
                    .align(Alignment.Center)
            ) {}
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RollerColumn(
                    itemCount = 12,
                    selectedIndex = hour12 - 1,
                    labelFor = { (it + 1).toString() },
                    onSettled = { hour12 = it + 1; push() },
                    horizontalAlignment = Alignment.End,
                    contentPadding = PaddingValues(end = 12.dp),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ":",
                    fontFamily = NumeralFontFamily,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(12.dp),
                    textAlign = TextAlign.Center
                )
                RollerColumn(
                    itemCount = 60,
                    selectedIndex = minute,
                    labelFor = { it.toString().padStart(2, '0') },
                    onSettled = { minute = it; push() },
                    horizontalAlignment = Alignment.Start,
                    contentPadding = PaddingValues(start = 12.dp),
                    modifier = Modifier.weight(1f)
                )
                val amLabel = stringResource(R.string.schedule_edit_time_dialog_am)
                val pmLabel = stringResource(R.string.schedule_edit_time_dialog_pm)
                RollerColumn(
                    itemCount = 2,
                    selectedIndex = if (isPm) 1 else 0,
                    labelFor = { if (it == 0) amLabel else pmLabel },
                    onSettled = { isPm = it == 1; push() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(76.dp)
                )
            }
        }
    }
}

/**
 * One flingable, snap-to-row column of [itemCount] rows — doc section 3's exact
 * geometry: 220dp viewport (5 visible 44dp rows), 88dp top/bottom content padding so
 * row 0 can reach the centre band, `rememberSnapFlingBehavior` (the doc's own
 * Android recommendation) rather than a hand-rolled settle correction. The selected
 * row is full-opacity/weight-600; others sit at 50% opacity/weight-400, animated over
 * ~180ms as the selection moves.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RollerColumn(
    itemCount: Int,
    selectedIndex: Int,
    labelFor: (Int) -> String,
    onSettled: (Int) -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) return@collect
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@collect
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val centered = layoutInfo.visibleItemsInfo.minByOrNull {
                kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
            } ?: return@collect
            val index = centered.index.coerceIn(0, itemCount - 1)
            onSettled(index)
        }
    }
    // Keeps this column in sync if its value ever changes from outside a scroll of
    // its own — e.g. the keypad being used while the roller is still visible, or the
    // wheel opening already scrolled to the current value.
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Box(modifier = modifier.height(RollerViewportHeight)) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = RollerEdgePadding,
                bottom = RollerEdgePadding,
                start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = contentPadding.calculateEndPadding(LayoutDirection.Ltr)
            )
        ) {
            items(itemCount) { index ->
                val isSelected = index == selectedIndex
                val alpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.5f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                    label = "rollerRowAlpha"
                )
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(RollerItemHeight),
                    contentAlignment = when (horizontalAlignment) {
                        Alignment.Start -> Alignment.CenterStart
                        Alignment.End -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                ) {
                    Text(
                        labelFor(index),
                        fontFamily = NumeralFontFamily,
                        fontSize = 26.sp,
                        letterSpacing = (-1).sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alpha(alpha)
                    )
                }
            }
        }
        // Doc section 3: fade masks — non-interactive overlays fading the top/bottom
        // rows toward the surface color so the wheel reads as receding, not clipped.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface,
                        0.38f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surface
                    )
                )
        )
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
 * roller below is currently centered on.
 *
 * Tapping the label row only selects this box as the active target (centering the
 * roller on its value) — tapping the time value itself, inspired by a
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


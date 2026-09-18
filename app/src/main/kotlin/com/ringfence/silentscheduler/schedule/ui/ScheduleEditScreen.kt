package com.ringfence.silentscheduler.schedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    // v2 "Lux" doc section 3: which half of the Start/End segmented tab is active —
    // the wheel below always targets this one and re-scrolls to its value when it
    // changes (see [TimeRoller]'s `resetKey`).
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
        WindowCard(
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            activeTarget = activeTarget,
            onTargetChange = { activeTarget = it },
            isInvalidRange = isInvalidRange,
            onValueChange = { minuteOfDay ->
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

        // v2 "Lux" doc section 3: fixed bottom bar, pinned outside the scroll area —
        // there's no more inline keypad to cover it, so it's always visible.
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

// User feedback on the v2 Lux doc's own 5-visible-row geometry: only one neighbour
// above and below the selected row reads more like a real dial, so this app uses 3
// visible rows (1 above, selected, 1 below) rather than the doc's 5 — everything
// else about the wheel's feel (44dp rows, snap fling, fade masks) still matches.
private val RollerItemHeight = 44.dp
private const val ROLLER_VISIBLE_ROWS = 3
private val RollerViewportHeight = RollerItemHeight * ROLLER_VISIBLE_ROWS
private val RollerEdgePadding = RollerItemHeight * ((ROLLER_VISIBLE_ROWS - 1) / 2) // so the selected row can centre

// A circular column (hour, minute) is implemented as a very large but finite list of
// [CIRCULAR_LOOP_COUNT] repeats of the real values, started in the middle repeat —
// far enough from either end that no real user fling can ever reach an edge, so it
// reads as an infinite dial: hour wraps 12 -> 1, minute wraps 59 -> 0, in both
// directions.
private const val CIRCULAR_LOOP_COUNT = 1001

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
                    circular = true,
                    horizontalAlignment = Alignment.End,
                    contentPadding = PaddingValues(end = 28.dp),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ":",
                    fontFamily = NumeralFontFamily,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.Center
                )
                RollerColumn(
                    itemCount = 60,
                    selectedIndex = minute,
                    labelFor = { it.toString().padStart(2, '0') },
                    onSettled = { minute = it; push() },
                    circular = true,
                    horizontalAlignment = Alignment.Start,
                    contentPadding = PaddingValues(start = 28.dp),
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
    circular: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    // A non-circular column (AM/PM) is just [itemCount] rows; a circular one (hour,
    // minute) is [CIRCULAR_LOOP_COUNT] repeats of those same [itemCount] rows, so
    // scrolling past the last real row keeps going into the next repeat instead of
    // hitting an end.
    val virtualItemCount = if (circular) itemCount * CIRCULAR_LOOP_COUNT else itemCount
    val middleRepeatBase = if (circular) (CIRCULAR_LOOP_COUNT / 2) * itemCount else 0

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = middleRepeatBase + selectedIndex)
    val flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState)

    /** The virtual index nearest [current] that maps (mod [itemCount]) to [target]. */
    fun nearestVirtualIndex(current: Int, target: Int): Int {
        val base = current - Math.floorMod(current, itemCount)
        return listOf(base - itemCount + target, base + target, base + itemCount + target)
            .minByOrNull { kotlin.math.abs(it - current) }!!
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) return@collect
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@collect
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val centered = layoutInfo.visibleItemsInfo.minByOrNull {
                kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
            } ?: return@collect
            val index = Math.floorMod(centered.index, itemCount)
            onSettled(index)
        }
    }
    // Keeps this column in sync if its value ever changes from outside a scroll of
    // its own — e.g. the keypad being used while the roller is still visible, or the
    // wheel opening already scrolled to the current value.
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && Math.floorMod(listState.firstVisibleItemIndex, itemCount) != selectedIndex) {
            val target = if (circular) {
                nearestVirtualIndex(listState.firstVisibleItemIndex, selectedIndex)
            } else {
                selectedIndex
            }
            listState.scrollToItem(target)
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
            items(virtualItemCount) { virtualIndex ->
                val index = Math.floorMod(virtualIndex, itemCount)
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
 * v2 "Lux" doc section 3 ("Add / edit window — the important one"): a single card
 * holding the Start/End segmented tab, the shared wheel, and a footer with the
 * duration caption + "Crosses midnight" tag — replacing the old two side-by-side
 * time boxes plus their own separate duration/tag row. Tapping a tab half only
 * re-targets the wheel (no more inline keypad entry — the wheel is the only input
 * now, per the doc).
 */
@Composable
private fun WindowCard(
    startMinuteOfDay: Int,
    endMinuteOfDay: Int,
    activeTarget: TimeTarget,
    onTargetChange: (TimeTarget) -> Unit,
    onValueChange: (Int) -> Unit,
    isInvalidRange: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Column {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(6.dp)) {
                    TimeTabHalf(
                        label = stringResource(R.string.schedule_edit_start),
                        minuteOfDay = startMinuteOfDay,
                        isSelected = activeTarget == TimeTarget.START,
                        onClick = { onTargetChange(TimeTarget.START) },
                        modifier = Modifier.weight(1f)
                    )
                    TimeTabHalf(
                        label = stringResource(R.string.schedule_edit_end),
                        minuteOfDay = endMinuteOfDay,
                        isSelected = activeTarget == TimeTarget.END,
                        onClick = { onTargetChange(TimeTarget.END) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            TimeRoller(
                resetKey = activeTarget,
                minuteOfDay = if (activeTarget == TimeTarget.START) startMinuteOfDay else endMinuteOfDay,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (isInvalidRange) {
                Box(modifier = Modifier.padding(16.dp)) {
                    InvalidRangeCard()
                }
            } else {
                val crossesMidnight = endMinuteOfDay <= startMinuteOfDay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(
                            R.string.schedule_edit_duration_caption,
                            formatDurationMinutes(minutesBetween(startMinuteOfDay, endMinuteOfDay).toLong())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (crossesMidnight) {
                        CrossesMidnightTag()
                    }
                }
            }
        }
    }
}

/** One half of the Start/End segmented tab — doc section 3: label stacked over the
 * time value, the selected half getting a raised surface "thumb" and an accent-tinted
 * value. */
@Composable
private fun TimeTabHalf(
    label: String,
    minuteOfDay: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatMinuteOfDay(minuteOfDay),
                fontFamily = NumeralFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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


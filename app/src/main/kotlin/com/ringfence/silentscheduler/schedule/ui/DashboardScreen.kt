package com.ringfence.silentscheduler.schedule.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.theme.DangerRedDark
import com.ringfence.silentscheduler.core.theme.DangerRedLight
import com.ringfence.silentscheduler.core.theme.LocalHeroPalette
import com.ringfence.silentscheduler.core.theme.NumeralFontFamily
import com.ringfence.silentscheduler.core.time.formatActiveCountdown
import com.ringfence.silentscheduler.core.ui.PillSwitch
import kotlin.math.roundToInt
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
    val selectedIds by viewModel.selectedIds.collectAsState()

    // The notification permission (or the app-level notification toggle) can change
    // while the Dashboard isn't in the foreground — from system Settings, or Android's
    // auto-revoke for unused permissions — so it's re-checked on every resume, not
    // just once when the ViewModel was created.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNotificationPermissionStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val selectionMode = selectedIds.isNotEmpty()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // S-01/S-02/S-03: derived from viewport height, not stored — a rotation or a
    // multi-window resize should reflect immediately, not stick to launch-time size.
    val compact = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_THRESHOLD_DP

    // The header nudges up a little as the body scrolls — closing its own top gap,
    // never eating into the header's own content — so it stays a "little bit
    // scrollable" without ever clipping or fully hiding the day label/icons.
    val density = LocalDensity.current
    val maxHeaderCollapseDp = 16.dp
    val maxHeaderCollapsePx = with(density) { maxHeaderCollapseDp.toPx() }
    var headerCollapsePx by remember { mutableFloatStateOf(0f) }
    val headerNestedScrollConnection = remember(maxHeaderCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling content up (negative delta): collapse the header first.
                if (available.y >= 0f) return Offset.Zero
                val newCollapsePx = (headerCollapsePx - available.y).coerceIn(0f, maxHeaderCollapsePx)
                val consumedPx = -(newCollapsePx - headerCollapsePx)
                headerCollapsePx = newCollapsePx
                return Offset(0f, consumedPx)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Scrolling content down (positive delta) but the list has no more
                // room to give: expand the header back with whatever's left over.
                if (available.y <= 0f) return Offset.Zero
                val newCollapsePx = (headerCollapsePx - available.y).coerceIn(0f, maxHeaderCollapsePx)
                val consumedPx = -(newCollapsePx - headerCollapsePx)
                headerCollapsePx = newCollapsePx
                return Offset(0f, consumedPx)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 18.dp)
                .nestedScroll(headerNestedScrollConnection)
        ) {
            // headerCollapsePx changes on every scroll frame (drag and fling alike),
            // so reading it directly in the composable body — via a plain
            // Spacer(Modifier.height(dp)) — recomposed this whole screen once per
            // pixel scrolled, fighting the LazyColumn's own already-smooth scrolling
            // with visible stutter. Modifier.layout reads it during the layout phase
            // instead, which only re-measures this one Spacer rather than recomposing.
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val gapPx = (maxHeaderCollapsePx - headerCollapsePx).roundToInt().coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = gapPx))
                        layout(placeable.width, gapPx) {
                            placeable.placeRelative(0, 0)
                        }
                    }
            )

            // The day label and the +/settings icons keep almost all of their space —
            // only the gap above them breathes with the scroll — so the header stays
            // fully readable no matter how far the body has scrolled.
            if (selectionMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel_selection))
                    }
                    Text(
                        stringResource(R.string.dashboard_selected_count, selectedIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val dangerRed = if (isSystemInDarkTheme()) DangerRedDark else DangerRedLight
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete_selected),
                            tint = dangerRed
                        )
                    }
                }
            } else {
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
            }

            Spacer(Modifier.height(if (compact) 12.dp else 16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    if (state.showNotificationsDisabledBanner) {
                        val context = LocalContext.current
                        NotificationsDisabledBanner(
                            onOpenNotificationSettings = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        )
                        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                    }

                    HeroPanel(
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

                    if (state.isAllOff) {
                        AllSchedulesPausedPanel(onResumeAll = { viewModel.resumeAll() })
                        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
                    }
                }

                val anyActive = state.rows.any { it.isActiveNow }
                items(state.rows, key = { it.schedule.id }) { row ->
                    ScheduleRow(
                        row = row,
                        anyOtherActive = anyActive && !row.isActiveNow,
                        selectionMode = selectionMode,
                        isSelected = row.schedule.id in selectedIds,
                        onToggle = { viewModel.toggleEnabled(row.schedule) },
                        onClick = {
                            if (selectionMode) {
                                viewModel.toggleSelection(row.schedule.id)
                            } else {
                                onEditSchedule(row.schedule.id)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(row.schedule.id) }
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }

        if (!selectionMode) {
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dashboard_delete_selected_title, selectedIds.size)) },
            text = { Text(stringResource(R.string.dashboard_delete_selected_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteConfirm = false
                }) {
                    Text(
                        stringResource(R.string.dashboard_delete_selected_confirm),
                        color = if (isSystemInDarkTheme()) DangerRedDark else DangerRedLight
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.dashboard_delete_selected_cancel))
                }
            }
        )
    }
}

/**
 * v2 "Lux" doc section 2 (Home — hero panel): replaces the old plain white ring
 * card with a full-width indigo gradient card. The countdown/ring math itself
 * ([HeroRing]) is untouched from the v1 [StatusRing] it replaces — only the
 * container, colours and the CTA (now a white pill on the gradient) changed.
 */
@Composable
private fun HeroPanel(
    state: DashboardUiState,
    compact: Boolean,
    onEndNow: () -> Unit,
    onSilentNow: () -> Unit,
    onTurnSoundOn: () -> Unit
) {
    val hero = LocalHeroPalette.current
    val gap = if (compact) 12.dp else 16.dp

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(30.dp),
                ambientColor = hero.heroInk.copy(alpha = 0.55f),
                spotColor = hero.heroInk.copy(alpha = 0.55f)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(hero.gradient))
        ) {
            // Two decorative, non-interactive radial blooms — doc section 2's
            // "top-right"/"bottom-left" corner light. Clipped by the parent Surface's
            // own shape, so nothing needs its own explicit clip here.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 52.dp, y = (-70).dp)
                    .size(190.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-46).dp, y = 86.dp)
                    .size(210.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.09f), Color.Transparent)),
                        CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val active = state.active
                // Called from this one call site regardless of active/idle — not from
                // inside the branches below — so its animateFloatAsState/animateColorAsState
                // instances stay alive across an active<->idle transition instead of being
                // torn down and recreated. That's what turns "silence just started/ended"
                // into a felt 900ms sweep of the ring filling or draining (H-04's ring
                // motion, applied to the on/off edge itself, not just the per-second tick).
                HeroRing(active = active, idleCountdownText = state.idleCountdownText, compact = compact)
                Spacer(Modifier.height(gap / 2))
                Text(
                    active?.untilText ?: state.idleSubText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = hero.onHeroDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
                Spacer(Modifier.height(gap))
                when {
                    active != null -> {
                        HeroPillButton(
                            text = stringResource(R.string.dashboard_end_silence_now),
                            onClick = onEndNow,
                            hero = hero
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.dashboard_end_silence_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = hero.onHeroFaint,
                            textAlign = TextAlign.Center
                        )
                        if (active.alreadySilentWarning) {
                            Spacer(Modifier.height(gap * 0.75f))
                            AlreadySilentPanel(
                                // R-15: quick silence has no schedule to attach an override to.
                                onTurnSoundOn = onTurnSoundOn.takeIf { active.source is ActiveSource.FromSchedule }
                            )
                        }
                    }
                    else -> {
                        HeroPillButton(
                            text = stringResource(R.string.dashboard_silent_now),
                            onClick = onSilentNow,
                            hero = hero
                        )
                    }
                }
            }
        }
    }
}

/**
 * The hero panel's single white-on-dark button (doc section 2): full-width, height
 * 56, fully rounded, 16sp w700 label in [HeroPalette.heroInk], lifting 1dp on press.
 * This is the only white-on-gradient button in the app by design — nothing else
 * competes with it for attention.
 */
@Composable
private fun HeroPillButton(text: String, onClick: () -> Unit, hero: com.ringfence.silentscheduler.core.theme.HeroPalette) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val liftDp by animateDpAsState(targetValue = if (isPressed) 0.dp else 1.dp, label = "heroCtaLift")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = hero.onHero,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .offset(y = -liftDp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(percent = 50),
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = hero.heroInk
            )
        }
    }
}

/**
 * H-04/H-04b/H-05: ticks every second (not just on the ViewModel's coarser 30s
 * refresh) and animates the ring arc 900ms linear between ticks, per the design's
 * motion spec. Kept local to the Composable rather than in the ViewModel's uiState
 * so a live countdown doesn't force the whole schedule list to recompose every second.
 *
 * Also covers the active<->idle edge itself: because the caller ([StatusCard]) holds
 * this at one call site instead of branching between two different composables, the
 * same [animateFloatAsState]/[animateColorAsState] instances carry across silence
 * starting or ending, so the border sweeps from empty to full (or back) and the
 * accent color fades in/out with it — a felt "it just started/ended," not an
 * instant cut. That start/end sweep runs slower ([TRANSITION_DURATION_MILLIS]) than
 * the per-second tick smoothing ([TICK_DURATION_MILLIS], kept close to H-04's
 * spec'd 900ms so each tick's animation finishes before the next one-second tick
 * arrives) — otherwise the same short duration that keeps ticking smooth would make
 * the on/off moment itself easy to miss.
 */
private const val TICK_DURATION_MILLIS = 900
private const val TRANSITION_DURATION_MILLIS = 1600

@Composable
private fun HeroRing(
    active: ActiveCardState?,
    idleCountdownText: String,
    compact: Boolean
) {
    val hero = LocalHeroPalette.current
    var remainingSeconds by remember(active?.startEpochMillis, active?.endEpochMillis) {
        mutableLongStateOf(active?.let { (it.endEpochMillis - System.currentTimeMillis()) / 1000 } ?: 0L)
    }
    LaunchedEffect(active?.startEpochMillis, active?.endEpochMillis) {
        if (active == null) return@LaunchedEffect
        while (true) {
            remainingSeconds = ((active.endEpochMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    var wasActive by remember { mutableStateOf(active != null) }
    val isTransitioning = wasActive != (active != null)
    SideEffect { wasActive = active != null }
    val durationMillis = if (isTransitioning) TRANSITION_DURATION_MILLIS else TICK_DURATION_MILLIS

    val totalSeconds = active?.let { ((it.endEpochMillis - it.startEpochMillis) / 1000).coerceAtLeast(1) } ?: 1L
    val targetFraction = if (active != null) (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        label = "silenceRingProgress"
    )
    // Doc section 2: "progress = pure white" — the ring itself no longer switches
    // color between active/idle now that it lives on the gradient; only its fill
    // fraction (via [animatedFraction]) communicates state, same 900ms sweep as before.
    // S-01: ~78% size when compact (188dp -> 147dp per the doc's own ratio).
    ProgressRing(
        ringSize = if (compact) 147.dp else 188.dp,
        strokeWidth = 9.dp,
        progressFraction = animatedFraction,
        trackColor = hero.ringTrack,
        accentColor = hero.onHero
    ) {
        Text(
            stringResource(if (active != null) R.string.dashboard_silent_status else R.string.dashboard_sound_on_status),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = NumeralFontFamily,
            fontSize = 10.sp,
            letterSpacing = 1.3.sp,
            color = hero.onHeroDim
        )
        Spacer(Modifier.height(4.dp))
        val countdownText = if (active != null) formatActiveCountdown(remainingSeconds.coerceAtLeast(0)) else idleCountdownText
        Text(countdownText, style = countdownTextStyle(countdownText, compact), color = hero.onHero, maxLines = 1)
    }
}

/**
 * displayLarge (42sp) is sized for a short countdown like "51:27" or "—" — the
 * longer "Xh Ym" form (used past the first hour, active or idle) is wide enough at
 * that size to spill past the ring's own stroke instead of staying inside it, since
 * [HeroRing] draws this text over a fixed-size ring rather than one that grows with
 * its content.
 */
@Composable
private fun countdownTextStyle(text: String, compact: Boolean): TextStyle {
    val base = MaterialTheme.typography.displayLarge
    val isLongForm = text.contains('h')
    return when {
        isLongForm && compact -> base.copy(fontSize = 22.sp)
        isLongForm -> base.copy(fontSize = 28.sp)
        else -> base
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

/**
 * Shown above the row list when every schedule exists but none is armed — matches
 * design_handoff_silent_scheduler_android/SilentApp.dc.html's isAllOff panel exactly
 * (surface2 background, a "Resume all" chip), distinct from [AlreadySilentPanel]'s
 * accent-tinted secondaryContainer since this one isn't warning about anything.
 */
@Composable
private fun AllSchedulesPausedPanel(onResumeAll: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.dashboard_all_off_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.dashboard_all_off_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.clickable(onClick = onResumeAll)
            ) {
                Text(
                    stringResource(R.string.dashboard_resume_all),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Shown when the user wants banner/log notifications (Settings) but the system is
 * blocking them entirely — POST_NOTIFICATIONS denied or revoked, or the app disabled
 * from system notification settings. Without this, a schedule silently starts/ends
 * with no visible confirmation and nothing tells the user why.
 */
@Composable
private fun NotificationsDisabledBanner(onOpenNotificationSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.dashboard_notifications_disabled_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    stringResource(R.string.dashboard_notifications_disabled_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.clickable(onClick = onOpenNotificationSettings)
            ) {
                Text(
                    stringResource(R.string.dashboard_notifications_disabled_cta),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Accent arc over a faint full-circle track, depleting as [progressFraction] (time remaining) drops to 0. */
@Composable
private fun ProgressRing(
    ringSize: Dp,
    progressFraction: Float,
    accentColor: Color,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = strokeWidth.toPx()
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

/**
 * H-11/H-15: the row's left edge marks its state at a glance — accent while it's the
 * one actually silencing right now, a faint outline while merely armed, and nothing
 * at all once disabled — matching design_handoff_silent_scheduler_android's
 * `edge: active ? accent : (s.on ? line : 'transparent')` exactly rather than only
 * relying on the "NOW" badge text.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleRow(
    row: ScheduleRowUiState,
    // Whether some OTHER row is the one currently silencing — used to dim this row
    // further when it isn't, so the active row reads as unmistakably current instead
    // of relying on the 3dp edge color alone (user feedback: the two states looked
    // too similar at a glance).
    anyOtherActive: Boolean,
    // Long-pressing any row enters selection mode by selecting just that one; while
    // active, a plain tap on any row toggles its own selection instead of opening
    // its editor, and the enable switch steps aside for a checkbox so there's only
    // one tap target per row.
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val edgeColor = when {
        row.isActiveNow -> MaterialTheme.colorScheme.primary
        row.schedule.isEnabled -> MaterialTheme.colorScheme.outline
        else -> Color.Transparent
    }
    val rowAlpha = if (anyOtherActive) 0.55f else 1f
    Surface(
        // Matches the design's cardRadius (24dp on Android) — the same rounding as
        // the status card above it, not the smaller 16dp this used before.
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(rowAlpha)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(edgeColor)
            )
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
                Spacer(Modifier.width(12.dp))
                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = null)
                } else {
                    PillSwitch(checked = row.schedule.isEnabled, onCheckedChange = { onToggle() })
                }
            }
        }
    }
}

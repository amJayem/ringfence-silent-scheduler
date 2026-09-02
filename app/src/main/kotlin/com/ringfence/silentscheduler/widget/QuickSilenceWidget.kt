package com.ringfence.silentscheduler.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ringfence.silentscheduler.MainActivity
import com.ringfence.silentscheduler.R
import dagger.hilt.android.EntryPointAccessors

/**
 * Renders W-07 (2x2: header + the four duration chips) from
 * design_handoff_silent_scheduler_android's AndroidSurface.dc.html widget-wide
 * mockup. A genuinely separate class from [QuickSilenceSmallWidget] — not just a
 * parameter on a shared one — because Glance's updateAll()/getGlanceIds() key off
 * the concrete GlanceAppWidget class to find a provider's placed instances; sharing
 * one class between the wide and compact receivers made those calls unable to tell
 * the two providers' instances apart. Each keeps its own copy of the state-resolving
 * boilerplate in provideGlance as the price of that separation.
 *
 * SizeMode.Exact rather than Responsive: measured on-device, the actual placed size
 * on this launcher (One UI) didn't match either of a fixed pair of preset sizes —
 * Responsive snaps to the nearest preset and renders content built for THAT size, so
 * a real container narrower/shorter than the preset it snapped to clipped content
 * (the sub-text and footer both got cut off). Exact always hands the composable the
 * real container, so the layout below is written to stay compact enough to fit a
 * small real allotment rather than assuming a specific size.
 */
class QuickSilenceWideWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance instantiates GlanceAppWidgetReceiver (and this class) via its own
        // reflection-based no-arg construction outside the normal onReceive lifecycle,
        // so Hilt field/constructor injection never runs on it — resolved here
        // instead, the same EntryPoint pattern the ActionCallbacks already use.
        val stateProvider = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetStateProvider()
        val state = stateProvider.buildState()
        provideContent { WideWidgetContent(state) }
    }
}

/** Renders W-01/W-02 (2x1: icon + kicker + big value + sub) — see [QuickSilenceWideWidget]. */
class QuickSilenceSmallWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stateProvider = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetStateProvider()
        val state = stateProvider.buildState()
        provideContent { SmallWidgetContent(state) }
    }
}

@Composable
private fun SmallWidgetContent(state: WidgetUiState) {
    // Icon + text together are the toggle "button" — one shared click on the row that
    // wraps both, so there's no dead gap between them — and a small dedicated icon
    // opens the app, same split the wide widget uses.
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground(state.silent))
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .toggleClickModifier(state),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            StatusIcon(silent = state.silent, size = 32.dp)
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                KickerText(state.kicker, state.silent)
                Text(
                    state.bigText,
                    maxLines = 1,
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = textColor())
                )
                Text(
                    state.subText,
                    maxLines = 1,
                    style = TextStyle(fontSize = 10.sp, color = dimColor())
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        OpenAppIcon()
    }
}

@Composable
private fun WideWidgetContent(state: WidgetUiState) {
    // Matches AndroidSurface.dc.html's widget-wide markup: icon + a kicker/headline
    // column + the big value, all sharing one header row (its headline column
    // ellipsizes on overflow, same as the design's own text-overflow:ellipsis — that
    // is the designed behavior for a narrow placement, not a bug to route around).
    // Now that the widget can be resized up to 5 columns wide, a real placement is
    // much closer to the design's own 392px-wide canvas, so ellipsis should be the
    // rare case rather than the default. The footer still only renders if the real
    // height comfortably fits it, since resizeMode also allows a short 2-row widget
    // where there just isn't room for a fourth line.
    val showFooter = LocalSize.current.height > 105.dp
    // Most taps on this widget are meant to set a quick duration or toggle from the
    // header, not launch the app — so "open the app" is a small, deliberate icon
    // rather than a whole free-space region. (An earlier attempt inset a "border
    // ring" out of a padded inner container, on the assumption that padding shrinks
    // a clickable element's hit area the way it does in Jetpack Compose proper —
    // verified on-device that Glance does NOT carry that behavior into RemoteViews;
    // the inner container's click claimed the full card regardless of its padding,
    // so the border was never actually reachable. A small icon with its own click,
    // as a direct sibling other elements already successfully override a parent's
    // click with — the same pattern the duration chips already rely on — is what
    // actually works.)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground(state.silent))
            .cornerRadius(28.dp)
            .padding(14.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().toggleClickModifier(state),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            StatusIcon(silent = state.silent, size = 30.dp)
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                KickerText(state.kicker, state.silent)
                Text(
                    state.subText,
                    maxLines = 1,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor())
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(
                state.bigText,
                maxLines = 1,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = textColor())
            )
            Spacer(GlanceModifier.width(8.dp))
            OpenAppIcon()
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            state.chips.forEachIndexed { index, chip ->
                if (index > 0) Spacer(GlanceModifier.width(8.dp))
                DurationChip(chip = chip, modifier = GlanceModifier.defaultWeight())
            }
        }
        if (showFooter) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                state.footerText,
                maxLines = 1,
                style = TextStyle(fontSize = 10.sp, color = dimColor(), textAlign = TextAlign.Center),
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OpenAppIcon() {
    // The clickable area is deliberately bigger than the icon graphic itself — a
    // 22dp tap target (the icon's own visual size) was hard to land a finger on
    // reliably; Android's own guidance calls for touch targets closer to 48dp.
    // The glyph stays the same visual size, just with more forgiving padding
    // around it before a tap counts as a miss.
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .cornerRadius(18.dp)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp)
        )
    }
}

@Composable
private fun StatusIcon(silent: Boolean, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(if (silent) accentSoftColor() else surface2Color())
            .cornerRadius(size),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(if (silent) R.drawable.ic_notification else R.drawable.ic_widget_bell),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (silent) accentColor() else dimColor()),
            modifier = GlanceModifier.size(size * 0.5f)
        )
    }
}

@Composable
private fun KickerText(kicker: String, silent: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Text(
        kicker,
        maxLines = 1,
        modifier = modifier,
        style = TextStyle(
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = if (silent) accentColor() else faintColor()
        )
    )
}

@Composable
private fun DurationChip(chip: WidgetChip, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (chip.isDefault) accentSoftColor() else surface2Color())
            .cornerRadius(15.dp)
            .clickable(actionRunCallback<StartChipDurationAction>(actionParametersOf(ChipMinutesKey to chip.minutes))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            chip.label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (chip.isDefault) accentColor() else textColor()
            )
        )
    }
}

@Composable
private fun GlanceModifier.toggleClickModifier(state: WidgetUiState): GlanceModifier {
    val context = LocalContext.current
    return if (state.dndAccessGranted) {
        this.clickable(actionRunCallback<ToggleSilenceAction>())
    } else {
        this.clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    }
}

private fun widgetBackground(silent: Boolean) =
    ColorProvider(if (silent) R.color.widget_silent_surface else R.color.widget_surface)

private fun textColor() = ColorProvider(R.color.widget_text)
private fun dimColor() = ColorProvider(R.color.widget_dim)
private fun faintColor() = ColorProvider(R.color.widget_faint)
private fun accentColor() = ColorProvider(R.color.widget_accent)
private fun accentSoftColor() = ColorProvider(R.color.widget_accent_soft)
private fun surface2Color() = ColorProvider(R.color.widget_surface2)

val ChipMinutesKey = ActionParameters.Key<Int>("chip_minutes")

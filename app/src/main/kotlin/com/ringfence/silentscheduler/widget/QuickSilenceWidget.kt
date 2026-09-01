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
 * Renders both W-01/W-02 (2x1: icon + kicker + big value + sub) and W-07 (2x2: same
 * header, plus the four duration chips) from design_handoff_silent_scheduler_android's
 * AndroidSurface.dc.html widget-small/widget-wide mockups.
 *
 * SizeMode.Exact rather than Responsive: measured on-device, the actual placed size
 * on this launcher (One UI) didn't match either of a fixed pair of preset sizes —
 * Responsive snaps to the nearest preset and renders content built for THAT size, so
 * a real container narrower/shorter than the preset it snapped to clipped content
 * (the sub-text and footer both got cut off). Exact always hands the composable the
 * real container, so the layout below is written to stay compact enough to fit a
 * small real allotment rather than assuming a specific size.
 */
class QuickSilenceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance instantiates GlanceAppWidgetReceiver (and this class) via its own
        // reflection-based no-arg construction outside the normal onReceive lifecycle,
        // so Hilt field/constructor injection never runs on it — resolved here
        // instead, the same EntryPoint pattern the ActionCallbacks already use.
        val stateProvider = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).widgetStateProvider()
        val state = stateProvider.buildState()
        provideContent {
            val size = LocalSize.current
            if (size.height > COMPACT_HEIGHT_THRESHOLD) {
                WideWidgetContent(state)
            } else {
                SmallWidgetContent(state)
            }
        }
    }

    private companion object {
        val COMPACT_HEIGHT_THRESHOLD = 70.dp
    }
}

@Composable
private fun SmallWidgetContent(state: WidgetUiState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground(state.silent))
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp)
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
}

@Composable
private fun WideWidgetContent(state: WidgetUiState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBackground(state.silent))
            .cornerRadius(26.dp)
            .padding(10.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().toggleClickModifier(state),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            StatusIcon(silent = state.silent, size = 24.dp)
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                KickerText(state.kicker, state.silent)
                Text(
                    state.subText,
                    maxLines = 1,
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = textColor())
                )
            }
            Text(
                state.bigText,
                maxLines = 1,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor())
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            state.chips.forEachIndexed { index, chip ->
                if (index > 0) Spacer(GlanceModifier.width(6.dp))
                DurationChip(chip = chip, modifier = GlanceModifier.defaultWeight())
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(
            state.footerText,
            maxLines = 1,
            style = TextStyle(fontSize = 9.sp, color = dimColor(), textAlign = TextAlign.Center),
            modifier = GlanceModifier.fillMaxWidth()
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
private fun KickerText(kicker: String, silent: Boolean) {
    Text(
        kicker,
        maxLines = 1,
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
            .height(32.dp)
            .background(if (chip.isDefault) accentSoftColor() else surface2Color())
            .cornerRadius(12.dp)
            .clickable(actionRunCallback<StartChipDurationAction>(actionParametersOf(ChipMinutesKey to chip.minutes))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            chip.label,
            style = TextStyle(
                fontSize = 12.sp,
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

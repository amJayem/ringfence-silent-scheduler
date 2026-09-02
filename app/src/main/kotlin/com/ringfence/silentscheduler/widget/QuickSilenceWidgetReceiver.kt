package com.ringfence.silentscheduler.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// No Hilt injection here — Glance instantiates receivers via reflection outside the
// normal onReceive lifecycle Hilt hooks into (see QuickSilenceWidget.provideGlance's
// own comment), so QuickSilenceWidget resolves its dependencies lazily instead.
class QuickSilenceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickSilenceWideWidget()
}

package com.ringfence.silentscheduler.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * A second, independently pickable provider for the same [QuickSilenceWidget] class —
 * W-01/W-02's compact 1-row form (6a/6b) needs its own fixed-height entry in the
 * widget picker, separate from [QuickSilenceWidgetReceiver]'s 2-row wide form (6c),
 * since one appwidget-provider XML can't offer two different fixed heights with no
 * vertical resize between them. QuickSilenceWidget itself already picks small vs wide
 * content purely from the real size it's given, so both receivers can point at the
 * exact same widget class — this one's XML just locks that size into the 1-row range.
 */
class QuickSilenceSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickSilenceWidget()
}

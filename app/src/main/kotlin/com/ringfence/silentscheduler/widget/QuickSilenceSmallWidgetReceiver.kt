package com.ringfence.silentscheduler.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * A second, independently pickable provider — W-01/W-02's compact 1-row form (6a/6b)
 * needs its own fixed-height entry in the widget picker, separate from
 * [QuickSilenceWidgetReceiver]'s 2-row wide form (6c), since one appwidget-provider
 * XML can't offer two different fixed heights with no vertical resize between them.
 */
class QuickSilenceSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickSilenceSmallWidget()
}

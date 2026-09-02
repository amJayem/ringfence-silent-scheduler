package com.ringfence.silentscheduler.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * W-06: "a stale widget is the single most damaging bug" — every place that changes
 * what the widget would show (starting/ending a quick silence, a schedule window
 * starting/ending, a schedule being toggled) calls this immediately afterward,
 * rather than waiting on the widget's own periodic update.
 *
 * Renders and pushes each placed instance directly via GlanceAppWidget.compose() +
 * AppWidgetManager.updateAppWidget(), the same direct path widget taps themselves
 * use — not updateAll(), which schedules the work through a WorkManager session
 * Glance's own team documents as "not suitable for real-time scenarios" (measured
 * here at anywhere from seconds to over a minute). Originally this used updateAll()
 * for every *other* placed instance while only the tapped one bypassed it directly;
 * running both paths against the same instances let Glance's own delayed session
 * update occasionally land after this one and repaint over it with stale/invalid
 * content, intermittently leaving a widget appearing blank until something else
 * happened to redraw it. Using this same direct path everywhere removes that race
 * entirely — there is now only one way any instance ever gets updated.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun refresh() {
        pushUpdates(QuickSilenceWideWidget())
        pushUpdates(QuickSilenceSmallWidget())
    }

    private suspend fun pushUpdates(widget: GlanceAppWidget) {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        manager.getGlanceIds(widget::class.java).forEach { glanceId ->
            pushUpdate(widget, glanceId, appWidgetManager)
        }
    }

    private suspend fun pushUpdate(widget: GlanceAppWidget, glanceId: GlanceId, appWidgetManager: AppWidgetManager) {
        val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId ?: return
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val remoteViews = widget.compose(context, glanceId, options)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}

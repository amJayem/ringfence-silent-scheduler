package com.ringfence.silentscheduler.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.compose
import dagger.hilt.android.EntryPointAccessors

private fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

/**
 * Glance's own updateAll()/update() go through a WorkManager-scheduled session —
 * documented by the Glance team as "not suitable for real-time scenarios," and
 * measured on-device taking anywhere from several seconds to over a minute. Tapping
 * a widget and having it visibly react is exactly a real-time scenario, so the
 * instance that was actually tapped updates itself here directly: compose() renders
 * this GlanceId's content straight to a RemoteViews, pushed straight to
 * AppWidgetManager, skipping Glance's session queue entirely. Other placed
 * instances (the other widget type, or another instance of the same one) still
 * rely on the slower WidgetRefresher.refresh() call already happening inside
 * whichever repository method this action invoked.
 */
private suspend fun instantSelfUpdate(context: Context, glanceId: GlanceId) {
    val manager = GlanceAppWidgetManager(context)
    val widget = when (glanceId) {
        in manager.getGlanceIds(QuickSilenceWideWidget::class.java) -> QuickSilenceWideWidget()
        in manager.getGlanceIds(QuickSilenceSmallWidget::class.java) -> QuickSilenceSmallWidget()
        else -> return
    }
    val remoteViews = widget.compose(context, glanceId)
    val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId ?: return
    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews)
}

/**
 * W-04/W-05: tapping the widget's header/ring starts or ends silence depending on
 * current state. WidgetActionHandler's own calls (QuickSilenceRepository.startSilence/
 * revertSilence, ScheduleTriggerHandler.handleEnd) already trigger the broader
 * WidgetRefresher.refresh() for every placed instance; instantSelfUpdate here is
 * only about making the specific tapped instance react without that delay.
 */
class ToggleSilenceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val handler = entryPoint(context).widgetActionHandler()
        if (handler.isCurrentlySilent()) {
            handler.endActiveSilence()
        } else {
            handler.startQuickSilenceWithDefaultDuration()
        }
        instantSelfUpdate(context, glanceId)
    }
}

/** W-08: tapping a duration chip on the 2x2 starts that duration immediately. */
class StartChipDurationAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val minutes = parameters[ChipMinutesKey] ?: return
        entryPoint(context).widgetActionHandler().startQuickSilence(minutes)
        instantSelfUpdate(context, glanceId)
    }
}

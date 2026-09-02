package com.ringfence.silentscheduler.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors

private fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

/**
 * W-04/W-05: tapping the widget's header/ring starts or ends silence depending on
 * current state. No explicit widget refresh here — WidgetActionHandler's own calls
 * (QuickSilenceRepository.startSilence/revertSilence, ScheduleTriggerHandler.handleEnd)
 * already trigger one, the same as every other caller of those.
 */
class ToggleSilenceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val handler = entryPoint(context).widgetActionHandler()
        if (handler.isCurrentlySilent()) {
            handler.endActiveSilence()
        } else {
            handler.startQuickSilenceWithDefaultDuration()
        }
    }
}

/** W-08: tapping a duration chip on the 2x2 starts that duration immediately. */
class StartChipDurationAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val minutes = parameters[ChipMinutesKey] ?: return
        entryPoint(context).widgetActionHandler().startQuickSilence(minutes)
    }
}

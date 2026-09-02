package com.ringfence.silentscheduler.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.ConcurrentHashMap

private fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

/**
 * Each tap enqueues a widget refresh through Glance's WorkManager-scheduled update
 * pipeline, which the Glance team itself documents as "not suitable for real-time
 * scenarios" — repeated taps in quick succession (a user tapping again because
 * nothing visibly happened yet, not because they changed their mind) queue up
 * redundant refresh jobs faster than that pipeline can drain them, which is what
 * was actually behind the widget occasionally taking a very long time to react.
 * Debouncing per GlanceId — rather than adding a "loading" indicator, which would
 * itself need to push another update through the very pipeline that's congested —
 * directly reduces how many jobs get queued in the first place.
 */
private object TapDebounce {
    private val lastAcceptedAt = ConcurrentHashMap<GlanceId, Long>()
    private const val COOLDOWN_MS = 1500L

    fun shouldProcess(glanceId: GlanceId): Boolean {
        val now = System.currentTimeMillis()
        val last = lastAcceptedAt[glanceId] ?: 0L
        if (now - last < COOLDOWN_MS) return false
        lastAcceptedAt[glanceId] = now
        return true
    }
}

/**
 * W-04/W-05: tapping the widget's header/ring starts or ends silence depending on
 * current state. No explicit widget refresh here — WidgetActionHandler's own calls
 * (QuickSilenceRepository.startSilence/revertSilence, ScheduleTriggerHandler.handleEnd)
 * already trigger one, the same as every other caller of those.
 */
class ToggleSilenceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!TapDebounce.shouldProcess(glanceId)) return
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
        if (!TapDebounce.shouldProcess(glanceId)) return
        val minutes = parameters[ChipMinutesKey] ?: return
        entryPoint(context).widgetActionHandler().startQuickSilence(minutes)
    }
}

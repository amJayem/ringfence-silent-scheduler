package com.ringfence.silentscheduler.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * W-06: "a stale widget is the single most damaging bug" — every place that changes
 * what the widget would show (starting/ending a quick silence, a schedule window
 * starting/ending, a schedule being toggled) calls this immediately afterward,
 * rather than waiting on the widget's own periodic update.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun refresh() {
        QuickSilenceWideWidget().updateAll(context)
        QuickSilenceSmallWidget().updateAll(context)
    }
}

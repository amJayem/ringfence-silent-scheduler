package com.ringfence.silentscheduler.widget

import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.data.ScheduleTriggerHandler
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Glance's ActionCallback classes are instantiated by reflection (a no-arg
 * constructor), not by Hilt — this is the standard way to reach the same
 * Hilt-provided singletons every other entry point (ViewModels, BroadcastReceivers)
 * already uses, from inside one of those callbacks.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetStateProvider(): WidgetStateProvider
    fun widgetActionHandler(): WidgetActionHandler
    fun quickSilenceRepository(): QuickSilenceRepository
    fun scheduleRepository(): ScheduleRepository
    fun triggerHandler(): ScheduleTriggerHandler
    fun settingsRepository(): SettingsRepository
}

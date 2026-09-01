package com.ringfence.silentscheduler.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Glance's ActionCallback classes are instantiated by reflection (a no-arg
 * constructor), not by Hilt — this is the standard way to reach the same
 * Hilt-provided singletons every other entry point (ViewModels, BroadcastReceivers)
 * already uses, from inside one of those callbacks. WidgetActionHandler and
 * WidgetStateProvider themselves take the actual repositories as constructor
 * dependencies — only these two need to be reachable from here.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetStateProvider(): WidgetStateProvider
    fun widgetActionHandler(): WidgetActionHandler
}

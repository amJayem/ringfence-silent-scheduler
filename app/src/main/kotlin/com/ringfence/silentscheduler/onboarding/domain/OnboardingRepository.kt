package com.ringfence.silentscheduler.onboarding.domain

import kotlinx.coroutines.flow.Flow

/**
 * Tracks whether the user has been through the post-permissions onboarding tour
 * (predefined-window picker + Quick Silence demo) — separate from the DND/exact-alarm
 * permission checks in [com.ringfence.silentscheduler.onboarding.OnboardingViewModel],
 * which re-evaluate on every launch since permissions can be revoked; this flag is a
 * one-time "have they seen it" marker that only ever moves from false to true.
 */
interface OnboardingRepository {
    fun observeIsTourCompleted(): Flow<Boolean>
    suspend fun markTourCompleted()

    /**
     * Whether the app has already fired the one-time POST_NOTIFICATIONS system prompt
     * (Android 13+). Separate from whether the permission is currently granted — a
     * user who denied it shouldn't be re-prompted with the system dialog on every
     * launch; the Dashboard's own banner covers ongoing recovery instead.
     */
    fun observeHasRequestedNotificationPermission(): Flow<Boolean>
    suspend fun markNotificationPermissionRequested()
}

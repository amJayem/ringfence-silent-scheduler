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
}

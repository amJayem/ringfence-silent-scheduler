package com.ringfence.silentscheduler.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ringfence.silentscheduler.onboarding.domain.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : OnboardingRepository {

    override fun observeIsTourCompleted(): Flow<Boolean> =
        dataStore.data.map { it[TOUR_COMPLETED] ?: false }

    override suspend fun markTourCompleted() {
        dataStore.edit { it[TOUR_COMPLETED] = true }
    }

    private companion object {
        val TOUR_COMPLETED = booleanPreferencesKey("onboarding_tour_completed")
    }
}

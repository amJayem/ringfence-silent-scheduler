package com.ringfence.silentscheduler.onboarding.di

import com.ringfence.silentscheduler.onboarding.data.OnboardingRepositoryImpl
import com.ringfence.silentscheduler.onboarding.domain.OnboardingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(impl: OnboardingRepositoryImpl): OnboardingRepository
}

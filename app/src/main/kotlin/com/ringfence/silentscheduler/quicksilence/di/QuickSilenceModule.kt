package com.ringfence.silentscheduler.quicksilence.di

import com.ringfence.silentscheduler.quicksilence.data.QuickSilenceRepositoryImpl
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class QuickSilenceModule {
    @Binds
    @Singleton
    abstract fun bindQuickSilenceRepository(impl: QuickSilenceRepositoryImpl): QuickSilenceRepository
}

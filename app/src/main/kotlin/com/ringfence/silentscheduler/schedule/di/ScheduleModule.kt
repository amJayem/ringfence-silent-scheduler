package com.ringfence.silentscheduler.schedule.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.ringfence.silentscheduler.schedule.data.ScheduleListSerializer
import com.ringfence.silentscheduler.schedule.data.ScheduleRepositoryImpl
import com.ringfence.silentscheduler.schedule.data.proto.ScheduleListProto
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleModule {

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    companion object {
        @Provides
        @Singleton
        fun provideScheduleDataStore(@ApplicationContext context: Context): DataStore<ScheduleListProto> =
            DataStoreFactory.create(
                serializer = ScheduleListSerializer,
                produceFile = { context.dataStoreFile("schedules.pb") }
            )
    }
}

package com.ringfence.silentscheduler.schedule.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.ringfence.silentscheduler.schedule.data.ScheduleListSerializer
import com.ringfence.silentscheduler.schedule.data.SchedulingScheduleRepository
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
    abstract fun bindScheduleRepository(impl: SchedulingScheduleRepository): ScheduleRepository

    companion object {
        @Provides
        @Singleton
        fun provideScheduleDataStore(@ApplicationContext context: Context): DataStore<ScheduleListProto> =
            DataStoreFactory.create(
                serializer = ScheduleListSerializer,
                // Without a handler, a schedules file that can't be parsed (a write
                // cut off by power loss, storage corruption) makes every read throw —
                // and every entry point reads it: the launch screen, each alarm
                // receiver, the widget. The app would crash on open, permanently,
                // with no way for the user to recover short of clearing app data.
                // Starting over with an empty list loses the schedules but keeps the
                // app usable.
                corruptionHandler = ReplaceFileCorruptionHandler { ScheduleListSerializer.defaultValue },
                produceFile = { context.dataStoreFile("schedules.pb") }
            )
    }
}

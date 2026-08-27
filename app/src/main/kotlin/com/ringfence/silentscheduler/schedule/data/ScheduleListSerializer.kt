package com.ringfence.silentscheduler.schedule.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.ringfence.silentscheduler.schedule.data.proto.ScheduleListProto
import java.io.InputStream
import java.io.OutputStream

object ScheduleListSerializer : Serializer<ScheduleListProto> {
    override val defaultValue: ScheduleListProto = ScheduleListProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ScheduleListProto {
        try {
            return ScheduleListProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read schedule data", exception)
        }
    }

    override suspend fun writeTo(t: ScheduleListProto, output: OutputStream) {
        t.writeTo(output)
    }
}

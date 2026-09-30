package org.fischman.alarmingnotifications.gcal.datastore

import androidx.datastore.core.Serializer
import org.fischman.alarmingnotifications.gcal.ScheduledAlarmCollection
import java.io.InputStream
import java.io.OutputStream

object ScheduledAlarmSerializer : Serializer<ScheduledAlarmCollection> {
    override val defaultValue: ScheduledAlarmCollection =
        ScheduledAlarmCollection.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ScheduledAlarmCollection {
        return ScheduledAlarmCollection.parseFrom(input)
    }

    override suspend fun writeTo(t: ScheduledAlarmCollection, output: OutputStream) {
        t.writeTo(output)
    }
}

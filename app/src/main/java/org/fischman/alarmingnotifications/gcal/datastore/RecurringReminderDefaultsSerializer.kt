package org.fischman.alarmingnotifications.gcal.datastore

import androidx.datastore.core.Serializer
import org.fischman.alarmingnotifications.gcal.RecurringEventDefaultsCollection
import java.io.InputStream
import java.io.OutputStream

object RecurringReminderDefaultsSerializer : Serializer<RecurringEventDefaultsCollection> {
    override val defaultValue: RecurringEventDefaultsCollection =
        RecurringEventDefaultsCollection.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): RecurringEventDefaultsCollection {
        return RecurringEventDefaultsCollection.parseFrom(input)
    }

    override suspend fun writeTo(t: RecurringEventDefaultsCollection, output: OutputStream) {
        t.writeTo(output)
    }
}

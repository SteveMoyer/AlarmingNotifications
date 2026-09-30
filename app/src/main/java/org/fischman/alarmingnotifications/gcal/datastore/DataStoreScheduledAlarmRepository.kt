package org.fischman.alarmingnotifications.gcal.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import org.fischman.alarmingnotifications.gcal.ScheduledAlarm
import org.fischman.alarmingnotifications.gcal.ScheduledAlarmCollection
import org.fischman.alarmingnotifications.gcal.ScheduledAlarmEntry
import org.fischman.alarmingnotifications.gcal.ScheduledAlarmRepository

class DataStoreScheduledAlarmRepository(
    private val dataStore: DataStore<ScheduledAlarmCollection>
) : ScheduledAlarmRepository {

    override suspend fun getAll(): List<ScheduledAlarm> {
        return dataStore.data.first().alarmsList.map { it.toDomain() }
    }

    override suspend fun replaceAll(alarms: List<ScheduledAlarm>) {
        dataStore.updateData { collection ->
            collection.toBuilder()
                .clearAlarms()
                .addAllAlarms(alarms.map { it.toProto() })
                .build()
        }
    }
}

private fun ScheduledAlarm.toProto() = ScheduledAlarmEntry.newBuilder()
    .setEventId(eventId)
    .setMinutes(minutes)
    .setLabel(label)
    .setTriggerAt(triggerAt)
    .build()

private fun ScheduledAlarmEntry.toDomain() = ScheduledAlarm(
    eventId = eventId,
    minutes = minutes,
    label = label,
    triggerAt = triggerAt,
)

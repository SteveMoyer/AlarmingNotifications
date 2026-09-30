package org.fischman.alarmingnotifications.gcal.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import org.fischman.alarmingnotifications.gcal.RecurringEventDefaults
import org.fischman.alarmingnotifications.gcal.RecurringEventDefaultsCollection
import org.fischman.alarmingnotifications.gcal.ReminderDefault
import org.fischman.alarmingnotifications.gcal.ReminderDefaultStatus
import org.fischman.alarmingnotifications.gcal.RecurringReminderDefaultsRepository
import org.fischman.alarmingnotifications.gcal.ReminderStatus
import org.fischman.alarmingnotifications.gcal.StoredCustomReminder

class DataStoreRecurringReminderDefaultsRepository(
    private val dataStore: DataStore<RecurringEventDefaultsCollection>
) : RecurringReminderDefaultsRepository {

    override suspend fun getReminderDefaults(eventKey: String): Map<Int, ReminderStatus> {
        return dataStore.data.first()
            .eventsList
            .find { it.eventKey == eventKey }
            ?.remindersList
            ?.filter { !it.isCustom }
            ?.associate { it.minutes to it.defaultStatus.toDomain() }
            ?: emptyMap()
    }

    override suspend fun getCustomReminders(eventKey: String): List<StoredCustomReminder> {
        return dataStore.data.first()
            .eventsList
            .find { it.eventKey == eventKey }
            ?.remindersList
            ?.filter { it.isCustom }
            ?.map { StoredCustomReminder(it.minutes, it.defaultStatus.toDomain()) }
            ?: emptyList()
    }

    override suspend fun saveReminderDefault(
        eventKey: String,
        minutes: Int,
        defaultStatus: ReminderStatus
    ) {
        val protoStatus = defaultStatus.toProto()
            ?: throw IllegalArgumentException(
                "Only RECURRING_ON and RECURRING_OFF can be persisted, got $defaultStatus"
            )

        dataStore.updateData { collection ->
            val existingEventIndex = collection.eventsList.indexOfFirst { it.eventKey == eventKey }

            if (existingEventIndex == -1) {
                collection.toBuilder()
                    .addEvents(
                        RecurringEventDefaults.newBuilder()
                            .setEventKey(eventKey)
                            .addReminders(
                                ReminderDefault.newBuilder()
                                    .setMinutes(minutes)
                                    .setDefaultStatus(protoStatus)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            } else {
                val existingEvent = collection.eventsList[existingEventIndex]
                val reminderIndex = existingEvent.remindersList.indexOfFirst { it.minutes == minutes }

                val updatedEventBuilder = existingEvent.toBuilder()
                if (reminderIndex == -1) {
                    updatedEventBuilder.addReminders(
                        ReminderDefault.newBuilder()
                            .setMinutes(minutes)
                            .setDefaultStatus(protoStatus)
                            .build()
                    )
                } else {
                    updatedEventBuilder.setReminders(
                        reminderIndex,
                        existingEvent.remindersList[reminderIndex].toBuilder()
                            .setDefaultStatus(protoStatus)
                            .build()
                    )
                }

                collection.toBuilder()
                    .setEvents(existingEventIndex, updatedEventBuilder.build())
                    .build()
            }
        }
    }

    override suspend fun deleteReminderDefault(eventKey: String, minutes: Int) {
        dataStore.updateData { collection ->
            val existingEventIndex = collection.eventsList.indexOfFirst { it.eventKey == eventKey }
            if (existingEventIndex == -1) {
                return@updateData collection
            }

            val existingEvent = collection.eventsList[existingEventIndex]
            val reminderIndex = existingEvent.remindersList.indexOfFirst { it.minutes == minutes && !it.isCustom }
            if (reminderIndex == -1) {
                return@updateData collection
            }

            val updatedEvent = existingEvent.toBuilder()
                .removeReminders(reminderIndex)
                .build()

            val collectionBuilder = collection.toBuilder()
                .setEvents(existingEventIndex, updatedEvent)

            if (updatedEvent.remindersList.isEmpty()) {
                collectionBuilder.removeEvents(existingEventIndex)
            }

            collectionBuilder.build()
        }
    }

    override suspend fun addCustomReminder(eventKey: String, minutes: Int, singleEventDate: Long) {
        dataStore.updateData { collection ->
            val existingEventIndex = collection.eventsList.indexOfFirst { it.eventKey == eventKey }

            if (existingEventIndex == -1) {
                collection.toBuilder()
                    .addEvents(
                        RecurringEventDefaults.newBuilder()
                            .setEventKey(eventKey)
                            .setSingleEventDate(singleEventDate)
                            .addReminders(customReminder(minutes))
                            .build()
                    )
                    .build()
            } else {
                val existingEvent = collection.eventsList[existingEventIndex]
                val updatedEventBuilder = existingEvent.toBuilder()
                    .setSingleEventDate(singleEventDate)
                if (existingEvent.remindersList.none { it.minutes == minutes && it.isCustom }) {
                    updatedEventBuilder.addReminders(customReminder(minutes))
                }

                collection.toBuilder()
                    .setEvents(existingEventIndex, updatedEventBuilder.build())
                    .build()
            }
        }
    }

    override suspend fun removeCustomReminder(eventKey: String, minutes: Int) {
        dataStore.updateData { collection ->
            val existingEventIndex = collection.eventsList.indexOfFirst { it.eventKey == eventKey }
            if (existingEventIndex == -1) {
                return@updateData collection
            }

            val existingEvent = collection.eventsList[existingEventIndex]
            val reminderIndex = existingEvent.remindersList.indexOfFirst { it.minutes == minutes && it.isCustom }
            if (reminderIndex == -1) {
                return@updateData collection
            }

            val updatedEvent = existingEvent.toBuilder()
                .removeReminders(reminderIndex)
                .build()

            val collectionBuilder = collection.toBuilder()
                .setEvents(existingEventIndex, updatedEvent)

            if (updatedEvent.remindersList.isEmpty()) {
                collectionBuilder.removeEvents(existingEventIndex)
            }

            collectionBuilder.build()
        }
    }

    override suspend fun pruneExpiredCustomReminders(before: Long) {
        dataStore.updateData { collection ->
            val kept = collection.eventsList.filter { event ->
                event.singleEventDate == 0L || event.singleEventDate >= before
            }
            if (kept.size == collection.eventsCount) {
                return@updateData collection
            }
            collection.toBuilder()
                .clearEvents()
                .addAllEvents(kept)
                .build()
        }
    }
}

private fun customReminder(minutes: Int): ReminderDefault =
    ReminderDefault.newBuilder()
        .setMinutes(minutes)
        .setDefaultStatus(ReminderDefaultStatus.RECURRING_ON)
        .setIsCustom(true)
        .build()

private fun ReminderDefaultStatus.toDomain(): ReminderStatus {
    return when (this) {
        ReminderDefaultStatus.RECURRING_ON -> ReminderStatus.RECURRING_ON
        ReminderDefaultStatus.RECURRING_OFF -> ReminderStatus.RECURRING_OFF
        ReminderDefaultStatus.DEFAULT_STATUS_UNSPECIFIED,
        ReminderDefaultStatus.UNRECOGNIZED -> ReminderStatus.DEFAULT_OFF
    }
}

private fun ReminderStatus.toProto(): ReminderDefaultStatus? {
    return when (this) {
        ReminderStatus.RECURRING_ON -> ReminderDefaultStatus.RECURRING_ON
        ReminderStatus.RECURRING_OFF -> ReminderDefaultStatus.RECURRING_OFF
        else -> null
    }
}

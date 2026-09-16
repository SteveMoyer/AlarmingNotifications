package org.fischman.alarmingnotifications.gcal.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import org.fischman.alarmingnotifications.gcal.RecurringEventDefaultsCollection

private const val DATA_STORE_FILE_NAME = "recurring_reminder_defaults.pb"

val Context.recurringReminderDefaultsDataStore: DataStore<RecurringEventDefaultsCollection> by dataStore(
    fileName = DATA_STORE_FILE_NAME,
    serializer = RecurringReminderDefaultsSerializer
)

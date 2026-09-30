package org.fischman.alarmingnotifications.gcal.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import org.fischman.alarmingnotifications.gcal.ScheduledAlarmCollection

private const val DATA_STORE_FILE_NAME = "scheduled_alarms.pb"

val Context.scheduledAlarmsDataStore: DataStore<ScheduledAlarmCollection> by dataStore(
    fileName = DATA_STORE_FILE_NAME,
    serializer = ScheduledAlarmSerializer
)

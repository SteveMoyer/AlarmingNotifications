
package org.fischman.alarmingnotifications.gcal

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class DailyAlarmConfigReader(
    private val context: Context,
    private val defaultsRepository: RecurringReminderDefaultsRepository,
) {
    private val calendarReader by lazy { AlarmingCalendarReader(this.context) }
    /**
     * Fetches calendar events from the device content provider off the main thread.
     * Requires android.permission.READ_CALENDAR.
     */
    suspend fun fetchDefaultDailyAlarmConfig(): List<CalendarAlarmConfig> {
        val events = calendarReader.fetchCalendarEvents()
        return events.map { event ->
            val savedDefaults = if (event.isRepeating) {
                defaultsRepository.getReminderDefaults(event.eventKey())
            } else {
                emptyMap()
            }
            event.toConfig(savedDefaults)
        }
    }
    fun AlarmingCalendarEvent.toConfig(savedDefaults: Map<Int, ReminderStatus>)= CalendarAlarmConfig (
        title=this.title,
        startTime=this.startTime,
        calendarName = this.calendarName,

        id=this.id,
        originalId=this.originalId,
        eventId=this.eventId,
        syncId=this.syncId,
        isRepeating=this.isRepeating,
        status =CalendarAlarmStatus.DEFAULT,
        reminders =this.reminderMinutes.map { minutes ->
            val defaultStatus = savedDefaults[minutes] ?: ReminderStatus.DEFAULT_OFF
            ReminderConfig(
                minutes = minutes,
                status = defaultStatus,
                defaultStatus = defaultStatus,
                originalDefaultStatus = defaultStatus,
            )
        }
    )
}

internal fun AlarmingCalendarEvent.eventKey(): String =
    originalId.takeIf { it.isNotBlank() } ?: eventId


package org.fischman.alarmingnotifications.gcal

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class DailyAlarmConfigReader(private val context: Context) {
    private val calendarReader by lazy { AlarmingCalendarReader(this.context) }
    /**
     * Fetches calendar events from the device content provider off the main thread.
     * Requires android.permission.READ_CALENDAR.
     */
    suspend fun fetchDefaultDailyAlarmConfig(): List<CalendarAlarmConfig> {
        val events = calendarReader.fetchCalendarEvents()
        return events.map{it.toConfig()}
    }
    fun AlarmingCalendarEvent.toConfig()= CalendarAlarmConfig (
        title=this.title,
        startTime=this.startTime,
        calendarName = this.calendarName,

        id=this.id,
        originalId=this.originalId,
        eventId=this.eventId,
        syncId=this.syncId,
        isRepeating=this.isRepeating,
        status =CalendarAlarmStatus.DEFAULT,
        reminders =this.reminderMinutes.map{ ReminderConfig(it,ReminderStatus.DEFAULT_OFF)}
    )
}

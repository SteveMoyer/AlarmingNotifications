package org.fischman.alarmingnotifications.gcal

import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

public data class AlarmingCalendarEvent(
    val title: String,
    val startTime: Long,
    val calendarName: String
)

public data class AlarmingCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isVisible: Boolean,
    val isSynced: Boolean
)

class AlarmingCalendarReader(private val context: Context) {
    /**
     * Fetches calendar events from the device content provider off the main thread.
     * Requires android.permission.READ_CALENDAR.
     */
    suspend fun fetchCalendarEvents(): List<AlarmingCalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<AlarmingCalendarEvent>()

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis

        // Querying events that start during the current day
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val selectionArgs = arrayOf(startOfDay.toString(), endOfDay.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val titleColumn = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val startColumn = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val calendarColumn = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val title = if (titleColumn != -1) cursor.getString(titleColumn) ?: "Untitled" else "Untitled"
                val startTime = if (startColumn != -1) cursor.getLong(startColumn) else 0L
                val calendarName = if (calendarColumn != -1) cursor.getString(calendarColumn) ?: "Unknown" else "Unknown"

                events.add(AlarmingCalendarEvent(title, startTime, calendarName))
            }
        }

        events
    }

    suspend fun fetchCalendars(): List<AlarmingCalendar> = withContext(Dispatchers.IO) {
        val calendars = mutableListOf<AlarmingCalendar>()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS
        )

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val nameColumn = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountColumn = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val visibleColumn = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
            val syncColumn = cursor.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)

            while (cursor.moveToNext()) {
                val id = if (idColumn != -1) cursor.getLong(idColumn) else -1L
                val name = if (nameColumn != -1) cursor.getString(nameColumn) ?: "Unnamed" else "Unnamed"
                val account = if (accountColumn != -1) cursor.getString(accountColumn) ?: "Unknown" else "Unknown"
                val visible = if (visibleColumn != -1) cursor.getInt(visibleColumn) == 1 else false
                val synced = if (syncColumn != -1) cursor.getInt(syncColumn) == 1 else false

                calendars.add(AlarmingCalendar(id, name, account, visible, synced))
            }
        }
        calendars
    }
}

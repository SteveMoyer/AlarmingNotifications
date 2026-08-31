package org.fischman.alarmingnotifications.gcal

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

public data class AlarmingCalendarEvent(
    val title: String,
    val startTime: Long,
    val calendarName: String,
    val id:String,
    val originalId:String,
    val eventId:String,
    val syncId:String,
    val isRepeating: Boolean
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
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.RRULE,

            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances._ID,
            CalendarContract.Instances.ORIGINAL_ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events._SYNC_ID
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 2)
        val endOfDay = calendar.timeInMillis

        // Querying events that start during the current day
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        var uriBuilder= CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder,startOfDay)
        ContentUris.appendId(uriBuilder,endOfDay)
        context.contentResolver.query(
            uriBuilder.build(),
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val titleColumn = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val startColumn = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val calendarColumn = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
            val idColumn = cursor.getColumnIndex(CalendarContract.Instances._ID)
            val originalIdColumn = cursor.getColumnIndex(CalendarContract.Instances.ORIGINAL_ID)
            val eventIdColumn = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val syncIdColumn = cursor.getColumnIndex(CalendarContract.Events._SYNC_ID)
            val rDateColumn = cursor.getColumnIndex(CalendarContract.Events.RDATE)
            val rRuleColumn = cursor.getColumnIndex(CalendarContract.Events.RRULE)


            while (cursor.moveToNext()) {
                val title = if (titleColumn != -1) cursor.getString(titleColumn) ?: "Untitled" else "Untitled"
                val startTime = if (startColumn != -1) cursor.getLong(startColumn) else 0L
                val calendarName = if (calendarColumn != -1) cursor.getString(calendarColumn) ?: "Unknown" else "Unknown"
                val id = if (idColumn != -1) cursor.getString(idColumn) ?: "Unknown" else "Unknown"
                val originalId = if (originalIdColumn != -1) cursor.getString(originalIdColumn) ?: "" else ""
                val eventId = if (eventIdColumn != -1) cursor.getString(eventIdColumn) ?: "" else ""
                val syncId = if (syncIdColumn != -1) cursor.getString(syncIdColumn) ?: "" else ""
                val rRule = if (rRuleColumn != -1) cursor.getString(rRuleColumn) else null
                val rDate = if (rDateColumn != -1) cursor.getString(rDateColumn) else null
                val isRepeating = !rRule.isNullOrEmpty() ||
                        !rDate.isNullOrEmpty() ||
                        originalId.isNotEmpty()

                events.add(AlarmingCalendarEvent(title, startTime, calendarName, id, originalId, eventId, syncId, isRepeating))
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

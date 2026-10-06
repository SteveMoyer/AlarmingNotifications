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
    val calendarId: Long,
    val id:String,
    val originalId:String,
    val eventId:String,
    val syncId:String,
    val isRepeating: Boolean,
    val reminderMinutes: List<Int> = emptyList()
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

        val instancesProjection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.RRULE,

            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_ID,
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
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis

        // Querying events that start during the current day
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        var uriBuilder= CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder,startOfDay)
        ContentUris.appendId(uriBuilder,endOfDay)
        context.contentResolver.query(
            uriBuilder.build(),
            instancesProjection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val titleColumn = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val startColumn = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val allDayColumn = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val calendarColumn = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
            val calendarIdColumn = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
            val idColumn = cursor.getColumnIndex(CalendarContract.Instances._ID)
            val originalIdColumn = cursor.getColumnIndex(CalendarContract.Instances.ORIGINAL_ID)
            val eventIdColumn = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val syncIdColumn = cursor.getColumnIndex(CalendarContract.Events._SYNC_ID)
            val rDateColumn = cursor.getColumnIndex(CalendarContract.Events.RDATE)
            val rRuleColumn = cursor.getColumnIndex(CalendarContract.Events.RRULE)


            // Collect event IDs for later reminder query
            val eventIds = mutableListOf<String>()
            val eventMap = mutableMapOf<String, AlarmingCalendarEvent>()

            while (cursor.moveToNext()) {
                // Skip all-day events; they have no meaningful alarm time.
                if (allDayColumn != -1 && cursor.getInt(allDayColumn) == 1) continue

                val title = if (titleColumn != -1) cursor.getString(titleColumn) ?: "Untitled" else "Untitled"
                val startTime = if (startColumn != -1) cursor.getLong(startColumn) else 0L
                val calendarName = if (calendarColumn != -1) cursor.getString(calendarColumn) ?: "Unknown" else "Unknown"
                val calendarId = if (calendarIdColumn != -1) cursor.getLong(calendarIdColumn) else 0L
                val id = if (idColumn != -1) cursor.getString(idColumn) ?: "Unknown" else "Unknown"
                val originalId = if (originalIdColumn != -1) cursor.getString(originalIdColumn) ?: "" else ""
                val eventId = if (eventIdColumn != -1) cursor.getString(eventIdColumn) ?: "" else ""
                val syncId = if (syncIdColumn != -1) cursor.getString(syncIdColumn) ?: "" else ""
                val rRule = if (rRuleColumn != -1) cursor.getString(rRuleColumn) else null
                val rDate = if (rDateColumn != -1) cursor.getString(rDateColumn) else null
                val isRepeating = !rRule.isNullOrEmpty() ||
                        !rDate.isNullOrEmpty() ||
                        originalId.isNotEmpty()

                eventIds.add(eventId)
                val event = AlarmingCalendarEvent(title, startTime, calendarName, calendarId, id, originalId, eventId, syncId, isRepeating)
                eventMap[eventId] = event
                events.add(event)
            }
            
                // If we have event IDs, query for reminders
                if (eventIds.isNotEmpty()) {
                    val remindersProjection = arrayOf(
                        CalendarContract.Reminders.EVENT_ID,
                        CalendarContract.Reminders.MINUTES
                    )
                    
                    // Filter out empty event IDs to avoid empty IN clause
                    val validEventIds = eventIds.filter { it.isNotBlank() }
                    if (validEventIds.isNotEmpty()) {
                        val selection = "${CalendarContract.Reminders.EVENT_ID} IN ${validEventIds.joinToString(",", prefix = "(", postfix = ")")}"
                        
                        context.contentResolver.query(
                            CalendarContract.Reminders.CONTENT_URI,
                            remindersProjection,
                            selection,
                            null,
                            null
                        )?.use { reminderCursor ->
                            val eventIdColumn = reminderCursor.getColumnIndex(CalendarContract.Reminders.EVENT_ID)
                            val minutesColumn = reminderCursor.getColumnIndex(CalendarContract.Reminders.MINUTES)
                            
                            // Build a map of event ID to reminder minutes
                            val reminderMap = mutableMapOf<String, MutableList<Int>>()
                            while (reminderCursor.moveToNext()) {
                                val eventId = if (eventIdColumn != -1) reminderCursor.getString(eventIdColumn) ?: "" else ""
                                val minutes = if (minutesColumn != -1) reminderCursor.getInt(minutesColumn) else 0
                                
                                reminderMap.getOrPut(eventId) { mutableListOf() }.add(minutes)
                            }
                            
                            // Update events with reminder information
                            val updatedEvents = mutableListOf<AlarmingCalendarEvent>()
                            events.forEach { event ->
                                val reminderMinutes = reminderMap[event.eventId] ?: emptyList()
                                val updatedEvent = event.copy(reminderMinutes = reminderMinutes)
                                updatedEvents.add(updatedEvent)
                            }
                            events.clear()
                            events.addAll(updatedEvents)
                        }
                    }
                }
        }

        events.toList()
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

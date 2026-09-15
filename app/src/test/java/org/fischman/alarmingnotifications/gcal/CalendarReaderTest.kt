package org.fischman.alarmingnotifications.gcal

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertContentEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CalendarReaderTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var reader: AlarmingCalendarReader

    @Before
    fun setUp() {
        mockContext = mockk()
        mockContentResolver = mockk()
        every { mockContext.contentResolver } returns mockContentResolver
        reader = AlarmingCalendarReader(mockContext)
    }

    @Test
    fun testFetchCalendarEventsParsesCursorCorrectly() = runTest {
        val cursor = MatrixCursor(arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances._ID,
            CalendarContract.Instances.ORIGINAL_ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events._SYNC_ID
        ))

        cursor.addRow(arrayOf(
            "Weekly Sync",
            1725120000000L,
            null,
            "FREQ=WEEKLY",
            "Work Calendar",
            "1001",
            "",
            "2001",
            "sync_abc"
        ))

        cursor.addRow(arrayOf(
            "One-time 1:1",
            1725123600000L,
            null,
            null,
            "Personal Calendar",
            "1002",
            null,
            "2002",
            "sync_def"
        ))

        val reminderCursor = MatrixCursor(arrayOf(
            CalendarContract.Reminders.EVENT_ID,
            CalendarContract.Reminders.MINUTES,
        ))

        reminderCursor.addRow(arrayOf(2001L,  1))
        reminderCursor.addRow(arrayOf(2001L,  10))
        every {
            mockContentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                any<Array<String>>(),
                any(),
                null,
                null
            )
        } returns reminderCursor
        every {
            mockContentResolver.query(
                any(),
                any<Array<String>>(),
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
        } answers {
            cursor
        }

        val events = reader.fetchCalendarEvents()

        assertEquals(2, events.size)

        val event1 = events[0]
        assertEquals("Weekly Sync", event1.title)
        assertEquals(1725120000000L, event1.startTime)
        assertEquals("Work Calendar", event1.calendarName)
        assertEquals("1001", event1.id)
        assertEquals("2001", event1.eventId)
        assertEquals("sync_abc", event1.syncId)
        assertTrue("Event with RRULE should be repeating", event1.isRepeating)
        assertContentEquals( event1.reminderMinutes,listOf(1,10))

        val event2 = events[1]
        assertEquals("One-time 1:1", event2.title)
        assertEquals(1725123600000L, event2.startTime)
        assertEquals("Personal Calendar", event2.calendarName)
        assertEquals("1002", event2.id)
        assertEquals("2002", event2.eventId)
        assertEquals("sync_def", event2.syncId)
        assertFalse("Event with no RRULE, RDATE, or originalId should not be repeating", event2.isRepeating)
        assertTrue("Event should have no reminders by default", event2.reminderMinutes.isEmpty())
    }

    @Test
    fun testFetchCalendarEventsWithReminders() = runTest {
        // First cursor for instances
        val instancesCursor = MatrixCursor(arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances._ID,
            CalendarContract.Instances.ORIGINAL_ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events._SYNC_ID
        ))

        instancesCursor.addRow(arrayOf(
            "Meeting",
            1725120000000L,
            null,
            null,
            "Work Calendar",
            "1001",
            "",
            "2001",
            "sync_abc"
        ))

        instancesCursor.addRow(arrayOf(
            "Lunch",
            1725123600000L,
            null,
            null,
            "Personal Calendar",
            "1002",
            null,
            "2002",
            "sync_def"
        ))

        // Second cursor for reminders
        val remindersCursor = MatrixCursor(arrayOf(
            CalendarContract.Reminders.EVENT_ID,
            CalendarContract.Reminders.MINUTES
        ))

        // Add reminders for the first event (2001) - 15 and 30 minutes before
        remindersCursor.addRow(arrayOf("2001", 15))
        remindersCursor.addRow(arrayOf("2001", 30))
        
        // Add reminder for the second event (2002) - 10 minutes before
        remindersCursor.addRow(arrayOf("2002", 10))

        every {
            mockContentResolver.query(
                any<Uri>(),
                any<Array<String>>(),
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
        } returns instancesCursor

        every {
            mockContentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(
                    CalendarContract.Reminders.EVENT_ID,
                    CalendarContract.Reminders.MINUTES
                ),
                any<String>(),
                null,
                null
            )
        } answers {
            remindersCursor
        }

        val events = reader.fetchCalendarEvents()

        assertEquals(2, events.size)

        // Check first event has reminders
        val event1 = events[0]
        assertEquals("Meeting", event1.title)
        assertEquals(1725120000000L, event1.startTime)
        assertEquals("Work Calendar", event1.calendarName)
        assertEquals("2001", event1.eventId)
        assertEquals(listOf(15, 30), event1.reminderMinutes)
        
        // Check second event has reminders
        val event2 = events[1]
        assertEquals("Lunch", event2.title)
        assertEquals(1725123600000L, event2.startTime)
        assertEquals("Personal Calendar", event2.calendarName)
        assertEquals("2002", event2.eventId)
        assertEquals(listOf(10), event2.reminderMinutes)
    }

    @Test
    fun testFetchCalendarEventsHandlesNullAndMissingColumns() = runTest {
        val cursor = MatrixCursor(arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances._ID,
            CalendarContract.Instances.ORIGINAL_ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events._SYNC_ID
        ))

        // Row with nulls for optional fields
        cursor.addRow(arrayOf(
            null,
            0L,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ))

        every {
            mockContentResolver.query(
                any<Uri>(),
                any<Array<String>>(),
                null,
                null,
                any<String>()
            )
        } answers {
            cursor
        }

        val events = reader.fetchCalendarEvents()
        assertEquals(1, events.size)
        assertEquals("Untitled", events[0].title)
        assertEquals(0L, events[0].startTime)
        assertEquals("Unknown", events[0].calendarName)
        assertEquals("Unknown", events[0].id)
        assertEquals("", events[0].originalId)
        assertEquals("", events[0].eventId)
        assertEquals("", events[0].syncId)
        assertFalse(events[0].isRepeating)
        assertTrue("Event should have no reminders by default", events[0].reminderMinutes.isEmpty())
    }

    @Test
    fun testFetchCalendarsParsesCursorCorrectly() = runTest {
        val cursor = MatrixCursor(arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS
        ))

        cursor.addRow(arrayOf(1L, "Main Calendar", "test@example.com", 1, 1))
        cursor.addRow(arrayOf(2L, "Subscribed Cal", "sub@example.com", 0, 1))
        cursor.addRow(arrayOf(3L, "Unsynced Cal", "test@example.com", 1, 0))

        every {
            mockContentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                any<Array<String>>(),
                null,
                null,
                null
            )
        } returns cursor


        val calendars = reader.fetchCalendars()

        assertEquals(3, calendars.size)

        assertEquals(1L, calendars[0].id)
        assertEquals("Main Calendar", calendars[0].displayName)
        assertEquals("test@example.com", calendars[0].accountName)
        assertTrue(calendars[0].isVisible)
        assertTrue(calendars[0].isSynced)

        assertEquals(2L, calendars[1].id)
        assertFalse(calendars[1].isVisible)
        assertTrue(calendars[1].isSynced)

        assertEquals(3L, calendars[2].id)
        assertTrue(calendars[2].isVisible)
        assertFalse(calendars[2].isSynced)
    }
}

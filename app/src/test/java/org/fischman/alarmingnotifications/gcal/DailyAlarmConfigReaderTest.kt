package org.fischman.alarmingnotifications.gcal

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import io.mockk.coEvery
import io.mockk.coVerify
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DailyAlarmConfigReaderTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var repository: RecurringReminderDefaultsRepository
    private lateinit var reader: DailyAlarmConfigReader

    @Before
    fun setUp() {
        mockContext = mockk()
        mockContentResolver = mockk()
        every { mockContext.contentResolver } returns mockContentResolver
        repository = mockk(relaxed = true)
        reader = DailyAlarmConfigReader(mockContext, repository)
    }

    private fun stubCalendar(
        events: List<Array<out Any?>>,
        reminders: List<Array<out Any?>>,
    ) {
        val instancesCursor = MatrixCursor(INSTANCE_COLUMNS)
        events.forEach { instancesCursor.addRow(it) }
        every {
            mockContentResolver.query(any<Uri>(), any<Array<String>>(), null, null, INSTANCE_SORT)
        } returns instancesCursor

        val remindersCursor = MatrixCursor(
            arrayOf(CalendarContract.Reminders.EVENT_ID, CalendarContract.Reminders.MINUTES)
        )
        reminders.forEach { remindersCursor.addRow(it) }
        every {
            mockContentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                any<Array<String>>(),
                any<String>(),
                null,
                null
            )
        } returns remindersCursor
    }

    private fun instanceRow(
        title: String,
        startTime: Long,
        rrule: String?,
        calendarName: String,
        calendarId: Long = 1L,
        id: String,
        originalId: String?,
        eventId: String,
        syncId: String,
    ) = arrayOf(title, startTime, null, rrule, calendarName, calendarId, id, originalId, eventId, syncId)

    @Test
    fun customReminderIsMergedForRepeatingEvent() = runTest {
        stubCalendar(
            events = listOf(
                instanceRow("Weekly Sync", 1_700_000_000_000L, "FREQ=WEEKLY", "Work", 1L, "1001", "", "2001", "sync")
            ),
            reminders = listOf(arrayOf("2001", 10)),
        )
        coEvery { repository.getReminderDefaults("2001") } returns emptyMap()
        coEvery { repository.getCustomReminders("2001") } returns
            listOf(StoredCustomReminder(45, ReminderStatus.RECURRING_ON))

        val events = reader.fetchDefaultDailyAlarmConfig()

        assertEquals(1, events.size)
        assertEquals(1L, events[0].calendarId)
        val reminders = events[0].reminders
        assertEquals(listOf(10, 45), reminders.map { it.minutes })
        assertFalse(reminders[0].isCustom)
        assertEquals(ReminderStatus.DEFAULT_OFF, reminders[0].status)
        assertTrue(reminders[1].isCustom)
        assertEquals(ReminderStatus.RECURRING_ON, reminders[1].status)
    }

    @Test
    fun customReminderIsOnThisTimeForNonRepeatingEvent() = runTest {
        stubCalendar(
            events = listOf(
                instanceRow("One-time", 1_700_000_000_000L, null, "Work", 1L, "1001", null, "2001", "sync")
            ),
            reminders = emptyList(),
        )
        coEvery { repository.getCustomReminders("2001") } returns
            listOf(StoredCustomReminder(45, ReminderStatus.RECURRING_ON))

        val events = reader.fetchDefaultDailyAlarmConfig()

        val reminders = events[0].reminders
        assertEquals(listOf(45), reminders.map { it.minutes })
        assertTrue(reminders[0].isCustom)
        assertEquals(ReminderStatus.ON_THIS_TIME, reminders[0].status)
    }

    @Test
    fun calendarReminderUsesSavedDefault() = runTest {
        stubCalendar(
            events = listOf(
                instanceRow("Weekly Sync", 1_700_000_000_000L, "FREQ=WEEKLY", "Work", 1L, "1001", "", "2001", "sync")
            ),
            reminders = listOf(arrayOf("2001", 10)),
        )
        coEvery { repository.getReminderDefaults("2001") } returns
            mapOf(10 to ReminderStatus.RECURRING_OFF)

        val events = reader.fetchDefaultDailyAlarmConfig()

        val reminder = events[0].reminders.single()
        assertFalse(reminder.isCustom)
        assertEquals(ReminderStatus.RECURRING_OFF, reminder.status)
    }

    @Test
    fun prunesExpiredCustomRemindersOnLoad() = runTest {
        stubCalendar(events = emptyList(), reminders = emptyList())

        reader.fetchDefaultDailyAlarmConfig()

        coVerify { repository.pruneExpiredCustomReminders(any()) }
    }

    private companion object {
        val INSTANCE_COLUMNS = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances._ID,
            CalendarContract.Instances.ORIGINAL_ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events._SYNC_ID,
        )
        val INSTANCE_SORT = "${CalendarContract.Instances.BEGIN} ASC"
    }
}

package org.fischman.alarmingnotifications.gcal

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarFilterTest {

    private fun event(calendarId: Long, id: String) = CalendarAlarmConfig(
        title = "Event $id",
        startTime = 0L,
        calendarName = "Cal $calendarId",
        calendarId = calendarId,
        id = id,
        originalId = "",
        eventId = "event-$id",
        syncId = "sync-$id",
        isRepeating = false,
        status = CalendarAlarmStatus.DEFAULT,
    )

    @Test
    fun excludesEventsFromExcludedCalendars() {
        val events = listOf(event(1L, "a"), event(2L, "b"), event(1L, "c"))

        val filtered = filterEventsByExcludedCalendars(events, excludedCalendarIds = setOf(1L))

        assertEquals(listOf("b"), filtered.map { it.id })
    }

    @Test
    fun keepsAllEventsWhenNothingExcluded() {
        val events = listOf(event(1L, "a"), event(2L, "b"))

        val filtered = filterEventsByExcludedCalendars(events, excludedCalendarIds = emptySet())

        assertEquals(listOf("a", "b"), filtered.map { it.id })
    }
}

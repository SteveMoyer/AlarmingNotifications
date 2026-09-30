package org.fischman.alarmingnotifications.gcal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderConfigBuildingTest {

    @Test
    fun mergesCalendarAndCustomRemindersSortedByMinutes() {
        val reminders = buildReminders(
            calendarMinutes = listOf(30, 10),
            customReminders = listOf(StoredCustomReminder(45, ReminderStatus.RECURRING_ON)),
            savedDefaults = mapOf(10 to ReminderStatus.RECURRING_OFF),
            isRepeating = true,
        )

        assertEquals(listOf(10, 30, 45), reminders.map { it.minutes })
        assertEquals(ReminderStatus.RECURRING_OFF, reminders[0].status)
        assertEquals(ReminderStatus.DEFAULT_OFF, reminders[1].status)
        assertEquals(ReminderStatus.RECURRING_ON, reminders[2].status)
        assertFalse(reminders[0].isCustom)
        assertFalse(reminders[1].isCustom)
        assertTrue(reminders[2].isCustom)
    }

    @Test
    fun customReminderUsesOnThisTimeForNonRepeatingEvents() {
        val reminders = buildReminders(
            calendarMinutes = emptyList(),
            customReminders = listOf(StoredCustomReminder(45, ReminderStatus.RECURRING_ON)),
            savedDefaults = emptyMap(),
            isRepeating = false,
        )

        assertEquals(1, reminders.size)
        assertEquals(ReminderStatus.ON_THIS_TIME, reminders[0].status)
        assertTrue(reminders[0].isCustom)
    }

    @Test
    fun customReminderUsesStoredStatusForRepeatingEvents() {
        val reminders = buildReminders(
            calendarMinutes = emptyList(),
            customReminders = listOf(StoredCustomReminder(45, ReminderStatus.RECURRING_OFF)),
            savedDefaults = emptyMap(),
            isRepeating = true,
        )

        assertEquals(ReminderStatus.RECURRING_OFF, reminders[0].status)
    }

    @Test
    fun customMinuteMatchingCalendarMinuteIsNotCustom() {
        val reminders = buildReminders(
            calendarMinutes = listOf(30),
            customReminders = listOf(StoredCustomReminder(30, ReminderStatus.RECURRING_ON)),
            savedDefaults = emptyMap(),
            isRepeating = true,
        )

        assertEquals(1, reminders.size)
        assertFalse(reminders[0].isCustom)
    }

    @Test
    fun isValidCustomReminderRequiresPositiveNonDuplicate() {
        assertTrue(isValidCustomReminder(10, existingMinutes = listOf(5, 30)))
        assertFalse(isValidCustomReminder(0, existingMinutes = emptyList()))
        assertFalse(isValidCustomReminder(-5, existingMinutes = emptyList()))
        assertFalse(isValidCustomReminder(30, existingMinutes = listOf(5, 30)))
    }

    @Test
    fun startOfTodayMillisReturnsLocalMidnight() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2024-01-02T15:30:00Z").toEpochMilli()

        assertEquals(
            Instant.parse("2024-01-02T00:00:00Z").toEpochMilli(),
            startOfTodayMillis(now, zone)
        )
    }
}

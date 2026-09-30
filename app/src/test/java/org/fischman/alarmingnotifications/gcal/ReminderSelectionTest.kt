package org.fischman.alarmingnotifications.gcal

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSelectionTest {

    @Test
    fun selectableStatusesForRepeatingEvent() {
        assertEquals(
            listOf(
                ReminderStatus.RECURRING_ON,
                ReminderStatus.RECURRING_OFF,
                ReminderStatus.ON_THIS_TIME,
                ReminderStatus.OFF_THIS_TIME,
            ),
            selectableStatuses(isRepeating = true)
        )
    }

    @Test
    fun selectableStatusesForNonRepeatingEvent() {
        assertEquals(
            listOf(
                ReminderStatus.ON_THIS_TIME,
                ReminderStatus.OFF_THIS_TIME,
            ),
            selectableStatuses(isRepeating = false)
        )
    }

    @Test
    fun reminderStatusLabels() {
        assertEquals("On every time", reminderStatusLabel(ReminderStatus.RECURRING_ON))
        assertEquals("Off every time", reminderStatusLabel(ReminderStatus.RECURRING_OFF))
        assertEquals("On this time only", reminderStatusLabel(ReminderStatus.ON_THIS_TIME))
        assertEquals("Off this time only", reminderStatusLabel(ReminderStatus.OFF_THIS_TIME))
        assertEquals("Use calendar default", reminderStatusLabel(ReminderStatus.DEFAULT_OFF))
    }

    @Test
    fun selectingRecurringOnUpdatesStatusAndDefault() {
        val reminder = ReminderConfig(
            minutes = 10,
            status = ReminderStatus.DEFAULT_OFF,
            defaultStatus = ReminderStatus.DEFAULT_OFF,
        )

        val updated = applyReminderSelection(reminder, ReminderStatus.RECURRING_ON)

        assertEquals(ReminderStatus.RECURRING_ON, updated.status)
        assertEquals(ReminderStatus.RECURRING_ON, updated.defaultStatus)
    }

    @Test
    fun selectingRecurringOffUpdatesStatusAndDefault() {
        val reminder = ReminderConfig(
            minutes = 10,
            status = ReminderStatus.RECURRING_ON,
            defaultStatus = ReminderStatus.RECURRING_ON,
        )

        val updated = applyReminderSelection(reminder, ReminderStatus.RECURRING_OFF)

        assertEquals(ReminderStatus.RECURRING_OFF, updated.status)
        assertEquals(ReminderStatus.RECURRING_OFF, updated.defaultStatus)
    }

    @Test
    fun selectingOnThisTimePreservesRecurringDefault() {
        val reminder = ReminderConfig(
            minutes = 10,
            status = ReminderStatus.RECURRING_OFF,
            defaultStatus = ReminderStatus.RECURRING_OFF,
        )

        val updated = applyReminderSelection(reminder, ReminderStatus.ON_THIS_TIME)

        assertEquals(ReminderStatus.ON_THIS_TIME, updated.status)
        assertEquals(ReminderStatus.RECURRING_OFF, updated.defaultStatus)
    }

    @Test
    fun selectingOffThisTimePreservesRecurringDefault() {
        val reminder = ReminderConfig(
            minutes = 10,
            status = ReminderStatus.RECURRING_ON,
            defaultStatus = ReminderStatus.RECURRING_ON,
        )

        val updated = applyReminderSelection(reminder, ReminderStatus.OFF_THIS_TIME)

        assertEquals(ReminderStatus.OFF_THIS_TIME, updated.status)
        assertEquals(ReminderStatus.RECURRING_ON, updated.defaultStatus)
    }
}

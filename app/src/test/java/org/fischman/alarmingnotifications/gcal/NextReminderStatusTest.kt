package org.fischman.alarmingnotifications.gcal

import org.junit.Assert.assertEquals
import org.junit.Test

class NextReminderStatusTest {

    @Test
    fun defaultRecurringOnCycle() {
        assertEquals(
            ReminderStatus.OFF_THIS_TIME,
            nextReminderStatus(ReminderStatus.RECURRING_ON, ReminderStatus.RECURRING_ON, isRepeating = true)
        )
        assertEquals(
            ReminderStatus.RECURRING_OFF,
            nextReminderStatus(ReminderStatus.OFF_THIS_TIME, ReminderStatus.RECURRING_ON, isRepeating = true)
        )
        assertEquals(
            ReminderStatus.RECURRING_ON,
            nextReminderStatus(ReminderStatus.RECURRING_OFF, ReminderStatus.RECURRING_ON, isRepeating = true)
        )
    }

    @Test
    fun defaultRecurringOffCycle() {
        assertEquals(
            ReminderStatus.ON_THIS_TIME,
            nextReminderStatus(ReminderStatus.RECURRING_OFF, ReminderStatus.RECURRING_OFF, isRepeating = true)
        )
        assertEquals(
            ReminderStatus.RECURRING_ON,
            nextReminderStatus(ReminderStatus.ON_THIS_TIME, ReminderStatus.RECURRING_OFF, isRepeating = true)
        )
        assertEquals(
            ReminderStatus.OFF_THIS_TIME,
            nextReminderStatus(ReminderStatus.RECURRING_ON, ReminderStatus.RECURRING_OFF, isRepeating = true)
        )
        assertEquals(
            ReminderStatus.RECURRING_OFF,
            nextReminderStatus(ReminderStatus.OFF_THIS_TIME, ReminderStatus.RECURRING_OFF, isRepeating = true)
        )
    }

    @Test
    fun defaultOffBehavesLikeRecurringOffCycle() {
        assertEquals(
            ReminderStatus.ON_THIS_TIME,
            nextReminderStatus(ReminderStatus.DEFAULT_OFF, ReminderStatus.DEFAULT_OFF, isRepeating = true)
        )
        assertEquals(
            ReminderStatus.RECURRING_ON,
            nextReminderStatus(ReminderStatus.ON_THIS_TIME, ReminderStatus.DEFAULT_OFF, isRepeating = true)
        )
    }

    @Test
    fun nonRepeatingEventTogglesThisTimeOnly() {
        assertEquals(
            ReminderStatus.ON_THIS_TIME,
            nextReminderStatus(ReminderStatus.DEFAULT_OFF, ReminderStatus.DEFAULT_OFF, isRepeating = false)
        )
        assertEquals(
            ReminderStatus.OFF_THIS_TIME,
            nextReminderStatus(ReminderStatus.ON_THIS_TIME, ReminderStatus.DEFAULT_OFF, isRepeating = false)
        )
        assertEquals(
            ReminderStatus.ON_THIS_TIME,
            nextReminderStatus(ReminderStatus.OFF_THIS_TIME, ReminderStatus.DEFAULT_OFF, isRepeating = false)
        )
        assertEquals(
            ReminderStatus.OFF_THIS_TIME,
            nextReminderStatus(ReminderStatus.RECURRING_ON, ReminderStatus.RECURRING_ON, isRepeating = false)
        )
    }
}

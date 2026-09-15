package org.fischman.alarmingnotifications.gcal

import org.junit.Assert.assertEquals
import org.junit.Test

class NextReminderStatusTest {

    @Test
    fun turningOnForRepeatingEventUsesRecurringOn() {
        assertEquals(ReminderStatus.RECURRING_ON, nextReminderStatus(ReminderStatus.DEFAULT_OFF, isRepeating = true))
        assertEquals(ReminderStatus.RECURRING_ON, nextReminderStatus(ReminderStatus.RECURRING_OFF, isRepeating = true))
    }

    @Test
    fun turningOffForRepeatingEventUsesRecurringOff() {
        assertEquals(ReminderStatus.RECURRING_OFF, nextReminderStatus(ReminderStatus.RECURRING_ON, isRepeating = true))
        assertEquals(ReminderStatus.RECURRING_OFF, nextReminderStatus(ReminderStatus.ON_THIS_TIME, isRepeating = true))
    }

    @Test
    fun turningOnForOneTimeEventUsesOnThisTime() {
        assertEquals(ReminderStatus.ON_THIS_TIME, nextReminderStatus(ReminderStatus.DEFAULT_OFF, isRepeating = false))
        assertEquals(ReminderStatus.ON_THIS_TIME, nextReminderStatus(ReminderStatus.OFF_THIS_TIME, isRepeating = false))
    }

    @Test
    fun turningOffForOneTimeEventUsesOffThisTime() {
        assertEquals(ReminderStatus.OFF_THIS_TIME, nextReminderStatus(ReminderStatus.ON_THIS_TIME, isRepeating = false))
        assertEquals(ReminderStatus.OFF_THIS_TIME, nextReminderStatus(ReminderStatus.RECURRING_ON, isRepeating = false))
    }
}

package org.fischman.alarmingnotifications.gcal

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduledAlarmUiTest {

    @Test
    fun scheduledReminderKeysMapsAndDedupes() {
        val alarms = listOf(
            ScheduledAlarm("event1", 10, "A", 100L),
            ScheduledAlarm("event1", 10, "A", 100L),
            ScheduledAlarm("event2", 30, "B", 200L),
        )

        assertEquals(setOf("event1" to 10, "event2" to 30), scheduledReminderKeys(alarms))
    }

    @Test
    fun scheduledReminderKeysEmptyWhenNoAlarms() {
        assertEquals(emptySet<Pair<String, Int>>(), scheduledReminderKeys(emptyList()))
    }

    @Test
    fun alarmsByTriggerTimeSortsAscending() {
        val alarms = listOf(
            ScheduledAlarm("event2", 5, "B", 300L),
            ScheduledAlarm("event1", 10, "A", 100L),
            ScheduledAlarm("event3", 15, "C", 200L),
        )

        assertEquals(listOf(100L, 200L, 300L), alarmsByTriggerTime(alarms).map { it.triggerAt })
    }
}

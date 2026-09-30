package org.fischman.alarmingnotifications.gcal

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CalendarAlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var shadowAlarmManager: ShadowAlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
    }

    private fun event(
        startTime: Long,
        reminders: List<ReminderConfig>,
        id: String = "event1",
    ) = CalendarAlarmConfig(
        title = "Team Sync",
        startTime = startTime,
        calendarName = "Work",
        id = id,
        originalId = "",
        eventId = "2001",
        syncId = "sync",
        isRepeating = false,
        status = CalendarAlarmStatus.DEFAULT,
        reminders = reminders,
    )

    @Test
    fun schedulesOnReminderAtStartTimeMinusMinutes() {
        val startTime = System.currentTimeMillis() + 3_600_000
        val scheduler = CalendarAlarmScheduler(context)

        scheduler.scheduleAlarms(
            listOf(event(startTime, listOf(ReminderConfig(minutes = 10, status = ReminderStatus.ON_THIS_TIME))))
        )

        val scheduled = shadowAlarmManager.scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(startTime - 10 * 60_000L, scheduled[0].triggerAtTime)
    }

    @Test
    fun skipsRemindersThatAreNotOn() {
        val startTime = System.currentTimeMillis() + 3_600_000
        val scheduler = CalendarAlarmScheduler(context)

        scheduler.scheduleAlarms(
            listOf(
                event(
                    startTime,
                    listOf(
                        ReminderConfig(minutes = 10, status = ReminderStatus.OFF_THIS_TIME),
                        ReminderConfig(minutes = 20, status = ReminderStatus.DEFAULT_OFF),
                    )
                )
            )
        )

        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun skipsRemindersWhoseTriggerTimeIsInThePast() {
        val startTime = System.currentTimeMillis() + 5 * 60_000
        val scheduler = CalendarAlarmScheduler(context)

        scheduler.scheduleAlarms(
            listOf(
                event(
                    startTime,
                    listOf(
                        ReminderConfig(minutes = 10, status = ReminderStatus.RECURRING_ON),
                        ReminderConfig(minutes = 2, status = ReminderStatus.RECURRING_ON),
                    )
                )
            )
        )

        val scheduled = shadowAlarmManager.scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(startTime - 2 * 60_000L, scheduled[0].triggerAtTime)
    }

    @Test
    fun schedulesEachOnReminderIndependently() {
        val startTime = System.currentTimeMillis() + 3_600_000
        val scheduler = CalendarAlarmScheduler(context)

        scheduler.scheduleAlarms(
            listOf(
                event(
                    startTime,
                    listOf(
                        ReminderConfig(minutes = 10, status = ReminderStatus.ON_THIS_TIME),
                        ReminderConfig(minutes = 30, status = ReminderStatus.RECURRING_ON),
                    ),
                    id = "event1"
                ),
                event(
                    startTime,
                    listOf(ReminderConfig(minutes = 5, status = ReminderStatus.ON_THIS_TIME)),
                    id = "event2"
                ),
            )
        )

        assertEquals(3, shadowAlarmManager.scheduledAlarms.size)
    }
}

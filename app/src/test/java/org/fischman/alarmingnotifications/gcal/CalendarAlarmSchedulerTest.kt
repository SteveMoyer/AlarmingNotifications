package org.fischman.alarmingnotifications.gcal

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
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
    private lateinit var repository: FakeScheduledAlarmRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        repository = FakeScheduledAlarmRepository()
    }

    private fun scheduler(now: Long = System.currentTimeMillis()) =
        CalendarAlarmScheduler(context, repository) { now }

    private fun event(
        startTime: Long,
        reminders: List<ReminderConfig>,
        id: String = "event1",
        isRepeating: Boolean = false,
    ) = CalendarAlarmConfig(
        title = "Team Sync",
        startTime = startTime,
        calendarName = "Work",
        calendarId = 1L,
        id = id,
        originalId = "",
        eventId = "2001",
        syncId = "sync",
        isRepeating = isRepeating,
        status = CalendarAlarmStatus.DEFAULT,
        reminders = reminders,
    )

    @Test
    fun schedulesOnReminderAtStartTimeMinusMinutes() = runTest {
        val startTime = System.currentTimeMillis() + 3_600_000

        scheduler().scheduleAlarms(
            listOf(event(startTime, listOf(ReminderConfig(minutes = 10, status = ReminderStatus.ON_THIS_TIME))))
        )

        val scheduled = shadowAlarmManager.scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(startTime - 10 * 60_000L, scheduled[0].triggerAtTime)
    }

    @Test
    fun skipsRemindersThatAreNotOn() = runTest {
        val startTime = System.currentTimeMillis() + 3_600_000

        scheduler().scheduleAlarms(
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
        assertTrue(repository.alarms.isEmpty())
    }

    @Test
    fun skipsRemindersWhoseTriggerTimeIsInThePast() = runTest {
        val startTime = System.currentTimeMillis() + 5 * 60_000

        scheduler().scheduleAlarms(
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
    fun schedulesEachOnReminderIndependently() = runTest {
        val startTime = System.currentTimeMillis() + 3_600_000

        scheduler().scheduleAlarms(
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

    @Test
    fun persistsScheduledAlarms() = runTest {
        val startTime = System.currentTimeMillis() + 3_600_000

        scheduler().scheduleAlarms(
            listOf(event(startTime, listOf(ReminderConfig(minutes = 10, status = ReminderStatus.ON_THIS_TIME))))
        )

        assertEquals(
            listOf(ScheduledAlarm("event1", 10, "Team Sync", startTime - 10 * 60_000L)),
            repository.alarms
        )
    }

    @Test
    fun reschedulePersistedRegistersFutureAlarms() = runTest {
        val startTime = System.currentTimeMillis() + 3_600_000
        repository.alarms = listOf(
            ScheduledAlarm("event1", 10, "Team Sync", startTime - 10 * 60_000L)
        )

        scheduler().reschedulePersisted()

        val scheduled = shadowAlarmManager.scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(startTime - 10 * 60_000L, scheduled[0].triggerAtTime)
        assertEquals(1, repository.alarms.size)
    }

    @Test
    fun reschedulePersistedPrunesPastAlarms() = runTest {
        val now = System.currentTimeMillis()
        repository.alarms = listOf(
            ScheduledAlarm("past", 10, "Old", now - 60_000L),
            ScheduledAlarm("future", 10, "Soon", now + 3_600_000L),
        )

        scheduler(now).reschedulePersisted()

        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        assertEquals(listOf("future"), repository.alarms.map { it.eventId })
    }

    @Test
    fun schedulesCustomReminder() = runTest {
        val startTime = System.currentTimeMillis() + 3_600_000

        scheduler().scheduleAlarms(
            listOf(
                event(
                    startTime,
                    listOf(
                        ReminderConfig(minutes = 45, status = ReminderStatus.RECURRING_ON, isCustom = true)
                    )
                )
            )
        )

        val scheduled = shadowAlarmManager.scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(startTime - 45 * 60_000L, scheduled[0].triggerAtTime)
        assertEquals(
            listOf(ScheduledAlarm("event1", 45, "Team Sync", startTime - 45 * 60_000L)),
            repository.alarms
        )
    }

    @Test
    fun skipsCustomReminderWhoseTriggerTimeIsInThePast() = runTest {
        val startTime = System.currentTimeMillis() + 5 * 60_000

        scheduler().scheduleAlarms(
            listOf(
                event(
                    startTime,
                    listOf(
                        ReminderConfig(minutes = 10, status = ReminderStatus.RECURRING_ON, isCustom = true)
                    )
                )
            )
        )

        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
        assertTrue(repository.alarms.isEmpty())
    }

    @Test
    fun schedulesCustomReminderOnRepeatingEvent() = runTest {
        val startTime = System.currentTimeMillis() + 3_600_000

        scheduler().scheduleAlarms(
            listOf(
                event(
                    startTime,
                    listOf(
                        ReminderConfig(minutes = 45, status = ReminderStatus.RECURRING_ON, isCustom = true)
                    ),
                    isRepeating = true,
                )
            )
        )

        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        assertEquals(
            listOf(ScheduledAlarm("event1", 45, "Team Sync", startTime - 45 * 60_000L)),
            repository.alarms
        )
    }
}

private class FakeScheduledAlarmRepository : ScheduledAlarmRepository {
    var alarms: List<ScheduledAlarm> = emptyList()

    override suspend fun getAll(): List<ScheduledAlarm> = alarms

    override fun observeAll() = kotlinx.coroutines.flow.flowOf(alarms)

    override suspend fun replaceAll(alarms: List<ScheduledAlarm>) {
        this.alarms = alarms
    }
}

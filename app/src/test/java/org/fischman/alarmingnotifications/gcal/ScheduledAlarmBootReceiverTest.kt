package org.fischman.alarmingnotifications.gcal

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.fischman.alarmingnotifications.AlarmSource
import org.fischman.alarmingnotifications.setAlarmSource
import org.fischman.alarmingnotifications.gcal.datastore.DataStoreScheduledAlarmRepository
import org.fischman.alarmingnotifications.gcal.datastore.scheduledAlarmsDataStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ScheduledAlarmBootReceiverTest {

    private lateinit var context: Context
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var repository: DataStoreScheduledAlarmRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        repository = DataStoreScheduledAlarmRepository(context.scheduledAlarmsDataStore)
        repository.replaceAll(emptyList())
        setAlarmSource(context, AlarmSource.SCHEDULED)
    }

    @After
    fun tearDown() = runTest {
        repository.replaceAll(emptyList())
    }

    @Test
    fun handlesBootAndPackageReplacedOnly() {
        assertTrue(isRestoreAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(isRestoreAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(isRestoreAction(Intent.ACTION_TIME_CHANGED))
        assertFalse(isRestoreAction(null))
    }

    @Test
    fun restoreScheduledAlarmsRegistersPersistedFutureAlarms() = runTest {
        val future = System.currentTimeMillis() + 3_600_000
        repository.replaceAll(listOf(ScheduledAlarm("event1", 10, "Team Sync", future)))

        restoreScheduledAlarms(context)

        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun restoreScheduledAlarmsNoOpWhenNotificationsSourceActive() = runTest {
        setAlarmSource(context, AlarmSource.NOTIFICATIONS)
        repository.replaceAll(
            listOf(ScheduledAlarm("event1", 10, "Team Sync", System.currentTimeMillis() + 3_600_000))
        )

        restoreScheduledAlarms(context)

        assertTrue(shadowAlarmManager.scheduledAlarms.isEmpty())
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun restoreScheduledAlarmsDropsPastAlarms() = runTest {
        val now = System.currentTimeMillis()
        repository.replaceAll(
            listOf(
                ScheduledAlarm("past", 10, "Old", now - 60_000L),
                ScheduledAlarm("future", 10, "Soon", now + 3_600_000L),
            )
        )

        restoreScheduledAlarms(context)

        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        assertEquals(listOf("future"), repository.getAll().map { it.eventId })
    }
}

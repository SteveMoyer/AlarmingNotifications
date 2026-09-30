package org.fischman.alarmingnotifications.gcal.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.fischman.alarmingnotifications.gcal.ScheduledAlarm
import org.fischman.alarmingnotifications.gcal.ScheduledAlarmCollection
import org.fischman.alarmingnotifications.gcal.ScheduledAlarmRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DataStoreScheduledAlarmRepositoryTest {

    private lateinit var testFile: File
    private lateinit var repository: ScheduledAlarmRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        testFile = File(context.filesDir, "test_scheduled_alarms_${System.nanoTime()}.pb")
        val dataStore: DataStore<ScheduledAlarmCollection> = DataStoreFactory.create(
            serializer = ScheduledAlarmSerializer,
            produceFile = { testFile }
        )
        repository = DataStoreScheduledAlarmRepository(dataStore)
    }

    @After
    fun tearDown() {
        testFile.delete()
    }

    @Test
    fun returnsEmptyWhenNothingSaved() = runTest {
        assertEquals(emptyList<ScheduledAlarm>(), repository.getAll())
    }

    @Test
    fun replaceAllAndGetAllRoundTrip() = runTest {
        val alarms = listOf(
            ScheduledAlarm("event1", 10, "Team Sync", 1_000L),
            ScheduledAlarm("event2", 5, "Lunch", 2_000L),
        )
        repository.replaceAll(alarms)
        assertEquals(alarms, repository.getAll())
    }

    @Test
    fun replaceAllOverwritesPreviousSet() = runTest {
        repository.replaceAll(listOf(ScheduledAlarm("old", 10, "Old", 1_000L)))
        repository.replaceAll(listOf(ScheduledAlarm("new", 5, "New", 2_000L)))

        assertEquals(listOf(ScheduledAlarm("new", 5, "New", 2_000L)), repository.getAll())
    }

    @Test
    fun replaceAllWithEmptyClearsSet() = runTest {
        repository.replaceAll(listOf(ScheduledAlarm("event1", 10, "Team Sync", 1_000L)))
        repository.replaceAll(emptyList())

        assertEquals(emptyList<ScheduledAlarm>(), repository.getAll())
    }
}

package org.fischman.alarmingnotifications.gcal.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.fischman.alarmingnotifications.gcal.RecurringEventDefaultsCollection
import org.fischman.alarmingnotifications.gcal.RecurringReminderDefaultsRepository
import org.fischman.alarmingnotifications.gcal.ReminderStatus
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
class DataStoreRecurringReminderDefaultsRepositoryTest {

    private lateinit var context: Context
    private lateinit var testFile: File
    private lateinit var dataStore: DataStore<RecurringEventDefaultsCollection>
    private lateinit var repository: RecurringReminderDefaultsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testFile = File(context.filesDir, "test_recurring_defaults_${System.nanoTime()}.pb")
        dataStore = DataStoreFactory.create(
            serializer = RecurringReminderDefaultsSerializer,
            produceFile = { testFile }
        )
        repository = DataStoreRecurringReminderDefaultsRepository(dataStore)
    }

    @After
    fun tearDown() {
        testFile.delete()
    }

    @Test
    fun returnsEmptyDefaultsWhenNothingSaved() = runTest {
        assertEquals(emptyMap<Int, ReminderStatus>(), repository.getReminderDefaults("event1"))
    }

    @Test
    fun saveAndRetrieveRecurringOn() = runTest {
        repository.saveReminderDefault("event1", 10, ReminderStatus.RECURRING_ON)
        assertEquals(
            mapOf(10 to ReminderStatus.RECURRING_ON),
            repository.getReminderDefaults("event1")
        )
    }

    @Test
    fun saveAndRetrieveRecurringOff() = runTest {
        repository.saveReminderDefault("event1", 10, ReminderStatus.RECURRING_OFF)
        assertEquals(
            mapOf(10 to ReminderStatus.RECURRING_OFF),
            repository.getReminderDefaults("event1")
        )
    }

    @Test
    fun updateExistingReminderDefault() = runTest {
        repository.saveReminderDefault("event1", 10, ReminderStatus.RECURRING_ON)
        repository.saveReminderDefault("event1", 10, ReminderStatus.RECURRING_OFF)
        assertEquals(
            mapOf(10 to ReminderStatus.RECURRING_OFF),
            repository.getReminderDefaults("event1")
        )
    }

    @Test
    fun multipleEventsAreIndependent() = runTest {
        repository.saveReminderDefault("event1", 10, ReminderStatus.RECURRING_ON)
        repository.saveReminderDefault("event2", 20, ReminderStatus.RECURRING_OFF)
        assertEquals(
            mapOf(10 to ReminderStatus.RECURRING_ON),
            repository.getReminderDefaults("event1")
        )
        assertEquals(
            mapOf(20 to ReminderStatus.RECURRING_OFF),
            repository.getReminderDefaults("event2")
        )
    }

    @Test
    fun deleteReminderDefault() = runTest {
        repository.saveReminderDefault("event1", 10, ReminderStatus.RECURRING_ON)
        repository.deleteReminderDefault("event1", 10)
        assertEquals(emptyMap<Int, ReminderStatus>(), repository.getReminderDefaults("event1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun savingNonRecurringDefaultThrows() = runTest {
        repository.saveReminderDefault("event1", 10, ReminderStatus.ON_THIS_TIME)
    }
}

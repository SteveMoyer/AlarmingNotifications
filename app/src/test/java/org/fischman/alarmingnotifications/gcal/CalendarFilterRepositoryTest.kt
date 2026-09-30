package org.fischman.alarmingnotifications.gcal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.fischman.alarmingnotifications.getSettingsSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CalendarFilterRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: CalendarFilterRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        getSettingsSharedPreferences(context).edit().clear().commit()
        repository = SharedPreferencesCalendarFilterRepository(context)
    }

    @Test
    fun defaultsToNoExclusions() {
        assertEquals(emptySet<Long>(), repository.getExcludedCalendarIds())
    }

    @Test
    fun roundTripsExcludedIds() {
        repository.setExcludedCalendarIds(setOf(1L, 2L))

        assertEquals(setOf(1L, 2L), repository.getExcludedCalendarIds())
    }

    @Test
    fun clearingExclusionsRemovesAll() {
        repository.setExcludedCalendarIds(setOf(1L, 2L))
        repository.setExcludedCalendarIds(emptySet())

        assertEquals(emptySet<Long>(), repository.getExcludedCalendarIds())
    }
}

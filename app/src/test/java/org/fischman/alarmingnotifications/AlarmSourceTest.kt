package org.fischman.alarmingnotifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AlarmSourceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        getSettingsSharedPreferences(context).edit().clear().apply()
    }

    @Test
    fun defaultsToNotifications() {
        assertEquals(AlarmSource.NOTIFICATIONS, getAlarmSource(context))
    }

    @Test
    fun setAndGetRoundTrips() {
        setAlarmSource(context, AlarmSource.SCHEDULED)
        assertEquals(AlarmSource.SCHEDULED, getAlarmSource(context))

        setAlarmSource(context, AlarmSource.NOTIFICATIONS)
        assertEquals(AlarmSource.NOTIFICATIONS, getAlarmSource(context))
    }

    @Test
    fun unknownStoredValueFallsBackToNotifications() {
        getSettingsSharedPreferences(context).edit().putString(alarmSourceKey, "bogus").apply()

        assertEquals(AlarmSource.NOTIFICATIONS, getAlarmSource(context))
    }
}

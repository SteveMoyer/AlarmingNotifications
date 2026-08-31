package org.fischman.alarmingnotifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MuteLogicTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        unmuteAll(context)
    }

    @Test
    fun testInitialMuteStateIsEmpty() {
        assertEquals("", mutedUntil(context))
        assertEquals(0, muteCountRemaining(context))
        assertFalse(decrementMuteCount(context))
    }

    @Test
    fun testMuteForMinutesSetsFutureDeadline() {
        muteForMinutes(context, 15)
        val until = mutedUntil(context)
        assertTrue("Expected non-empty mutedUntil deadline", until.isNotEmpty())

        val deadline = LocalDateTime.parse(until)
        val now = LocalDateTime.now()
        assertTrue(deadline.isAfter(now))
        assertTrue(deadline.isBefore(now.plusMinutes(16)))
    }

    @Test
    fun testMuteForZeroOrNegativeMinutesClearsTimeMute() {
        muteForMinutes(context, 30)
        assertTrue(mutedUntil(context).isNotEmpty())

        muteForMinutes(context, 0)
        assertEquals("", mutedUntil(context))

        muteForMinutes(context, 30)
        muteForMinutes(context, -5)
        assertEquals("", mutedUntil(context))
    }

    @Test
    fun testMuteForHoursSetsFutureDeadline() {
        muteForHours(context, 2)
        val until = mutedUntil(context)
        assertTrue("Expected non-empty mutedUntil deadline", until.isNotEmpty())

        val deadline = LocalDateTime.parse(until)
        val now = LocalDateTime.now()
        assertTrue(deadline.isAfter(now.plusHours(1)))
        assertTrue(deadline.isBefore(now.plusHours(3)))
    }

    @Test
    fun testMuteForZeroOrNegativeHoursClearsTimeMute() {
        muteForHours(context, 2)
        assertTrue(mutedUntil(context).isNotEmpty())

        muteForHours(context, 0)
        assertEquals("", mutedUntil(context))
    }

    @Test
    fun testMuteForNNotificationsAndDecrement() {
        muteForNNotifications(context, 3)
        assertEquals(3, muteCountRemaining(context))

        // 1st notification: suppressed, count becomes 2
        assertTrue("First notification should be muted", decrementMuteCount(context))
        assertEquals(2, muteCountRemaining(context))

        // 2nd notification: suppressed, count becomes 1
        assertTrue("Second notification should be muted", decrementMuteCount(context))
        assertEquals(1, muteCountRemaining(context))

        // 3rd notification: suppressed (took us to zero), count cleared
        assertTrue("Third notification that reaches zero should be muted", decrementMuteCount(context))
        assertEquals(0, muteCountRemaining(context))

        // 4th notification: not muted (fires alarm)
        assertFalse("Fourth notification should not be muted", decrementMuteCount(context))
        assertEquals(0, muteCountRemaining(context))
    }

    @Test
    fun testMuteForZeroOrNegativeNotificationsClearsCountMute() {
        muteForNNotifications(context, 5)
        assertEquals(5, muteCountRemaining(context))

        muteForNNotifications(context, 0)
        assertEquals(0, muteCountRemaining(context))

        muteForNNotifications(context, 5)
        muteForNNotifications(context, -1)
        assertEquals(0, muteCountRemaining(context))
    }

    @Test
    fun testExpiredTimeDeadlineClearsAutomatically() {
        // Manually set a deadline in the past
        val pastDeadline = LocalDateTime.now().minusMinutes(5).toString()
        getMuteSharedPreferences(context).edit().putString(muteDeadlineKey, pastDeadline).apply()

        assertEquals("", mutedUntil(context))
        // Verify it cleaned up the preference key
        val stored = getMuteSharedPreferences(context).getString(muteDeadlineKey, null)
        assertEquals(null, stored)
    }

    @Test
    fun testUnmuteAllClearsBothMutes() {
        muteForHours(context, 1)
        muteForNNotifications(context, 3)

        assertTrue(mutedUntil(context).isNotEmpty())
        assertEquals(3, muteCountRemaining(context))

        unmuteAll(context)

        assertEquals("", mutedUntil(context))
        assertEquals(0, muteCountRemaining(context))
    }
}

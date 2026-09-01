package org.fischman.alarmingnotifications

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NotificationListenerIntegrationTest {

    private lateinit var context: Context
    private lateinit var service: NotificationListener
    private lateinit var shadowApp: ShadowApplication

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowApp = shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
        unmuteAll(context)
        getSettingsSharedPreferences(context).edit().clear().apply()

        service = Robolectric.buildService(NotificationListener::class.java).get()
    }

    private fun createSbn(
        packageName: String = "com.google.android.calendar",
        title: String? = "Sprint Planning",
        text: String? = "10:00 - 11:00 AM",
        key: String = "cal_notif_100"
    ): StatusBarNotification {
        val extras = Bundle().apply {
            if (title != null) putCharSequence(Notification.EXTRA_TITLE, title)
            if (text != null) putCharSequence(Notification.EXTRA_TEXT, text)
        }

        val notification = Notification().apply {
            this.extras = extras
        }

        val sbn = mockk<StatusBarNotification>()
        every { sbn.packageName } returns packageName
        every { sbn.key } returns key
        every { sbn.notification } returns notification
        return sbn
    }

    @Test
    fun testOnNotificationPostedTriggersAlarmService() {
        val sbn = createSbn(
            packageName = "com.google.android.calendar",
            title = "Quarterly Review",
            text = "Room 404",
            key = "sbn_event_1"
        )

        service.onNotificationPosted(sbn)

        val nextService = shadowApp.nextStartedService
        assertNotNull("TriggerAlarm service should be started", nextService)
        assertEquals(TriggerAlarm::class.java.name, nextService.component?.className)
        assertEquals("show", nextService.getStringExtra("action"))
        assertTrue("Label should contain title and text", nextService.getStringExtra("label")?.contains("Quarterly Review") == true)
        assertEquals("sbn_event_1", nextService.getStringExtra("originalNotificationKey"))
    }

    @Test
    fun testOnNotificationPostedSuppressedWhenTimeMuted() {
        muteForMinutes(context, 30)

        val sbn = createSbn(
            packageName = "com.google.android.calendar",
            title = "Design Sync",
            text = "1:00 PM",
            key = "sbn_event_2"
        )

        service.onNotificationPosted(sbn)

        val nextService = shadowApp.nextStartedService
        assertNull("TriggerAlarm service should NOT be started when time muted", nextService)
    }

    @Test
    fun testOnNotificationPostedCountMuteSequence() {
        muteForNNotifications(context, 2)
        assertEquals(2, muteCountRemaining(context))

        // 1st notification -> suppressed
        val sbn1 = createSbn(key = "sbn_count_1", title = "Meeting 1")
        service.onNotificationPosted(sbn1)
        assertNull("1st notification should be suppressed", shadowApp.nextStartedService)
        assertEquals(1, muteCountRemaining(context))

        // 2nd notification -> suppressed
        val sbn2 = createSbn(key = "sbn_count_2", title = "Meeting 2")
        service.onNotificationPosted(sbn2)
        assertNull("2nd notification should be suppressed", shadowApp.nextStartedService)
        assertEquals(0, muteCountRemaining(context))

        // 3rd notification -> triggers alarm!
        val sbn3 = createSbn(key = "sbn_count_3", title = "Meeting 3")
        service.onNotificationPosted(sbn3)
        val startedService = shadowApp.nextStartedService
        assertNotNull("3rd notification after mute count expires should trigger alarm", startedService)
        assertEquals("show", startedService.getStringExtra("action"))
    }

    @Test
    fun testOnNotificationPostedIgnoredForAllDayEvents() {
        val sbn = createSbn(
            packageName = "com.google.android.calendar",
            title = "Company Holiday",
            text = "Tomorrow",
            key = "sbn_allday_1"
        )

        service.onNotificationPosted(sbn)
        assertNull("All-day event notification with text 'Tomorrow' should be ignored", shadowApp.nextStartedService)
    }

    @Test
    fun testOnNotificationRemovedSendsStopIntent() {
        val sbn = createSbn(
            packageName = "com.google.android.calendar",
            title = "1:1 with Manager",
            text = "3:30 PM",
            key = "sbn_dismiss_1"
        )

        // First post it to track key
        service.onNotificationPosted(sbn)
        shadowApp.clearStartedServices()

        // Then remove it
        service.onNotificationRemoved(sbn)

        val stopService = shadowApp.nextStartedService
        assertNotNull("Stop intent should be dispatched to TriggerAlarm on notification removal", stopService)
        assertEquals("stop", stopService.getStringExtra("action"))
        assertEquals("sbn_dismiss_1", stopService.getStringExtra("originalNotificationKey"))
    }
}

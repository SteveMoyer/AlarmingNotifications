package org.fischman.alarmingnotifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TriggerAlarmServiceTest {

    private lateinit var context: Context
    private lateinit var service: TriggerAlarm
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)

        val defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(
            org.robolectric.shadows.util.DataSource.toDataSource(context, defaultUri),
            org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(1000, 0)
        )

        service = Robolectric.buildService(TriggerAlarm::class.java).create().get()
    }

    @Test
    fun testShowNotificationPostsNotificationWithActions() {
        val notifId = service.showNotification("Team Sync", "calendar_key_1")
        assertTrue("Notification ID should be positive", notifId >= 0)

        val shadowNm = shadowOf(notificationManager)
        val notification = shadowNm.getNotification(notifId)
        assertNotNull("Notification should be posted to NotificationManager", notification)

        assertEquals(Notification.CATEGORY_CALL, notification.category)
        assertTrue("Notification should have FLAG_NO_CLEAR", (notification.flags and Notification.FLAG_NO_CLEAR) != 0)

        // Verify actions: Stop, Snooze 1m, Snooze 5m
        assertNotNull(notification.actions)
        assertEquals(3, notification.actions.size)
        assertEquals("Stop", notification.actions[0].title)
        assertEquals("Snooze 1m", notification.actions[1].title)
        assertEquals("Snooze 5m", notification.actions[2].title)
    }

    @Test
    fun testOnStartCommandShowAction() {
        val intent = Intent(context, TriggerAlarm::class.java).apply {
            putExtra("action", "show")
            putExtra("label", "Urgent Standup")
            putExtra("originalNotificationKey", "gcal_key_42")
        }

        val result = service.onStartCommand(intent, 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, result)

        val shadowNm = shadowOf(notificationManager)
        val allNotifs = shadowNm.allNotifications
        assertTrue("At least one notification should be posted", allNotifs.isNotEmpty())
    }

    @Test
    fun testOnStartCommandStopActionDismissesNotification() {
        val notifId = service.showNotification("Dentist Appointment", "calendar_key_2")
        val shadowNm = shadowOf(notificationManager)
        assertNotNull(shadowNm.getNotification(notifId))

        val stopIntent = Intent(context, TriggerAlarm::class.java).apply {
            putExtra("action", "stop")
            putExtra("label", "Dentist Appointment")
            putExtra("originalNotificationKey", "calendar_key_2")
            putExtra("notificationID", notifId)
        }

        service.onStartCommand(stopIntent, 0, 2)
        assertEquals("Notification should be cancelled after stop action", null, shadowNm.getNotification(notifId))
    }

    @Test
    fun testOnStartCommandSnooze1mSchedulesExactAlarm() {
        val notifId = service.showNotification("Flight Departure", "calendar_key_3")
        val shadowNm = shadowOf(notificationManager)
        assertNotNull(shadowNm.getNotification(notifId))

        val snoozeIntent = Intent(context, TriggerAlarm::class.java).apply {
            putExtra("action", "snooze1m")
            putExtra("label", "Flight Departure")
            putExtra("originalNotificationKey", "calendar_key_3")
            putExtra("notificationID", notifId)
        }

        val beforeTime = System.currentTimeMillis()
        service.onStartCommand(snoozeIntent, 0, 3)

        // Verify current notification is dismissed
        assertEquals(null, shadowNm.getNotification(notifId))

        // Verify alarm scheduled in AlarmManager
        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue("Alarm should be scheduled in AlarmManager", scheduledAlarms.isNotEmpty())

        val nextAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Next scheduled alarm should not be null", nextAlarm)
        // Verify trigger time is roughly 1 minute (60,000ms) in the future
        val expectedTrigger = beforeTime + 60_000
        assertTrue("Alarm trigger time should be ~1m in future", nextAlarm.triggerAtTime >= expectedTrigger - 1000)
    }

    @Test
    fun testMultipleAlarmsDismissIndependently() {
        val notifId1 = service.showNotification("Meeting A", "cal_key_a")
        val notifId2 = service.showNotification("Meeting B", "cal_key_b")

        val shadowNm = shadowOf(notificationManager)
        assertNotNull(shadowNm.getNotification(notifId1))
        assertNotNull(shadowNm.getNotification(notifId2))

        service.dismiss(notifId1)

        assertEquals("First notification should be cancelled", null, shadowNm.getNotification(notifId1))
        assertNotNull("Second notification should still be active", shadowNm.getNotification(notifId2))
    }

    @Test
    fun testOnBindReturnsAlarmBinder() {
        val binder = service.onBind(Intent())
        assertNotNull(binder)
        assertTrue(binder is TriggerAlarm.AlarmBinder)
        assertEquals(service, (binder as TriggerAlarm.AlarmBinder).getService())
    }
}

package org.fischman.alarmingnotifications

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
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
class MainActivityLifecycleTest {

    private lateinit var app: Application
    private lateinit var shadowApp: ShadowApplication
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowApp = shadowOf(app)
        notificationManager = app.getSystemService(NotificationManager::class.java)

        // Clear enabled notification listeners
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", null)
    }

    @Test
    fun testApplicationCreatesNotificationChannels() {
        val anApp = ApplicationProvider.getApplicationContext<ANapp>()
        anApp.onCreate()

        val alarmChannel = notificationManager.getNotificationChannel(notificationChannelID)
        assertNotNull("Alarm notification channel should be created", alarmChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, alarmChannel.importance)
        assertEquals("Alarms", alarmChannel.name)

        val muteChannel = notificationManager.getNotificationChannel(muteStatusChannelID)
        assertNotNull("Mute status notification channel should be created", muteChannel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, muteChannel.importance)
        assertEquals("Mute status", muteChannel.name)
    }

    @Test
    fun testMainActivityLaunchesPermissionsWhenMissing() {
        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowApp.denyPermissions(Manifest.permission.READ_CALENDAR)

        Robolectric.buildActivity(MainActivity::class.java).setup()

        val nextStartedActivity = shadowApp.nextStartedActivity
        assertNotNull("Should start PermissionsActivity when required permissions are missing", nextStartedActivity)
        assertEquals(PermissionsActivity::class.java.name, nextStartedActivity.component?.className)
    }

    @Test
    fun testMainActivityBindsTriggerAlarmWithoutFiringTestAlarm() {
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowApp.grantPermissions(Manifest.permission.READ_CALENDAR)
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = shadowOf(alarmManager)
        shadowAlarm.javaClass.getMethod("setCanScheduleExactAlarms", Boolean::class.javaPrimitiveType).invoke(shadowAlarm, true)
        val componentName = ComponentName(app.packageName, NotificationListener::class.java.name).flattenToString()
        Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", componentName)

        val statuses = getPermissionStatuses(app)
        assertTrue("All permissions must be granted for test, but was: $statuses", statuses.all { it.granted })

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        // Verify bound service connection exists for TriggerAlarm
        val boundServices = shadowApp.boundServiceConnections
        assertTrue("TriggerAlarm service should be bound", boundServices.isNotEmpty())

        // Verify no test alarm was posted to NotificationManager
        val postedNotifications = shadowOf(notificationManager).allNotifications
        assertTrue("No automatic test alarm should be posted on connection", postedNotifications.isEmpty())

        // Verify clean destroy and unbind
        controller.destroy()
    }
}

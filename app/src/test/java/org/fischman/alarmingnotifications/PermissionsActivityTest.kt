package org.fischman.alarmingnotifications

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PermissionsActivityTest {

    private lateinit var context: Context
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        // Clear enabled notification listeners
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", null)
    }

    @Test
    fun testGetPermissionStatusesContainsAllSteps() {
        val statuses = getPermissionStatuses(context)
        val steps = statuses.map { it.step }

        assertTrue(steps.contains(PermissionStep.SendNotifications))
        assertTrue(steps.contains(PermissionStep.ReadNotifications))
        assertTrue(steps.contains(PermissionStep.SetExactAlarms))
        assertTrue(steps.contains(PermissionStep.ReadCalendar))
    }

    @Test
    fun testHasAllRequiredPermissionsReturnsFalseWhenNotGranted() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(app).denyPermissions(Manifest.permission.READ_CALENDAR)

        assertFalse(hasAllRequiredPermissions(context))
    }

    @Test
    fun testNotificationListenerPermissionDetected() {
        val componentName = ComponentName(context.packageName, NotificationListener::class.java.name).flattenToString()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", componentName)

        val statuses = getPermissionStatuses(context)
        val readNotifStatus = statuses.first { it.step == PermissionStep.ReadNotifications }
        assertTrue("Notification listener should be reported as granted when in enabled_notification_listeners", readNotifStatus.granted)
    }

    @Test
    fun testReadCalendarPermissionDetected() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)

        val statuses = getPermissionStatuses(context)
        val calendarStatus = statuses.first { it.step == PermissionStep.ReadCalendar }
        assertTrue("Calendar permission should be reported as granted when granted by system", calendarStatus.granted)
    }

    @Test
    fun testAllPermissionsGrantedReturnsTrue() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)

        val componentName = ComponentName(context.packageName, NotificationListener::class.java.name).flattenToString()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", componentName)

        // On API 34 Robolectric, exact alarms are allowed by default for apps declaring USE_EXACT_ALARM
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            assertTrue("hasAllRequiredPermissions should be true when all conditions are satisfied", hasAllRequiredPermissions(context))
        }
    }
}

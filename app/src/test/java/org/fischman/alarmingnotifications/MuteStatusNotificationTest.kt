package org.fischman.alarmingnotifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MuteStatusNotificationTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowNm: ShadowNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        shadowNm = shadowOf(notificationManager)
        unmuteAll(context)
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        MuteStatusNotification.stopWatching()
        unmuteAll(context)
        notificationManager.cancelAll()
    }

    @Test
    fun testNoNotificationWhenUnmuted() {
        MuteStatusNotification.update(context)
        val notif = shadowNm.getNotification(persistentMuteNotificationId)
        assertNull("No mute notification should be present when unmuted", notif)
    }

    @Test
    fun testPostsNotificationWhenTimeMuted() {
        muteForHours(context, 2)
        MuteStatusNotification.update(context)

        val notif = shadowNm.getNotification(persistentMuteNotificationId)
        assertNotNull("Mute notification should be posted when time muted", notif)

        val extras = notif.extras
        val contentText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertNotNull(contentText)
        assertTrue("Content text should contain 'Until'", contentText!!.contains("Until"))
        assertTrue("Notification should be ongoing", (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test
    fun testPostsNotificationWhenCountMuted() {
        muteForNNotifications(context, 3)
        MuteStatusNotification.update(context)

        val notif = shadowNm.getNotification(persistentMuteNotificationId)
        assertNotNull(notif)

        val contentText = notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertEquals("Muted: 3 notifications", contentText)
    }

    @Test
    fun testPostsCombinedNotificationWhenBothTimeAndCountMuted() {
        muteForMinutes(context, 45)
        muteForNNotifications(context, 1)
        MuteStatusNotification.update(context)

        val notif = shadowNm.getNotification(persistentMuteNotificationId)
        assertNotNull(notif)

        val contentText = notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertNotNull(contentText)
        assertTrue("Content text should combine time and count mutes", contentText!!.contains("Until") && contentText.contains("1 notification"))
    }

    @Test
    fun testStartWatchingAutoUpdatesOnPreferenceChanges() {
        MuteStatusNotification.startWatching(context)

        // Changing preferences directly should trigger listener and update notification
        muteForHours(context, 1)
        var notif = shadowNm.getNotification(persistentMuteNotificationId)
        assertNotNull("Should auto-post notification when preference changes", notif)

        // Clearing preferences should auto-cancel the notification
        unmuteAll(context)
        notif = shadowNm.getNotification(persistentMuteNotificationId)
        assertNull("Should auto-cancel notification when unmuted", notif)
    }
}

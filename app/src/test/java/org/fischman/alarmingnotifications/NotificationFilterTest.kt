package org.fischman.alarmingnotifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NotificationFilterTest {

    private lateinit var context: Context
    private lateinit var service: NotificationListener

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset settings preferences
        getSettingsSharedPreferences(context).edit().clear().apply()

        service = Robolectric.buildService(NotificationListener::class.java).get()
    }

    private fun createMockSbn(
        packageName: String = "com.google.android.calendar",
        title: String? = "Meeting with Alice",
        text: String? = "10:00 - 11:00 AM",
        key: String = "sbn_key_1",
        actions: Array<Notification.Action>? = null
    ): StatusBarNotification {
        val extras = Bundle().apply {
            if (title != null) putCharSequence(Notification.EXTRA_TITLE, title)
            if (text != null) putCharSequence(Notification.EXTRA_TEXT, text)
        }

        val notification = Notification().apply {
            this.extras = extras
            this.actions = actions
        }

        val sbn = mockk<StatusBarNotification>()
        every { sbn.packageName } returns packageName
        every { sbn.key } returns key
        every { sbn.notification } returns notification
        return sbn
    }

    @Test
    fun testExtractTextRetrievesAllAvailableFields() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "Project Review")
            putCharSequence(Notification.EXTRA_TEXT, "Room 3B")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "Detailed agenda for the quarterly review")
            putCharSequence(Notification.EXTRA_SUB_TEXT, "Important")
            putCharSequence(Notification.EXTRA_INFO_TEXT, "Q3")
            putCharSequence(Notification.EXTRA_SUMMARY_TEXT, "Summary details")
        }

        val notification = Notification().apply { this.extras = extras }
        val sbn = mockk<StatusBarNotification>()
        every { sbn.notification } returns notification

        val extracted = service.extractText(sbn).map { it.toString() }

        assertTrue(extracted.contains("Project Review"))
        assertTrue(extracted.contains("Room 3B"))
        assertTrue(extracted.contains("Detailed agenda for the quarterly review"))
        assertTrue(extracted.contains("Important"))
        assertTrue(extracted.contains("Q3"))
        assertTrue(extracted.contains("Summary details"))
    }

    @Test
    fun testDefaultCalendarNotificationIsInteresting() {
        val sbn = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Doctor Appointment",
            text = "2:00 PM"
        )
        assertTrue("Default Google Calendar notification should be interesting", service.isInteresting(sbn))
    }

    @Test
    fun testCalendarNotificationNotInterestingWhenScheduledSourceActive() {
        getSettingsSharedPreferences(service)
            .edit()
            .putString(alarmSourceKey, AlarmSource.SCHEDULED.name)
            .apply()

        val sbn = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Doctor Appointment",
            text = "2:00 PM"
        )
        assertFalse("Calendar notification should be ignored when scheduled source is active", service.isInteresting(sbn))
    }

    @Test
    fun testNonWhitelistedPackageIsNotInteresting() {
        val sbn = createMockSbn(
            packageName = "com.example.unwantedapp",
            title = "Promo 50% off",
            text = "Buy now"
        )
        assertFalse("Non-whitelisted package should not be interesting", service.isInteresting(sbn))
    }

    @Test
    fun testCustomPackageInSettingsIsInteresting() {
        getSettingsSharedPreferences(service)
            .edit()
            .putStringSet(alarmPackagesKey, setOf("com.google.android.calendar", "com.google.android.gm"))
            .apply()

        val sbn = createMockSbn(
            packageName = "com.google.android.gm",
            title = "Urgent Email",
            text = "Please check immediately"
        )
        assertTrue("Configured custom package should be interesting", service.isInteresting(sbn))
    }

    @Test
    fun testSilentSuffixAtEndOfTitleIsIgnored() {
        val sbn = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Lunch with Team /s",
            text = "12:00 PM"
        )
        assertFalse("Notification ending with /s should be ignored", service.isInteresting(sbn))
    }

    @Test
    fun testSilentSuffixAtEndOfTextIsIgnored() {
        val sbn = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Standup",
            text = "Meeting at 9:30 AM /s"
        )
        assertFalse("Notification with /s in text should be ignored", service.isInteresting(sbn))
    }

    @Test
    fun testCustomSilentSuffixIsRespected() {
        getSettingsSharedPreferences(service)
            .edit()
            .putString(ignoreSuffixKey, "#silent")
            .apply()

        val sbnMatch = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Sync #silent",
            text = "3:00 PM"
        )
        assertFalse("Custom suffix should cause notification to be ignored", service.isInteresting(sbnMatch))

        val sbnNormal = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Sync /s", // old suffix should no longer ignore when custom is set
            text = "3:00 PM"
        )
        assertTrue("Old suffix should not ignore when custom suffix is active", service.isInteresting(sbnNormal))
    }

    @Test
    fun testWordContainingSuffixIsNotFalselyIgnored() {
        // e.g. "/something" should not match suffix "/s" because "o" is a letter
        val sbn = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Visit https://example.com/system",
            text = "10:00 AM"
        )
        assertTrue("Word containing suffix letters should not be falsely ignored", service.isInteresting(sbn))
    }

    @Test
    fun testKeepRemindersIgnoredByDefault() {
        val dummyIntent = PendingIntent.getActivity(
            context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE
        )
        val icon = Icon.createWithResource(context, android.R.drawable.ic_menu_edit)
        val keepAction = Notification.Action.Builder(icon, "Open note", dummyIntent).build()

        val sbn = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Buy Groceries",
            text = "Reminder",
            actions = arrayOf(keepAction)
        )

        assertFalse("Keep reminder with Open note action should be ignored by default", service.isInteresting(sbn))
    }

    @Test
    fun testKeepRemindersAllowedWhenSettingDisabled() {
        getSettingsSharedPreferences(service)
            .edit()
            .putBoolean(ignoreKeepKey, false)
            .apply()

        val dummyIntent = PendingIntent.getActivity(
            context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE
        )
        val icon = Icon.createWithResource(context, android.R.drawable.ic_menu_edit)
        val keepAction = Notification.Action.Builder(icon, "Open note", dummyIntent).build()

        val sbn = createMockSbn(
            packageName = "com.google.android.calendar",
            title = "Buy Groceries",
            text = "Reminder",
            actions = arrayOf(keepAction)
        )

        assertTrue("Keep reminder should be interesting when ignoreKeep setting is false", service.isInteresting(sbn))
    }
}

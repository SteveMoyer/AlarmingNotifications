package org.fischman.alarmingnotifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.getSystemService


class NotificationListener : NotificationListenerService() {
    private val mp = MediaPlayer()
    private var originalNotificationKeyToAlarmingID: MutableMap<String, Int> = mutableMapOf()

    override fun onListenerConnected() {
        log("onListenerConnected")
        MuteStatusNotification.startWatching(this)
    }
    override fun onListenerDisconnected() {
        log("onListenerDisconnected")
        MuteStatusNotification.stopWatching()
    }

    override fun onCreate() {
        super.onCreate()
        mp.setDataSource(
            applicationContext,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        )
        mp.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        mp.isLooping = true
    }

    private fun extractText(sbn: StatusBarNotification): List<CharSequence> {
        val extras = sbn.notification.extras
        return (listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
        ) + (if (Build.VERSION.SDK_INT >= 33)
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
        else
            @Suppress("DEPRECATION") extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                )?.mapNotNull { (it as? Bundle)?.getCharSequence("text") }.orEmpty()
        ).filter { it.any(Char::isLetterOrDigit) }
    }

    private fun isInteresting(sbn: StatusBarNotification): Boolean {
        val prefs = getSettingsSharedPreferences(this)

        // Ignore Keep Reminders, now surfaced as Tasks notifications from Calendar (when Tasks app isn't installed), unless disabled.
        if (prefs.getBoolean(ignoreKeepKey, true)) {
            if (sbn.notification.actions?.any {
                    it.title == "Open note" && it.getIcon() != null
                } ?: false) {
                return false
            }
        }

        // Ignore notifications whose text ends with the configured suffix
        val suffix = prefs.getString(ignoreSuffixKey, "/s")!!
        if (suffix.isNotEmpty()) {
            // Match suffix at end of trimmed text, followed by non-letter or end
            // https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html#ubc
            val pattern = Regex(Regex.escape(suffix) + "(\\P{L}|$)")
            if (extractText(sbn).any { it.toString().trim().contains(pattern) }) {
                return false
            }
        }

        val alarmPackages: Set<String> = prefs.getStringSet(alarmPackagesKey, defaultAlarmPackages)!!
        if (!(sbn.packageName in alarmPackages)) return false

        return !originalNotificationKeyToAlarmingID.contains(sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        originalNotificationKeyToAlarmingID[sbn.key]?.let { getSystemService<TriggerAlarm>()!!.dismiss(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val mutedUntilStr = mutedUntil(this)
        if (mutedUntilStr != "") {
            log("Suppressing notification because muted until $mutedUntilStr")
            return
        }

        if (!isInteresting(sbn)) {
            return
        }

        // Other places that text can be stored in Notifications. Possibly of future interest for apps other than GCal and GMail.
        if (0 > 1) {
            val textFields = mutableListOf(
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TITLE_BIG,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_INFO_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_SUMMARY_TEXT,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_TEXT_LINES,
            )
            if (Build.VERSION.SDK_INT >= 31) {
                textFields += "Notification.EXTRA_VERIFICATION_TEXT"
            }
            val textContents = "${sbn.notification.tickerText}\n${
                textFields.joinToString(separator = "\n") { fieldName: String ->
                    @Suppress("DEPRECATION")
                    "$fieldName - ${sbn.notification.extras.get(fieldName)?.toString()}"
                }
            }"
            log("All text-related fields from notification: $textContents")
            log("Full notification: $sbn")
            log("and extras: ")
            for (key in sbn.notification.extras.keySet()) {
                @Suppress("DEPRECATION")
                log("key:" + key + ", value: " + sbn.notification.extras.get(key)?.toString())
            }
        }

        val notification = sbn.notification
        val tickerText = notification.tickerText?.toString()
        val extraText = notification.extras.getString(Notification.EXTRA_TEXT)
        val titleText = notification.extras.getString(Notification.EXTRA_TITLE)
        if (tickerText == null && extraText == null && titleText == null) {
            log("Both ticker and extras text are null, so ignoring notification"); return
        }
        // Ignore all-day events with this one weird trick! Unfortunately doesn't seem to be any
        // other indication of all-day nature of an event other than this text (and absence of a
        // time-window instead).
        if (extraText == "Tomorrow") return

        // Ignore notifications for events that start after 2am tomorrow.
        if (extraText?.contains("Tomorrow, (0[2-9]|1|2)".toRegex()) == true) return

        val label =
            ((tickerText ?: "") + "\n" + (extraText ?: "") + "\n" + (titleText ?: "")).trim()
        log("onNotificationPosted: $label")

        // Count-mute is checked here (late), unlike time-mute (early), because the
        // countdown should only decrement for notifications that would have actually
        // triggered an alarm — i.e. after isInteresting and text-content filtering.
        if (decrementMuteCount(this)) {
            val remaining = muteCountRemaining(this)
            originalNotificationKeyToAlarmingID[sbn.key] = -1 // Suppress future iterations of this notification, too.
            log("Suppressing notification because muted for $remaining more notification(s)")
            return
        }

        var notificationId = getSystemService<TriggerAlarm>()!!.showNotification(label, sbn.key)
        this.originalNotificationKeyToAlarmingID[sbn.key] =
            notificationId
    }






}

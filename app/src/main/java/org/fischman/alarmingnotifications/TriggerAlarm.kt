package org.fischman.alarmingnotifications
import androidx.core.content.getSystemService

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.app.Notification
import android.app.NotificationManager
import android.app.Service.START_NOT_STICKY
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlin.random.Random

import android.app.Service
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder

class TriggerAlarm : Service() {
    private val activeAlarms = HashSet<Int>()
    private val mp = MediaPlayer()

    // 1. Create the Binder instance given to activities
    private val binder = AlarmBinder()

    inner class AlarmBinder : Binder() {
        // Returns the service instance so activities can call public methods
        fun getService(): TriggerAlarm = this@TriggerAlarm
    }

    @Suppress("DEPRECATION")
    fun showNotification(

        label: String, originalNotificationKey: String
    ): Int {
        if (!this.mp.isPlaying) {
            this.mp.prepare()
            this.mp.start()
        }

        val notificationID = Random.nextInt(0, maxRandomNotificationId)


        val stopIntent =
            this.createPendingIntent(
                "stop",
                notificationID,
                label,
                originalNotificationKey
            )
        val snooze1mIntent =
            this.createPendingIntent(
                "snooze1m",
                notificationID,
                label,
                originalNotificationKey
            )
        val snooze5mIntent =
            this.createPendingIntent(
                "snooze5m",
                notificationID,
                label,
                originalNotificationKey
            )

        val notificationManager = getSystemService<NotificationManager>()
        val publicVersion = Notification.Builder(this, notificationChannelID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(this.getString(R.string.app_name))
            .setContentText("Unlock to view details")
            .build()
        val notificationBuilder =
            Notification.Builder(this, notificationChannelID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setContentTitle(label)
                .setContentText("")
                .setCategory(Notification.CATEGORY_CALL)
                .setFlag(Notification.FLAG_NO_CLEAR, true)
                .setDeleteIntent(stopIntent)
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.stat_notify_call_mute,
                        "Stop",
                        stopIntent
                    )
                        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MUTE)
                        .build()
                )
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.stat_notify_call_mute,
                        "Snooze 1m",
                        snooze1mIntent
                    )
                        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MUTE)
                        .build()
                )
                .addAction(
                    Notification.Action.Builder(
                        android.R.drawable.stat_notify_call_mute,
                        "Snooze 5m",
                        snooze5mIntent
                    )
                        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MUTE)
                        .build()
                )
        notificationManager!!.notify(notificationID, notificationBuilder.build())
        return notificationID
    }

    private fun createPendingIntent(
        action: String,
        notificationID: Int,
        label: String,
        originalNotificationKey: String
    ): PendingIntent {
        val intent = Intent(this, TriggerAlarm::class.java)
        intent.data =
            Uri.parse("alarmingnotifications://$action/$notificationID/${originalNotificationKey.hashCode()}") // Uniquify intent.
        intent.putExtra("action", action)
        intent.putExtra("notificationID", notificationID)
        intent.putExtra("label", label)
        intent.putExtra("originalNotificationKey", originalNotificationKey)
        return PendingIntent.getService(
            this,
            0, // Unused but platform requires >= 0.
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun bundleToString(bundle: Bundle?): String {
        if (bundle == null) return "(null bundle)"
        var str = "Bundle{"
        @Suppress("DEPRECATION")
        for (key in bundle.keySet()) str += " $key: ${bundle[key]};"
        str += "}"
        return str
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("extras: ${bundleToString(intent?.extras)}")
        val action = intent?.getStringExtra("action") ?: return START_NOT_STICKY
        val label = intent.getStringExtra("label") ?: return START_NOT_STICKY
        val originalNotificationKey =
            intent.getStringExtra("originalNotificationKey") ?: return START_NOT_STICKY

        if (action == "show") {
            showNotification(label, originalNotificationKey)
            return START_NOT_STICKY
        }

        val notificationID = intent.getIntExtra("notificationID", -1)
        if (notificationID < 0) return START_NOT_STICKY

        when (action) {
            "stop" -> {
                dismiss(notificationID)
            }

            "snooze1m", "snooze5m" -> {
                snooze(action, label, notificationID)
            }

            else -> {
                Log.e("AMI", "Unknown action: $action!")
            }
        }
        return START_NOT_STICKY

    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }


    public fun dismiss(notificationID: Int) {
        log("dismiss: notificationID: $notificationID")
        activeAlarms.remove(notificationID)
        if (mp.isPlaying && activeAlarms.none { it >= 0 }) {
            mp.stop()
        }

        getSystemService(NotificationManager::class.java).cancel(notificationID)

    }

    private fun snooze(
        action: String,
        label: String,
        notificationID: Int,

        ) {
        log("snooze: $action $label $notificationID")
        val durStr = action.removePrefix("snooze")
        if (durStr == action) {
            Log.wtf("AMI", "Missing prefix 'snooze' in $action")
        }
        val minutesStr = durStr.removeSuffix("m")
        if (minutesStr == durStr) {
            Log.wtf("AMI", "Missing suffix 'm' in $action")
        }
        val minutes = minutesStr.toInt()
        if (minutes != 5 && minutes != 1) {
            Log.wtf("AMI", "Unexpected snooze duration of $minutes in $action")
        }

        dismiss(notificationID)

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationListener::class.java)
        intent.data =
            Uri.parse("alarmingnotifications://resurrect/$notificationID}") // Uniquify intent.
        intent.putExtra("action", "show")
        intent.putExtra("label", label)

        val pendingIntent = PendingIntent.getService(
            this,
            0, // Unused but platform requires >= 0.
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val aci =
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 60 * 1000 * minutes, null)
        log("snoozed for $minutes minutes")
        setExactAlarm(alarmManager, aci, pendingIntent)
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun setExactAlarm(
        alarmManager: AlarmManager,
        aci: AlarmManager.AlarmClockInfo,
        pendingIntent: PendingIntent
    ) {
        alarmManager.setAlarmClock(aci, pendingIntent)
    }

    override fun onCreate() {
        super.onCreate()
        mp.setDataSource(
            applicationContext,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        )
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        mp.isLooping = true
    }
}
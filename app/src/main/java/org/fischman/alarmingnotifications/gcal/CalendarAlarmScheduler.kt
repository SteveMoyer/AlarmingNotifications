package org.fischman.alarmingnotifications.gcal

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.fischman.alarmingnotifications.TriggerAlarm

/**
 * Schedules exact alarms for the "on" reminders of calendar events and persists them so they can
 * be restored after a reboot or app update.
 *
 * When an alarm fires it starts [TriggerAlarm] with the "show" action, which causes the service to
 * call [TriggerAlarm.showNotification].
 */
class CalendarAlarmScheduler(
    private val context: Context,
    private val repository: ScheduledAlarmRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Registers an exact alarm for each on/future reminder and replaces the persisted set. */
    suspend fun scheduleAlarms(events: List<CalendarAlarmConfig>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduled = mutableListOf<ScheduledAlarm>()
        for (event in events) {
            for (reminder in event.reminders) {
                if (!reminder.status.shouldCreateAlarm()) continue

                val triggerAt = event.startTime - reminder.minutes * MINUTE_MILLIS
                if (triggerAt <= now()) continue

                registerAlarm(alarmManager, event.id, reminder.minutes, event.title, triggerAt)
                scheduled += ScheduledAlarm(event.id, reminder.minutes, event.title, triggerAt)
            }
        }
        repository.replaceAll(scheduled)
    }

    /** Cancels every persisted alarm and clears the persisted set. */
    suspend fun cancelAll() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (alarm in repository.getAll()) {
            val intent = Intent(context, TriggerAlarm::class.java).apply {
                data = Uri.parse("alarmingnotifications://schedule/${alarm.eventId}/${alarm.minutes}")
            }
            val pendingIntent = PendingIntent.getService(
                context,
                "${alarm.eventId}:${alarm.minutes}".hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent)
        }
        repository.replaceAll(emptyList())
    }

    /**
     * Re-registers every persisted alarm that is still in the future, dropping any that have
     * already passed. Used after a reboot or app update, both of which clear pending alarms.
     */
    suspend fun reschedulePersisted() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val persisted = repository.getAll()
        val future = persisted.filter { it.triggerAt > now() }
        for (alarm in future) {
            registerAlarm(alarmManager, alarm.eventId, alarm.minutes, alarm.label, alarm.triggerAt)
        }
        if (future.size != persisted.size) {
            repository.replaceAll(future)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun registerAlarm(
        alarmManager: AlarmManager,
        eventId: String,
        minutes: Int,
        label: String,
        triggerAt: Long,
    ) {
        val intent = Intent(context, TriggerAlarm::class.java).apply {
            data = Uri.parse("alarmingnotifications://schedule/$eventId/$minutes")
            putExtra("action", "show")
            putExtra("label", label)
            putExtra("originalNotificationKey", "gcal_${eventId}_${minutes}")
        }
        val pendingIntent = PendingIntent.getService(
            context,
            "$eventId:$minutes".hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, null), pendingIntent)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

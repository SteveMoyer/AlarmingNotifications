package org.fischman.alarmingnotifications.gcal

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.fischman.alarmingnotifications.TriggerAlarm

/**
 * Schedules exact alarms for the "on" reminders of calendar events.
 *
 * When an alarm fires it starts [TriggerAlarm] with the "show" action, which causes the
 * service to call [TriggerAlarm.showNotification].
 */
class CalendarAlarmScheduler(
    private val context: Context,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAlarms(events: List<CalendarAlarmConfig>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (event in events) {
            for (reminder in event.reminders) {
                if (!reminder.status.shouldCreateAlarm()) continue

                val triggerAt = event.startTime - reminder.minutes * MINUTE_MILLIS
                if (triggerAt <= now()) continue

                val intent = Intent(context, TriggerAlarm::class.java).apply {
                    data = Uri.parse("alarmingnotifications://schedule/${event.id}/${reminder.minutes}")
                    putExtra("action", "show")
                    putExtra("label", event.title)
                    putExtra("originalNotificationKey", "gcal_${event.id}_${reminder.minutes}")
                }
                val pendingIntent = PendingIntent.getService(
                    context,
                    "${event.id}:${reminder.minutes}".hashCode(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, null), pendingIntent)
            }
        }
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

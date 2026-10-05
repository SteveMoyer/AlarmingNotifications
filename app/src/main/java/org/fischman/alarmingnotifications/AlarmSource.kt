package org.fischman.alarmingnotifications

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import org.fischman.alarmingnotifications.gcal.CalendarAlarmScheduler
import org.fischman.alarmingnotifications.gcal.datastore.DataStoreScheduledAlarmRepository
import org.fischman.alarmingnotifications.gcal.datastore.scheduledAlarmsDataStore

/**
 * The two mutually-exclusive ways the app can raise alarms.
 *
 * [NOTIFICATIONS] promotes interesting notifications (including calendar ones) as they are posted.
 * [SCHEDULED] reads the calendar and fires exact alarms at the configured reminder offsets.
 */
enum class AlarmSource {
    NOTIFICATIONS,
    SCHEDULED,
}

/** Returns the persisted alarm source, defaulting to [AlarmSource.NOTIFICATIONS]. */
fun getAlarmSource(context: Context): AlarmSource {
    val stored = getSettingsSharedPreferences(context).getString(alarmSourceKey, null)
    return AlarmSource.entries.firstOrNull { it.name == stored } ?: AlarmSource.NOTIFICATIONS
}

/** Persists the alarm source without touching the listener binding or scheduled alarms. */
fun setAlarmSource(context: Context, source: AlarmSource) {
    getSettingsSharedPreferences(context).edit().putString(alarmSourceKey, source.name).apply()
}

internal fun notificationListenerComponentName(context: Context): ComponentName =
    ComponentName(context.packageName, NotificationListener::class.java.name)

/**
 * Binds or unbinds the notification listener to match [source]. In [AlarmSource.SCHEDULED] the
 * listener is unbound entirely so it does no work; the user's notification-access grant is kept,
 * so switching back to [AlarmSource.NOTIFICATIONS] rebinds without any user action.
 *
 * Unbinding from outside the service via [NotificationListenerService.requestUnbind] requires
 * API 34. On older versions the connected listener self-unbinds when it observes the source
 * preference change (see [NotificationListener]).
 */
fun applyListenerBinding(context: Context, source: AlarmSource) {
    val component = notificationListenerComponentName(context)
    when (source) {
        AlarmSource.NOTIFICATIONS -> NotificationListenerService.requestRebind(component)
        AlarmSource.SCHEDULED ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                NotificationListenerService.requestUnbind(component)
            }
    }
}

/**
 * Switches the active alarm source: persists it, updates the listener binding, and cancels any
 * scheduled calendar alarms when switching to [AlarmSource.NOTIFICATIONS] so events can't alarm
 * twice.
 */
suspend fun switchAlarmSource(context: Context, source: AlarmSource) {
    setAlarmSource(context, source)
    applyListenerBinding(context, source)
    if (source == AlarmSource.NOTIFICATIONS) {
        CalendarAlarmScheduler(
            context.applicationContext,
            DataStoreScheduledAlarmRepository(context.applicationContext.scheduledAlarmsDataStore),
        ).cancelAll()
    }
}

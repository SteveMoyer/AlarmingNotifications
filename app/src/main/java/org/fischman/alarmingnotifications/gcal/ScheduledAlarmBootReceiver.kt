package org.fischman.alarmingnotifications.gcal

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fischman.alarmingnotifications.gcal.datastore.DataStoreScheduledAlarmRepository
import org.fischman.alarmingnotifications.gcal.datastore.scheduledAlarmsDataStore

/**
 * Restores persisted calendar alarms after a reboot or app update, since Android clears pending
 * alarms in both cases.
 */
class ScheduledAlarmBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isRestoreAction(intent?.action)) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                restoreScheduledAlarms(appContext)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}

internal fun isRestoreAction(action: String?): Boolean =
    action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED

internal suspend fun restoreScheduledAlarms(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        return
    }
    CalendarAlarmScheduler(
        context,
        DataStoreScheduledAlarmRepository(context.scheduledAlarmsDataStore),
    ).reschedulePersisted()
}

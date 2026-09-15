package org.fischman.alarmingnotifications.gcal


public data class CalendarAlarmConfig(
    val title: String,
    val startTime: Long,
    val calendarName: String,
    val id:String,
    val originalId:String,
    val eventId:String,
    val syncId:String,
    val isRepeating: Boolean,
    val status: CalendarAlarmStatus,
    val reminders: List<ReminderConfig> = emptyList()
)

public data class ReminderConfig (
    val minutes: Int,
    val status: ReminderStatus
)
public enum class CalendarAlarmStatus {
    DEFAULT,// Show it
    HIDE
}
public enum class ReminderStatus {
    DEFAULT_OFF,
    RECURRING_OFF,
    RECURRING_ON, // Defaults to on for future instances. Implies ON for this instance as well.
    ON_THIS_TIME, // On this time only. Does not affect the setting for future events.
    OFF_THIS_TIME, // Off this time only. Does not affect the setting for future events.
    HIDE; // Hide this reminder by default for future instances. Implies OFF for this instance as well.


    fun shouldUpdateRecurringStatus():Boolean {
        return when(this) {
            RECURRING_OFF,RECURRING_ON,HIDE -> true
            DEFAULT_OFF,ON_THIS_TIME,OFF_THIS_TIME -> false
        }
    }
    fun shouldCreateAlarm():Boolean {
        return when(this) {
            RECURRING_ON,ON_THIS_TIME -> true
            DEFAULT_OFF,RECURRING_OFF,OFF_THIS_TIME,HIDE -> false
        }
    }

}


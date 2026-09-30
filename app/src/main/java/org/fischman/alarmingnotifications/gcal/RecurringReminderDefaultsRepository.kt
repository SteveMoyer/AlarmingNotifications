package org.fischman.alarmingnotifications.gcal

/** A reminder the user added in the app, persisted for an event. */
data class StoredCustomReminder(
    val minutes: Int,
    val defaultStatus: ReminderStatus,
)

interface RecurringReminderDefaultsRepository {
    /**
     * Returns the persisted default statuses for calendar reminders of an event,
     * keyed by reminder minutes. Only [ReminderStatus.RECURRING_ON] and
     * [ReminderStatus.RECURRING_OFF] are returned; absence means the user has
     * not selected a default ([ReminderStatus.DEFAULT_OFF]). Custom reminders
     * added in the app are excluded.
     */
    suspend fun getReminderDefaults(eventKey: String): Map<Int, ReminderStatus>

    /**
     * Persists the default for one reminder of a recurring event.
     * [defaultStatus] must be [ReminderStatus.RECURRING_ON] or
     * [ReminderStatus.RECURRING_OFF].
     */
    suspend fun saveReminderDefault(eventKey: String, minutes: Int, defaultStatus: ReminderStatus)

    /**
     * Removes any persisted default for one calendar reminder of a recurring event,
     * reverting it to the calendar default ([ReminderStatus.DEFAULT_OFF]).
     */
    suspend fun deleteReminderDefault(eventKey: String, minutes: Int)

    /** Returns the reminders the user added in the app for an event. */
    suspend fun getCustomReminders(eventKey: String): List<StoredCustomReminder>

    /**
     * Adds a custom reminder for an event. [singleEventDate] is the event start in
     * epoch millis for a non-repeating event, or 0 for a repeating event.
     */
    suspend fun addCustomReminder(eventKey: String, minutes: Int, singleEventDate: Long)

    /** Removes a custom reminder for an event. */
    suspend fun removeCustomReminder(eventKey: String, minutes: Int)

    /**
     * Removes every event whose [singleEventDate] is in the past (strictly before
     * [before]), which drops all of that single event's reminders.
     */
    suspend fun pruneExpiredCustomReminders(before: Long)
}

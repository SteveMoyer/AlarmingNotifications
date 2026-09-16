package org.fischman.alarmingnotifications.gcal

interface RecurringReminderDefaultsRepository {
    /**
     * Returns the persisted default statuses for reminders of a recurring event,
     * keyed by reminder minutes. Only [ReminderStatus.RECURRING_ON] and
     * [ReminderStatus.RECURRING_OFF] are returned; absence means the user has
     * not selected a default ([ReminderStatus.DEFAULT_OFF]).
     */
    suspend fun getReminderDefaults(eventKey: String): Map<Int, ReminderStatus>

    /**
     * Persists the default for one reminder of a recurring event.
     * [defaultStatus] must be [ReminderStatus.RECURRING_ON] or
     * [ReminderStatus.RECURRING_OFF].
     */
    suspend fun saveReminderDefault(eventKey: String, minutes: Int, defaultStatus: ReminderStatus)

    /**
     * Removes any persisted default for one reminder of a recurring event,
     * reverting it to the calendar default ([ReminderStatus.DEFAULT_OFF]).
     */
    suspend fun deleteReminderDefault(eventKey: String, minutes: Int)
}

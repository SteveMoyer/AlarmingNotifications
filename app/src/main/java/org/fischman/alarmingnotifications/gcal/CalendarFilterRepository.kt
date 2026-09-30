package org.fischman.alarmingnotifications.gcal

import android.content.Context
import org.fischman.alarmingnotifications.excludedCalendarIdsKey
import org.fischman.alarmingnotifications.getSettingsSharedPreferences

/**
 * Persists which calendars the user has chosen to exclude from Today's Events.
 * Calendars are selected by default, so only exclusions are stored; a calendar
 * added later is therefore selected automatically.
 */
interface CalendarFilterRepository {
    fun getExcludedCalendarIds(): Set<Long>
    fun setExcludedCalendarIds(ids: Set<Long>)
}

class SharedPreferencesCalendarFilterRepository(context: Context) : CalendarFilterRepository {
    private val prefs = getSettingsSharedPreferences(context.applicationContext)

    override fun getExcludedCalendarIds(): Set<Long> =
        prefs.getStringSet(excludedCalendarIdsKey, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    override fun setExcludedCalendarIds(ids: Set<Long>) {
        prefs.edit()
            .putStringSet(excludedCalendarIdsKey, ids.map { it.toString() }.toSet())
            .apply()
    }
}

/** Returns the events whose calendar has not been excluded. */
internal fun filterEventsByExcludedCalendars(
    events: List<CalendarAlarmConfig>,
    excludedCalendarIds: Set<Long>,
): List<CalendarAlarmConfig> = events.filter { it.calendarId !in excludedCalendarIds }

package org.fischman.alarmingnotifications.gcal

import android.content.Context
import java.time.Instant
import java.time.ZoneId

class DailyAlarmConfigReader(
    private val context: Context,
    private val defaultsRepository: RecurringReminderDefaultsRepository,
) {
    private val calendarReader by lazy { AlarmingCalendarReader(this.context) }

    /**
     * Fetches calendar events from the device content provider off the main thread.
     * Requires android.permission.READ_CALENDAR.
     */
    suspend fun fetchDefaultDailyAlarmConfig(): List<CalendarAlarmConfig> {
        defaultsRepository.pruneExpiredCustomReminders(startOfTodayMillis(System.currentTimeMillis()))
        val events = calendarReader.fetchCalendarEvents()
        return events.map { event ->
            val eventKey = event.eventKey()
            val savedDefaults = defaultsRepository.getReminderDefaults(eventKey)
            val customReminders = defaultsRepository.getCustomReminders(eventKey)
            event.toConfig(savedDefaults, customReminders)
        }
    }

    fun AlarmingCalendarEvent.toConfig(
        savedDefaults: Map<Int, ReminderStatus>,
        customReminders: List<StoredCustomReminder>,
    ) = CalendarAlarmConfig(
        title = this.title,
        startTime = this.startTime,
        calendarName = this.calendarName,
        calendarId = this.calendarId,

        id = this.id,
        originalId = this.originalId,
        eventId = this.eventId,
        syncId = this.syncId,
        isRepeating = this.isRepeating,
        status = CalendarAlarmStatus.DEFAULT,
        reminders = buildReminders(
            calendarMinutes = this.reminderMinutes,
            customReminders = customReminders,
            savedDefaults = savedDefaults,
            isRepeating = this.isRepeating,
        ),
    )
}

/** Minutes before an event that represents an alarm at the event start time. */
internal const val AT_START_MINUTES = 0

/**
 * Merges the calendar's own reminder minutes with the app-added custom reminders,
 * sorted by minutes and de-duplicated. An at-start reminder ([AT_START_MINUTES]) is
 * always included so every event offers an alarm at its start time.
 */
internal fun buildReminders(
    calendarMinutes: List<Int>,
    customReminders: List<StoredCustomReminder>,
    savedDefaults: Map<Int, ReminderStatus>,
    isRepeating: Boolean,
): List<ReminderConfig> {
    val customByMinutes = customReminders.associateBy { it.minutes }
    val allMinutes =
        (calendarMinutes + customReminders.map { it.minutes } + AT_START_MINUTES).distinct().sorted()
    return allMinutes.map { minutes ->
        val isCustom = minutes in customByMinutes && minutes !in calendarMinutes
        val defaultStatus = when {
            isCustom && isRepeating -> customByMinutes.getValue(minutes).defaultStatus
            isCustom -> ReminderStatus.ON_THIS_TIME
            else -> savedDefaults[minutes] ?: ReminderStatus.DEFAULT_OFF
        }
        ReminderConfig(
            minutes = minutes,
            status = defaultStatus,
            defaultStatus = defaultStatus,
            originalDefaultStatus = defaultStatus,
            isCustom = isCustom,
        )
    }
}

/** Epoch millis at the start of the local day containing [now]. */
internal fun startOfTodayMillis(now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(now)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

internal fun AlarmingCalendarEvent.eventKey(): String =
    originalId.takeIf { it.isNotBlank() } ?: eventId

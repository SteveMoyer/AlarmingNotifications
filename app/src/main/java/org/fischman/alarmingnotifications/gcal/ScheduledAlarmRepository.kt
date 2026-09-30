package org.fischman.alarmingnotifications.gcal

import kotlinx.coroutines.flow.Flow

/** A calendar reminder that has been scheduled as an exact alarm. */
data class ScheduledAlarm(
    val eventId: String,
    val minutes: Int,
    val label: String,
    val triggerAt: Long,
)

interface ScheduledAlarmRepository {
    /** Returns all persisted alarms. */
    suspend fun getAll(): List<ScheduledAlarm>

    /** Emits the persisted alarms, and re-emits whenever they change. */
    fun observeAll(): Flow<List<ScheduledAlarm>>

    /** Replaces the entire persisted set with [alarms]. */
    suspend fun replaceAll(alarms: List<ScheduledAlarm>)
}

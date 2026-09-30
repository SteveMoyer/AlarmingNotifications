package org.fischman.alarmingnotifications.gcal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Today

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.fischman.alarmingnotifications.gcal.datastore.DataStoreRecurringReminderDefaultsRepository
import org.fischman.alarmingnotifications.gcal.datastore.recurringReminderDefaultsDataStore
import org.fischman.alarmingnotifications.gcal.ui.theme.AlarmingNotificationsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TodaysEventsActivity : ComponentActivity() {

    private val calendarReader by lazy { AlarmingCalendarReader(this) }
    private val defaultsRepository by lazy {
        DataStoreRecurringReminderDefaultsRepository(applicationContext.recurringReminderDefaultsDataStore)
    }
    private val dailyConfigReader by lazy { DailyAlarmConfigReader(this, defaultsRepository) }
    private val alarmScheduler by lazy { CalendarAlarmScheduler(this) }

    private var events by mutableStateOf(emptyList<CalendarAlarmConfig>())
        private set

    private fun toggleReminderStatus(eventId: String, reminderIndex: Int) {
        events = events.map { event ->
            if (event.id != eventId) return@map event
            val updatedReminders = event.reminders.mapIndexed { index, reminder ->
                if (index == reminderIndex) {
                    val newStatus = nextReminderStatus(reminder.status, reminder.defaultStatus, event.isRepeating)
                    val newDefault = when (newStatus) {
                        ReminderStatus.RECURRING_ON, ReminderStatus.RECURRING_OFF -> newStatus
                        else -> reminder.defaultStatus
                    }
                    reminder.copy(status = newStatus, defaultStatus = newDefault)
                } else {
                    reminder
                }
            }
            event.copy(reminders = updatedReminders)
        }
    }

    private fun saveReminderDefaults() {
        lifecycleScope.launch {
            events.forEach { event ->
                if (!event.isRepeating) return@forEach
                val eventKey = event.originalId.takeIf { it.isNotBlank() } ?: event.eventId
                event.reminders.forEach { reminder ->
                    if (reminder.defaultStatus == reminder.originalDefaultStatus) return@forEach
                    when (reminder.defaultStatus) {
                        ReminderStatus.RECURRING_ON, ReminderStatus.RECURRING_OFF ->
                            defaultsRepository.saveReminderDefault(
                                eventKey,
                                reminder.minutes,
                                reminder.defaultStatus
                            )
                        ReminderStatus.DEFAULT_OFF ->
                            defaultsRepository.deleteReminderDefault(eventKey, reminder.minutes)
                        else -> { /* one-time overrides are not persisted */ }
                    }
                }
            }
            events = events.map { event ->
                event.copy(
                    reminders = event.reminders.map { reminder ->
                        reminder.copy(originalDefaultStatus = reminder.defaultStatus)
                    }
                )
            }
        }
    }

    private fun createAlarmsAndSave() {
        saveReminderDefaults()
        alarmScheduler.scheduleAlarms(events)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlarmingNotificationsTheme {
                val calendars by produceState(initialValue = emptyList()) {
                    value = calendarReader.fetchCalendars()
                }
                LaunchedEffect(Unit) {
                    events = dailyConfigReader.fetchDefaultDailyAlarmConfig()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        Text(
                            "Available Calendars",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold
                        )
                        CalendarList(calendars)

                        HorizontalDivider()

                        Text(
                            "Today's Events",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold
                        )
                        EventList(
                            events,
                            onReminderToggled = { eventId, reminderIndex ->
                                toggleReminderStatus(eventId, reminderIndex)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { saveReminderDefaults() },
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Text("Save")
                        }
                        Button(
                            onClick = { createAlarmsAndSave() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text("Create Alarms and Save")
                        }
                    }
                }
            }
        }
    }
}

internal fun nextReminderStatus(current: ReminderStatus, default: ReminderStatus, isRepeating: Boolean): ReminderStatus {
    if (!isRepeating) {
        return if (current.shouldCreateAlarm()) ReminderStatus.OFF_THIS_TIME else ReminderStatus.ON_THIS_TIME
    }
    return when (default) {
        ReminderStatus.RECURRING_ON -> when (current) {
            ReminderStatus.RECURRING_ON -> ReminderStatus.OFF_THIS_TIME
            ReminderStatus.OFF_THIS_TIME -> ReminderStatus.RECURRING_OFF
            ReminderStatus.RECURRING_OFF -> ReminderStatus.RECURRING_ON
            else -> ReminderStatus.RECURRING_ON
        }
        ReminderStatus.RECURRING_OFF, ReminderStatus.DEFAULT_OFF -> when (current) {
            ReminderStatus.RECURRING_OFF -> ReminderStatus.ON_THIS_TIME
            ReminderStatus.ON_THIS_TIME -> ReminderStatus.RECURRING_ON
            ReminderStatus.RECURRING_ON -> ReminderStatus.OFF_THIS_TIME
            ReminderStatus.DEFAULT_OFF -> ReminderStatus.ON_THIS_TIME
            else -> ReminderStatus.RECURRING_OFF
        }
        else -> current
    }
}

@Preview
@Composable
fun CalendarList(@PreviewParameter(PreviewCalendarProvider::class) calendars: List<AlarmingCalendar>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        calendars.forEach {  calendar ->
            CalendarItem(calendar)
        }
    }
}

@Composable
fun CalendarItem(calendar: AlarmingCalendar) {
    ListItem(
        headlineContent = { Text(calendar.displayName) },
        supportingContent = {
            val status = buildString {
                append(calendar.accountName)
                if (!calendar.isVisible) append(" • Hidden")
                if (!calendar.isSynced) append(" • Not Synced")
            }
            Text(status)
        }
    )
}

@Preview
@Composable
fun EventList(
    @PreviewParameter(PreviewEventProvider::class) events: List<CalendarAlarmConfig>,
    onReminderToggled: (eventId: String, reminderIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        events.forEach { event ->
            EventItem(event, onReminderToggled)
        }
    }
}

@Composable
fun EventItem(event: CalendarAlarmConfig, onReminderToggled: (eventId: String, reminderIndex: Int) -> Unit) {

    ListItem(
        headlineContent = { Text("${formatTime(event.startTime)} - ${event.title}")},
        supportingContent = {
            Row {
            Text("Alarms:  ")
            event.reminders.forEachIndexed { index, reminder ->
                AlarmItem(reminder, onToggle = { onReminderToggled(event.id, index) })
            }
        }
        }
    )
}

@Composable
fun AlarmItem(reminder: ReminderConfig, onToggle: () -> Unit) {
    val selected = reminder.status.shouldCreateAlarm()
    val (icon, contentDescription) = when (reminder.status) {
        ReminderStatus.RECURRING_ON -> Icons.Filled.Done to "Recurring on"
        ReminderStatus.ON_THIS_TIME -> Icons.Filled.Today to "On this time only"
        ReminderStatus.OFF_THIS_TIME -> Icons.Filled.Snooze to "Off this time only"
        ReminderStatus.RECURRING_OFF, ReminderStatus.DEFAULT_OFF -> Icons.Filled.Block to "Recurring off"
        ReminderStatus.HIDE -> Icons.Filled.Block to "Hidden"
    }
    val colors = when (reminder.status) {
        ReminderStatus.RECURRING_ON -> FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        ReminderStatus.ON_THIS_TIME -> FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
        else -> FilterChipDefaults.filterChipColors()
    }
    FilterChip(
        onClick = onToggle,
        label = {
            Text("${reminder.minutes} mins")
        },
        selected = selected,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        },
        colors = colors,
    )

}

internal fun formatTime(timeMillis: Long): String {
     val instant = Instant.ofEpochMilli(timeMillis)

    val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    //val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    ;
    return localDateTime.format(DateTimeFormatter.ofPattern("MM/dd h:mm a"))
}

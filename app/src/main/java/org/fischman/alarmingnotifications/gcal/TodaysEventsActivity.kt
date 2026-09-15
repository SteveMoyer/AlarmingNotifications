package org.fischman.alarmingnotifications.gcal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon

import androidx.compose.material.icons.filled.Done

import androidx.compose.material.icons.Icons

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
    private val dailyConfigReader by lazy { DailyAlarmConfigReader(this) }

    private var events by mutableStateOf(emptyList<CalendarAlarmConfig>())
        private set

    private fun toggleReminderStatus(eventId: String, reminderIndex: Int) {
        events = events.map { event ->
            if (event.id != eventId) return@map event
            val updatedReminders = event.reminders.mapIndexed { index, reminder ->
                if (index == reminderIndex) {
                    reminder.copy(status = nextReminderStatus(reminder.status, event.isRepeating))
                } else {
                    reminder
                }
            }
            event.copy(reminders = updatedReminders)
        }
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

internal fun nextReminderStatus(current: ReminderStatus, isRepeating: Boolean): ReminderStatus {
    return if (current.shouldCreateAlarm()) {
        if (isRepeating) ReminderStatus.RECURRING_OFF else ReminderStatus.OFF_THIS_TIME
    } else {
        if (isRepeating) ReminderStatus.RECURRING_ON else ReminderStatus.ON_THIS_TIME
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
    val selected =reminder.status.shouldCreateAlarm()
    FilterChip(
        onClick = onToggle,
        label = {
            Text("${reminder.minutes} mins")
        },
        selected = selected,
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Done icon",
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        },
    )

}

internal fun formatTime(timeMillis: Long): String {
     val instant = Instant.ofEpochMilli(timeMillis)

    val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    //val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    ;
    return localDateTime.format(DateTimeFormatter.ofPattern("MM/dd h:mm a"))
}

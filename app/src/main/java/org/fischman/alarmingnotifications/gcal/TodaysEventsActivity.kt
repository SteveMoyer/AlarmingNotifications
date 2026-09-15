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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlarmingNotificationsTheme {
                val calendars by produceState(initialValue = emptyList()) {
                    value = calendarReader.fetchCalendars()
                }
                val events by produceState(initialValue = emptyList()) {
                    value = dailyConfigReader.fetchDefaultDailyAlarmConfig()
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
                        EventList(events, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
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
fun EventList(@PreviewParameter(PreviewEventProvider::class) events: List<CalendarAlarmConfig>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        events.forEach { event ->
            EventItem(event)
        }
    }
}

@Composable
fun EventItem(event: CalendarAlarmConfig) {

    ListItem(
        headlineContent = { Text("${formatTime(event.startTime)} - ${event.title}")},
        supportingContent = {
            Row {
            Text("Alarms:  ")
            event.reminders.forEach{ reminder ->
                AlarmItem(reminder)
            }
        }
        }
    )
}

@Composable
fun AlarmItem(reminder: ReminderConfig) {
    val selected =reminder.status.shouldCreateAlarm()
    FilterChip(
        onClick = {},
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

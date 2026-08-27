package org.fischman.alarmingnotifications.gcal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fischman.alarmingnotifications.gcal.ui.theme.AlarmingNotificationsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TodaysEventsActivity : ComponentActivity() {

    private val calendarReader by lazy { AlarmingCalendarReader(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlarmingNotificationsTheme {
                val calendars by produceState<List<AlarmingCalendar>>(initialValue = emptyList()) {
                    value = calendarReader.fetchCalendars()
                }
                val events by produceState<List<AlarmingCalendarEvent>>(initialValue = emptyList()) {
                    value = calendarReader.fetchCalendarEvents()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        Text(
                            "Available Calendars",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold
                        )
                        CalendarList(calendars, modifier = Modifier.weight(1f))

                        HorizontalDivider()

                        Text(
                            "Today's Events",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold
                        )
                        EventList(events, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarList(calendars: List<AlarmingCalendar>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(calendars) { calendar ->
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

@Composable
fun EventList(events: List<AlarmingCalendarEvent>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(events) { event ->
            EventItem(event)
        }
    }
}

@Composable
fun EventItem(event: AlarmingCalendarEvent) {
    ListItem(
        headlineContent = { Text(event.title) },
        supportingContent = {
            Text("${formatTime(event.startTime)} • ${event.calendarName}")
        }
    )
}

private fun formatTime(timeMillis: Long): String {
    val date = Date(timeMillis)
    val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return format.format(date)
}

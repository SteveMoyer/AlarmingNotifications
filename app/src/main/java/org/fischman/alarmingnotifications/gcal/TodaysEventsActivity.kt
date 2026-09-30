package org.fischman.alarmingnotifications.gcal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.fischman.alarmingnotifications.gcal.datastore.DataStoreRecurringReminderDefaultsRepository
import org.fischman.alarmingnotifications.gcal.datastore.DataStoreScheduledAlarmRepository
import org.fischman.alarmingnotifications.gcal.datastore.recurringReminderDefaultsDataStore
import org.fischman.alarmingnotifications.gcal.datastore.scheduledAlarmsDataStore
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
    private val scheduledAlarmRepository by lazy {
        DataStoreScheduledAlarmRepository(applicationContext.scheduledAlarmsDataStore)
    }
    private val alarmScheduler by lazy { CalendarAlarmScheduler(this, scheduledAlarmRepository) }

    private var events by mutableStateOf(emptyList<CalendarAlarmConfig>())
        private set

    private fun setReminderStatus(eventId: String, reminderIndex: Int, status: ReminderStatus) {
        events = events.map { event ->
            if (event.id != eventId) return@map event
            val updatedReminders = event.reminders.mapIndexed { index, reminder ->
                if (index == reminderIndex) {
                    applyReminderSelection(reminder, status)
                } else {
                    reminder
                }
            }
            event.copy(reminders = updatedReminders)
        }
    }

    private fun addCustomReminder(eventId: String, minutes: Int) {
        val event = events.firstOrNull { it.id == eventId } ?: return
        if (!isValidCustomReminder(minutes, event.reminders.map { it.minutes })) return
        val status = if (event.isRepeating) ReminderStatus.RECURRING_ON else ReminderStatus.ON_THIS_TIME
        val reminder = ReminderConfig(
            minutes = minutes,
            status = status,
            defaultStatus = status,
            originalDefaultStatus = status,
            isCustom = true,
        )
        events = events.map { current ->
            if (current.id != eventId) {
                current
            } else {
                current.copy(reminders = (current.reminders + reminder).sortedBy { it.minutes })
            }
        }
        val singleEventDate = if (event.isRepeating) 0L else event.startTime
        lifecycleScope.launch {
            defaultsRepository.addCustomReminder(event.eventKey(), minutes, singleEventDate)
        }
    }

    private fun removeCustomReminder(eventId: String, reminderIndex: Int) {
        val event = events.firstOrNull { it.id == eventId } ?: return
        val reminder = event.reminders.getOrNull(reminderIndex) ?: return
        if (!reminder.isCustom) return
        events = events.map { current ->
            if (current.id != eventId) {
                current
            } else {
                current.copy(
                    reminders = current.reminders.filterIndexed { index, _ -> index != reminderIndex }
                )
            }
        }
        lifecycleScope.launch {
            defaultsRepository.removeCustomReminder(event.eventKey(), reminder.minutes)
        }
    }

    private fun saveReminderDefaults() {
        lifecycleScope.launch {
            events.forEach { event ->
                if (!event.isRepeating) return@forEach
                val eventKey = event.eventKey()
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
        lifecycleScope.launch {
            alarmScheduler.scheduleAlarms(events)
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
                val scheduledAlarms by produceState(initialValue = emptyList<ScheduledAlarm>()) {
                    scheduledAlarmRepository.observeAll().collect { value = it }
                }
                LaunchedEffect(Unit) {
                    events = dailyConfigReader.fetchDefaultDailyAlarmConfig()
                }
                val scheduledKeys = scheduledReminderKeys(scheduledAlarms)
                val sortedAlarms = alarmsByTriggerTime(scheduledAlarms)

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
                            scheduledKeys = scheduledKeys,
                            onReminderStatusSelected = { eventId, reminderIndex, status ->
                                setReminderStatus(eventId, reminderIndex, status)
                            },
                            onAddReminder = { eventId, minutes ->
                                addCustomReminder(eventId, minutes)
                            },
                            onRemoveReminder = { eventId, reminderIndex ->
                                removeCustomReminder(eventId, reminderIndex)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ScheduledAlarmList(sortedAlarms)
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

internal fun selectableStatuses(isRepeating: Boolean): List<ReminderStatus> =
    if (isRepeating) {
        listOf(
            ReminderStatus.RECURRING_ON,
            ReminderStatus.RECURRING_OFF,
            ReminderStatus.ON_THIS_TIME,
            ReminderStatus.OFF_THIS_TIME,
        )
    } else {
        listOf(
            ReminderStatus.ON_THIS_TIME,
            ReminderStatus.OFF_THIS_TIME,
        )
    }

internal fun reminderStatusLabel(status: ReminderStatus): String = when (status) {
    ReminderStatus.RECURRING_ON -> "On every time"
    ReminderStatus.RECURRING_OFF -> "Off every time"
    ReminderStatus.ON_THIS_TIME -> "On this time only"
    ReminderStatus.OFF_THIS_TIME -> "Off this time only"
    ReminderStatus.DEFAULT_OFF -> "Use calendar default"
    ReminderStatus.HIDE -> "Hidden"
}

internal fun applyReminderSelection(reminder: ReminderConfig, selection: ReminderStatus): ReminderConfig =
    when (selection) {
        ReminderStatus.RECURRING_ON, ReminderStatus.RECURRING_OFF ->
            reminder.copy(status = selection, defaultStatus = selection)
        else -> reminder.copy(status = selection)
    }

internal fun isValidCustomReminder(minutes: Int, existingMinutes: Collection<Int>): Boolean =
    minutes > 0 && minutes !in existingMinutes

internal fun CalendarAlarmConfig.eventKey(): String =
    originalId.takeIf { it.isNotBlank() } ?: eventId

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
    scheduledKeys: Set<Pair<String, Int>> = emptySet(),
    onReminderStatusSelected: (eventId: String, reminderIndex: Int, status: ReminderStatus) -> Unit = { _, _, _ -> },
    onAddReminder: (eventId: String, minutes: Int) -> Unit = { _, _ -> },
    onRemoveReminder: (eventId: String, reminderIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        events.forEach { event ->
            EventItem(event, scheduledKeys, onReminderStatusSelected, onAddReminder, onRemoveReminder)
        }
    }
}

@Composable
fun EventItem(
    event: CalendarAlarmConfig,
    scheduledKeys: Set<Pair<String, Int>> = emptySet(),
    onReminderStatusSelected: (eventId: String, reminderIndex: Int, status: ReminderStatus) -> Unit = { _, _, _ -> },
    onAddReminder: (eventId: String, minutes: Int) -> Unit = { _, _ -> },
    onRemoveReminder: (eventId: String, reminderIndex: Int) -> Unit = { _, _ -> },
) {
    var showAddDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("${formatTime(event.startTime)} - ${event.title}")},
        supportingContent = {
            Row {
            Text("Alarms:  ")
            event.reminders.forEachIndexed { index, reminder ->
                AlarmItem(
                    reminder,
                    isRepeating = event.isRepeating,
                    isScheduled = (event.id to reminder.minutes) in scheduledKeys,
                    onStatusSelected = { status -> onReminderStatusSelected(event.id, index, status) },
                    onRemove = { onRemoveReminder(event.id, index) },
                )
            }
            AssistChip(
                onClick = { showAddDialog = true },
                label = { Text("+") },
            )
        }
        }
    )

    if (showAddDialog) {
        AddReminderDialog(
            existingMinutes = event.reminders.map { it.minutes },
            onDismiss = { showAddDialog = false },
            onConfirm = { minutes ->
                onAddReminder(event.id, minutes)
                showAddDialog = false
            },
        )
    }
}

@Composable
fun AddReminderDialog(
    existingMinutes: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && isValidCustomReminder(minutes, existingMinutes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add reminder") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue -> text = newValue.filter { it.isDigit() } },
                    label = { Text("Minutes before event") },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                )
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(5, 10, 15, 30, 60).forEach { preset ->
                        AssistChip(
                            onClick = { text = preset.toString() },
                            label = { Text("$preset") },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { minutes?.let(onConfirm) },
                enabled = valid,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun AlarmItem(
    reminder: ReminderConfig,
    isRepeating: Boolean,
    isScheduled: Boolean = false,
    onStatusSelected: (ReminderStatus) -> Unit,
    onRemove: () -> Unit = {},
) {
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
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            onClick = { expanded = true },
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
            trailingIcon = if (isScheduled) {
                {
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = "Alarm set",
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            } else {
                null
            },
            colors = colors,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            selectableStatuses(isRepeating).forEach { option ->
                DropdownMenuItem(
                    text = { Text(reminderStatusLabel(option)) },
                    leadingIcon = {
                        if (option == reminder.status) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onStatusSelected(option)
                    }
                )
            }
            if (reminder.isCustom) {
                DropdownMenuItem(
                    text = { Text("Remove reminder") },
                    onClick = {
                        expanded = false
                        onRemove()
                    }
                )
            }
        }
    }

}

@Composable
fun ScheduledAlarmList(alarms: List<ScheduledAlarm>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Scheduled Alarms (${alarms.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (alarms.isEmpty()) {
            Text("No alarms set", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                alarms.forEach { alarm ->
                    Text(
                        "${formatTime(alarm.triggerAt)} - ${alarm.label} (${alarm.minutes} min before)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

internal fun scheduledReminderKeys(alarms: List<ScheduledAlarm>): Set<Pair<String, Int>> =
    alarms.map { it.eventId to it.minutes }.toSet()

internal fun alarmsByTriggerTime(alarms: List<ScheduledAlarm>): List<ScheduledAlarm> =
    alarms.sortedBy { it.triggerAt }

internal fun formatTime(timeMillis: Long): String {
     val instant = Instant.ofEpochMilli(timeMillis)

    val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    //val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    ;
    return localDateTime.format(DateTimeFormatter.ofPattern("MM/dd h:mm a"))
}

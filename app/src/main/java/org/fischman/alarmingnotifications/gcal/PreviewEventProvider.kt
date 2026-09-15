package org.fischman.alarmingnotifications.gcal

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class PreviewEventProvider
    : PreviewParameterProvider<List<CalendarAlarmConfig>> {
        override val values = sequenceOf(
            listOf(CalendarAlarmConfig(
                "Event Number One",
                1788480000000L,
                "mycal@gmail.com",
                "_sfklj",
                originalId = "org_id",
                eventId = "et_id",
                syncId = "sync_id",
                true,
                status = CalendarAlarmStatus.DEFAULT,
                reminders = listOf(ReminderConfig(
                    minutes = 10,
                    status =ReminderStatus.DEFAULT_OFF
                ))
            )),
            listOf(CalendarAlarmConfig(
                "Event Number Two",
                1L,
                "mycal@gmail.com",
                "_sfklj2",
                originalId = "org_id2",
                eventId = "et_id2",
                syncId = "sync_id2",
                true,
                status = CalendarAlarmStatus.DEFAULT,
                reminders = listOf(
                    ReminderConfig(
                    minutes = 10,
                    status =ReminderStatus.RECURRING_OFF
                ),
                    ReminderConfig(
                    minutes = 30,
                    status =ReminderStatus.RECURRING_ON
                )
            )
            )),
        )
    }

class PreviewCalendarProvider
    : PreviewParameterProvider<List<AlarmingCalendar>> {
    override val values = sequenceOf(
        listOf(
            AlarmingCalendar(1L, "Personal", "me@gmail.com", true, true),
            AlarmingCalendar(2L, "Work", "me@work.com", true, true)
        ),
    )
}

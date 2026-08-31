package org.fischman.alarmingnotifications.gcal

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class PreviewEventProvider
    : PreviewParameterProvider<List<AlarmingCalendarEvent>> {
        override val values = sequenceOf(
            listOf(AlarmingCalendarEvent("Event Number One", 1L, "mycal@gmail.com","_sfklj", originalId = "org_id", eventId = "et_id", syncId = "sync_id",true)),
            listOf(AlarmingCalendarEvent("Event Number Two", 1L, "mycal@gmail.com","_sfklj2", originalId = "org_id2", eventId = "et_id2", syncId = "sync_id2",true)),
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

package com.blocktime.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire types for the Google Calendar API v3. Only the fields BlockTime uses are modelled. */

@Serializable
data class EventDateTimeDto(
    /** Set for all-day events: "2026-09-17". */
    val date: String? = null,
    /** Set for timed events: RFC 3339, e.g. "2026-09-17T09:00:00+05:30". */
    val dateTime: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class ReminderOverrideDto(
    val method: String = "popup",
    val minutes: Int,
)

@Serializable
data class RemindersDto(
    val useDefault: Boolean = true,
    val overrides: List<ReminderOverrideDto>? = null,
)

@Serializable
data class ExtendedPropertiesDto(
    val private: Map<String, String>? = null,
)

@Serializable
data class EventDto(
    val id: String? = null,
    val status: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val start: EventDateTimeDto? = null,
    val end: EventDateTimeDto? = null,
    /** "transparent" means the event does not block time. */
    val transparency: String? = null,
    val colorId: String? = null,
    val htmlLink: String? = null,
    val recurrence: List<String>? = null,
    val recurringEventId: String? = null,
    val reminders: RemindersDto? = null,
    val extendedProperties: ExtendedPropertiesDto? = null,
    val eventType: String? = null,
)

@Serializable
data class EventsResponseDto(
    val items: List<EventDto> = emptyList(),
    val nextPageToken: String? = null,
    @SerialName("timeZone") val timeZone: String? = null,
)

@Serializable
data class CalendarListEntryDto(
    val id: String,
    val summary: String? = null,
    val summaryOverride: String? = null,
    val primary: Boolean = false,
    val selected: Boolean = false,
    val deleted: Boolean = false,
    val accessRole: String = "reader",
    val backgroundColor: String? = null,
)

@Serializable
data class CalendarListResponseDto(
    val items: List<CalendarListEntryDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class FreeBusyRequestDto(
    val timeMin: String,
    val timeMax: String,
    val items: List<FreeBusyItemDto>,
)

@Serializable
data class FreeBusyItemDto(val id: String)

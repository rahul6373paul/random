package com.blocktime.data.repository

import com.blocktime.data.remote.CalendarApi
import com.blocktime.data.remote.EventDateTimeDto
import com.blocktime.data.remote.EventDto
import com.blocktime.data.remote.ExtendedPropertiesDto
import com.blocktime.data.remote.ReminderOverrideDto
import com.blocktime.data.remote.RemindersDto
import com.blocktime.domain.BlockDraft
import com.blocktime.domain.CalendarEvent
import com.blocktime.domain.CalendarInfo
import com.blocktime.util.DateTimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime

/** A failure worth showing the user verbatim. */
class CalendarException(message: String, cause: Throwable? = null) : Exception(message, cause)

class CalendarRepository(
    private val api: CalendarApi,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun calendars(): List<CalendarInfo> = call {
        buildList {
            var pageToken: String? = null
            do {
                val page = api.calendarList(pageToken = pageToken)
                addAll(
                    page.items
                        .filterNot { it.deleted }
                        .map { entry ->
                            CalendarInfo(
                                id = entry.id,
                                title = entry.summaryOverride ?: entry.summary ?: entry.id,
                                primary = entry.primary,
                                accessRole = entry.accessRole,
                                backgroundColor = entry.backgroundColor,
                            )
                        },
                )
                pageToken = page.nextPageToken
            } while (pageToken != null)
        }.sortedWith(compareByDescending<CalendarInfo> { it.primary }.thenBy { it.title.lowercase() })
    }

    /** Events from every selected calendar in [start]..[end], merged and sorted by start time. */
    suspend fun events(
        calendarIds: Collection<String>,
        start: ZonedDateTime,
        end: ZonedDateTime,
    ): List<CalendarEvent> = call {
        coroutineScope {
            calendarIds
                .map { id -> async { eventsForCalendar(id, start, end) } }
                .flatMap { it.await() }
                .sortedWith(compareBy({ !it.allDay }, { it.start }))
        }
    }

    private suspend fun eventsForCalendar(
        calendarId: String,
        start: ZonedDateTime,
        end: ZonedDateTime,
    ): List<CalendarEvent> = buildList {
        var pageToken: String? = null
        do {
            val page = api.events(
                calendarId = calendarId,
                timeMin = DateTimeFormat.toRfc3339(start),
                timeMax = DateTimeFormat.toRfc3339(end),
                pageToken = pageToken,
            )
            addAll(page.items.mapNotNull { it.toDomain(calendarId) })
            pageToken = page.nextPageToken
        } while (pageToken != null)
    }

    suspend fun createBlock(draft: BlockDraft): CalendarEvent = call {
        val created = api.createEvent(draft.calendarId, draft.toDto())
        created.toDomain(draft.calendarId)
            ?: throw CalendarException("Google accepted the block but returned no event")
    }

    suspend fun updateBlock(eventId: String, draft: BlockDraft): CalendarEvent = call {
        val updated = api.patchEvent(draft.calendarId, eventId, draft.toDto())
        updated.toDomain(draft.calendarId)
            ?: throw CalendarException("Google accepted the change but returned no event")
    }

    suspend fun deleteEvent(calendarId: String, eventId: String) = call {
        api.deleteEvent(calendarId, eventId)
    }

    // --- mapping ---

    private fun EventDto.toDomain(calendarId: String): CalendarEvent? {
        if (status == "cancelled") return null
        val startDto = start ?: return null
        val endDto = end ?: return null
        val allDay = startDto.date != null

        val startAt = startDto.dateTime?.let { DateTimeFormat.parseRfc3339(it, zone) }
            ?: startDto.date?.let { DateTimeFormat.parseDate(it, zone) }
            ?: return null
        val endAt = endDto.dateTime?.let { DateTimeFormat.parseRfc3339(it, zone) }
            ?: endDto.date?.let { DateTimeFormat.parseDate(it, zone) }
            ?: return null

        return CalendarEvent(
            id = id ?: return null,
            calendarId = calendarId,
            title = summary?.takeIf { it.isNotBlank() } ?: "(No title)",
            description = description,
            location = location,
            start = startAt,
            end = endAt,
            allDay = allDay,
            busy = transparency != "transparent",
            colorId = colorId,
            htmlLink = htmlLink,
            recurring = recurringEventId != null || !recurrence.isNullOrEmpty(),
            createdByApp = extendedProperties?.private?.get(CREATED_BY_KEY) == CREATED_BY_VALUE,
        )
    }

    private fun BlockDraft.toDto() = EventDto(
        summary = title,
        description = description?.takeIf { it.isNotBlank() },
        start = EventDateTimeDto(dateTime = DateTimeFormat.toRfc3339(start), timeZone = zone.id),
        end = EventDateTimeDto(dateTime = DateTimeFormat.toRfc3339(end), timeZone = zone.id),
        transparency = if (busy) "opaque" else "transparent",
        colorId = colorId?.takeIf { it.isNotBlank() },
        recurrence = repeat.toRRule(repeatUntil)?.let { listOf(it) },
        reminders = reminderMinutes?.let {
            RemindersDto(useDefault = false, overrides = listOf(ReminderOverrideDto(minutes = it)))
        },
        extendedProperties = ExtendedPropertiesDto(
            private = mapOf(CREATED_BY_KEY to CREATED_BY_VALUE),
        ),
    )

    /** Runs [block] off the main thread and turns transport failures into readable messages. */
    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: HttpException) {
            throw CalendarException(e.userMessage(), e)
        } catch (e: IOException) {
            throw CalendarException("No connection to Google Calendar. Check your network.", e)
        }
    }

    private fun HttpException.userMessage(): String = when (code()) {
        401 -> "Your Google access expired. Reconnect in Settings."
        403 -> "Google denied the request. The Calendar API may be off, or you lack access to this calendar."
        404 -> "That calendar or event no longer exists."
        409 -> "This event was changed somewhere else. Refresh and try again."
        429 -> "Too many requests to Google Calendar. Try again in a moment."
        in 500..599 -> "Google Calendar is having trouble right now. Try again shortly."
        else -> "Google Calendar error (${code()})."
    }

    private companion object {
        const val CREATED_BY_KEY = "createdBy"
        const val CREATED_BY_VALUE = "blocktime-android"
    }
}

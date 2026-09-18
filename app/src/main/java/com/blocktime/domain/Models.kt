package com.blocktime.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** A calendar the signed-in user can read or write. */
data class CalendarInfo(
    val id: String,
    val title: String,
    val primary: Boolean,
    val accessRole: String,
    val backgroundColor: String?,
) {
    /** Only calendars we can write to may receive new blocks. */
    val canWrite: Boolean get() = accessRole == "owner" || accessRole == "writer"
}

/** A single occurrence of an event, already expanded and in the device time zone. */
data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val allDay: Boolean = false,
    /** False for events marked "free"/transparent — they never block time. */
    val busy: Boolean = true,
    val colorId: String? = null,
    val htmlLink: String? = null,
    val recurring: Boolean = false,
    /** True when this event was created by BlockTime. */
    val createdByApp: Boolean = false,
) {
    val duration: Duration get() = Duration.between(start, end)

    fun overlaps(otherStart: ZonedDateTime, otherEnd: ZonedDateTime): Boolean =
        start < otherEnd && otherStart < end
}

/** A gap in the day with nothing booked in it. */
data class FreeSlot(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
) {
    val duration: Duration get() = Duration.between(start, end)
}

/** How often a block repeats. Maps to an RFC 5545 RRULE when sent to Google. */
enum class Repeat(val label: String) {
    NONE("Does not repeat"),
    DAILY("Every day"),
    WEEKDAYS("Every weekday (Mon-Fri)"),
    WEEKLY("Every week"),
    ;

    /** `null` for [NONE]; otherwise a single RRULE line. */
    fun toRRule(until: LocalDate?): String? {
        val base = when (this) {
            NONE -> return null
            DAILY -> "RRULE:FREQ=DAILY"
            WEEKDAYS -> "RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            WEEKLY -> "RRULE:FREQ=WEEKLY"
        }
        // UNTIL must be a UTC timestamp; end of the chosen day keeps the last occurrence.
        return until?.let { "$base;UNTIL=${it.toString().replace("-", "")}T235900Z" } ?: base
    }
}

/** Everything needed to create or update a block. */
data class BlockDraft(
    val title: String,
    val calendarId: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val description: String? = null,
    val busy: Boolean = true,
    val colorId: String? = null,
    val repeat: Repeat = Repeat.NONE,
    val repeatUntil: LocalDate? = null,
    /** Minutes before start for a popup reminder; `null` uses the calendar default. */
    val reminderMinutes: Int? = null,
)

/** A one-tap preset on the block sheet. */
data class TaskTemplate(
    val title: String,
    val minutes: Int,
    val colorId: String? = null,
) {
    fun serialize(): String = listOf(title.replace("|", " "), minutes, colorId ?: "").joinToString("|")

    companion object {
        val DEFAULTS = listOf(
            TaskTemplate("Deep work", 90, GoogleEventColor.BLUEBERRY.id),
            TaskTemplate("Focus block", 60, GoogleEventColor.LAVENDER.id),
            TaskTemplate("Admin & email", 30, GoogleEventColor.GRAPHITE.id),
            TaskTemplate("Break", 15, GoogleEventColor.SAGE.id),
        )

        fun parse(raw: String): TaskTemplate? {
            val parts = raw.split("|")
            if (parts.size < 2) return null
            val minutes = parts[1].toIntOrNull() ?: return null
            return TaskTemplate(parts[0], minutes, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
        }
    }
}

/** The colour ids Google Calendar accepts for events, with their display names. */
enum class GoogleEventColor(val id: String, val label: String, val rgb: Long) {
    DEFAULT("", "Calendar default", 0xFF4285F4),
    LAVENDER("1", "Lavender", 0xFF7986CB),
    SAGE("2", "Sage", 0xFF33B679),
    GRAPE("3", "Grape", 0xFF8E24AA),
    FLAMINGO("4", "Flamingo", 0xFFE67C73),
    BANANA("5", "Banana", 0xFFF6BF26),
    TANGERINE("6", "Tangerine", 0xFFF4511E),
    PEACOCK("7", "Peacock", 0xFF039BE5),
    GRAPHITE("8", "Graphite", 0xFF616161),
    BLUEBERRY("9", "Blueberry", 0xFF3F51B5),
    BASIL("10", "Basil", 0xFF0B8043),
    TOMATO("11", "Tomato", 0xFFD50000),
    ;

    companion object {
        fun fromId(id: String?): GoogleEventColor =
            entries.firstOrNull { it.id == (id ?: "") } ?: DEFAULT
    }
}

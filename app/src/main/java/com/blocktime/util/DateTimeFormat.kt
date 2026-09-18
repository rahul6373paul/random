package com.blocktime.util

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Formatting and RFC 3339 helpers shared by the API layer and the UI. */
object DateTimeFormat {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val timeNoMinutesFormatter = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
    private val dayHeaderFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    /** "9:30 AM", collapsing "9:00 AM" to "9 AM" for hour labels on the timeline. */
    fun time(value: ZonedDateTime, collapseWholeHours: Boolean = false): String =
        if (collapseWholeHours && value.minute == 0) value.format(timeNoMinutesFormatter)
        else value.format(timeFormatter)

    fun timeRange(start: ZonedDateTime, end: ZonedDateTime): String =
        "${time(start)} – ${time(end)}"

    fun dayHeader(date: LocalDate): String = date.format(dayHeaderFormatter)

    fun shortDate(date: LocalDate): String = date.format(shortDateFormatter)

    /** "Wednesday" — the title when the day isn't today/tomorrow/yesterday. */
    fun weekdayName(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

    fun weekdayInitial(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    /** "1h 30m", "45m", "2h" — used for durations and day load. */
    fun duration(duration: Duration): String {
        val totalMinutes = duration.toMinutes()
        if (totalMinutes <= 0) return "0m"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    fun relativeDayLabel(date: LocalDate, today: LocalDate): String? = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> null
    }

    // --- RFC 3339, the wire format for the Calendar API ---

    fun toRfc3339(value: ZonedDateTime): String =
        value.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    fun parseRfc3339(value: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        OffsetDateTime.parse(value).atZoneSameInstant(zone)

    fun parseDate(value: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        LocalDate.parse(value).atStartOfDay(zone)
}

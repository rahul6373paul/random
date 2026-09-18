package com.blocktime.domain

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pure scheduling helpers: everything the day view and the block sheet need to know about
 * what is busy, what is free, and where a task still fits. No Android or network types here,
 * so this is all directly unit-testable.
 */
object Scheduling {

    /** Events that actually consume time: timed, not cancelled, not marked "free". */
    fun blockingEvents(events: List<CalendarEvent>): List<CalendarEvent> =
        events.filter { it.busy && !it.allDay && it.end > it.start }

    /** Existing events that would collide with [start]..[end], ignoring [ignoreEventId]. */
    fun conflicts(
        events: List<CalendarEvent>,
        start: ZonedDateTime,
        end: ZonedDateTime,
        ignoreEventId: String? = null,
    ): List<CalendarEvent> = blockingEvents(events)
        .filter { it.id != ignoreEventId && it.overlaps(start, end) }
        .sortedBy { it.start }

    /**
     * Merges overlapping/touching busy intervals into a minimal set, clipped to the window.
     * Sorting first and extending the current interval keeps this O(n log n).
     */
    fun busyIntervals(
        events: List<CalendarEvent>,
        windowStart: ZonedDateTime,
        windowEnd: ZonedDateTime,
    ): List<Pair<ZonedDateTime, ZonedDateTime>> {
        val clipped = blockingEvents(events)
            .mapNotNull { event ->
                val s = maxOf(event.start, windowStart)
                val e = minOf(event.end, windowEnd)
                if (s < e) s to e else null
            }
            .sortedBy { it.first }

        val merged = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()
        for ((start, end) in clipped) {
            val last = merged.lastOrNull()
            if (last != null && !start.isAfter(last.second)) {
                if (end.isAfter(last.second)) merged[merged.lastIndex] = last.first to end
            } else {
                merged += start to end
            }
        }
        return merged
    }

    /** Gaps of at least [minDuration] between [windowStart] and [windowEnd]. */
    fun freeSlots(
        events: List<CalendarEvent>,
        windowStart: ZonedDateTime,
        windowEnd: ZonedDateTime,
        minDuration: Duration = Duration.ofMinutes(15),
    ): List<FreeSlot> {
        if (!windowStart.isBefore(windowEnd)) return emptyList()
        val slots = mutableListOf<FreeSlot>()
        var cursor = windowStart
        for ((busyStart, busyEnd) in busyIntervals(events, windowStart, windowEnd)) {
            if (Duration.between(cursor, busyStart) >= minDuration) {
                slots += FreeSlot(cursor, busyStart)
            }
            if (busyEnd.isAfter(cursor)) cursor = busyEnd
        }
        if (Duration.between(cursor, windowEnd) >= minDuration) {
            slots += FreeSlot(cursor, windowEnd)
        }
        return slots
    }

    /**
     * The earliest slot of [duration] at or after [from] within the window — what the
     * "next free slot" button offers. Returns null when the day has no room left.
     */
    fun nextFreeSlot(
        events: List<CalendarEvent>,
        from: ZonedDateTime,
        duration: Duration,
        windowEnd: ZonedDateTime,
    ): FreeSlot? = freeSlots(events, from, windowEnd, duration)
        .firstOrNull()
        ?.let { FreeSlot(it.start, it.start.plus(duration)) }

    /**
     * Rounds [time] up to the next [stepMinutes] boundary so suggested start times land on
     * :00/:15/:30/:45 rather than 10:07.
     */
    fun roundUpTo(time: ZonedDateTime, stepMinutes: Long = 15): ZonedDateTime {
        val truncated = time.withSecond(0).withNano(0)
        val remainder = truncated.minute % stepMinutes
        return if (remainder == 0L && truncated == time) truncated
        else truncated.minusMinutes(remainder).plusMinutes(stepMinutes)
    }

    /** The working-hours window for the day [date] is anchored in, used as the planning window. */
    fun workingWindow(
        date: ZonedDateTime,
        dayStart: LocalTime,
        dayEnd: LocalTime,
    ): Pair<ZonedDateTime, ZonedDateTime> {
        val start = date.with(dayStart).withSecond(0).withNano(0)
        val end = date.with(dayEnd).withSecond(0).withNano(0)
        return start to (if (end.isAfter(start)) end else start.plusHours(1))
    }

    /** Total busy time inside the window — shown as the day's load. */
    fun bookedDuration(
        events: List<CalendarEvent>,
        windowStart: ZonedDateTime,
        windowEnd: ZonedDateTime,
    ): Duration = busyIntervals(events, windowStart, windowEnd)
        .fold(Duration.ZERO) { acc, (s, e) -> acc + Duration.between(s, e) }
}

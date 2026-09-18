package com.blocktime.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulingTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 9, 17)

    /** Accepts hour 24 so tests can express "midnight to midnight". */
    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        day.atStartOfDay(zone).plusHours(hour.toLong()).plusMinutes(minute.toLong())

    private fun event(
        startHour: Int,
        endHour: Int,
        startMinute: Int = 0,
        endMinute: Int = 0,
        busy: Boolean = true,
        allDay: Boolean = false,
        id: String = "e$startHour-$endHour",
    ) = CalendarEvent(
        id = id,
        calendarId = "primary",
        title = "Event $id",
        start = at(startHour, startMinute),
        end = at(endHour, endMinute),
        busy = busy,
        allDay = allDay,
    )

    @Test
    fun `free slots span the gaps between meetings`() {
        val events = listOf(event(10, 11), event(13, 14))

        val free = Scheduling.freeSlots(events, at(9), at(18))

        assertEquals(3, free.size)
        assertEquals(at(9), free[0].start)
        assertEquals(at(10), free[0].end)
        assertEquals(at(11) to at(13), free[1].start to free[1].end)
        assertEquals(at(14) to at(18), free[2].start to free[2].end)
    }

    @Test
    fun `overlapping meetings are merged into one busy block`() {
        val events = listOf(event(10, 12), event(11, 13))

        val free = Scheduling.freeSlots(events, at(9), at(18))

        assertEquals(2, free.size)
        assertEquals(at(9) to at(10), free[0].start to free[0].end)
        assertEquals(at(13) to at(18), free[1].start to free[1].end)
    }

    @Test
    fun `back to back meetings leave no gap`() {
        val events = listOf(event(9, 10), event(10, 11))

        val free = Scheduling.freeSlots(events, at(9), at(11))

        assertTrue(free.isEmpty())
    }

    @Test
    fun `gaps shorter than the minimum are not offered`() {
        val events = listOf(event(9, 10), event(10, 11, endMinute = 10))

        val free = Scheduling.freeSlots(
            events,
            at(9),
            at(11, 20),
            minDuration = Duration.ofMinutes(15),
        )

        assertTrue(free.isEmpty())
    }

    @Test
    fun `events marked free and all-day events do not block time`() {
        val events = listOf(
            event(10, 11, busy = false, id = "transparent"),
            event(0, 24, allDay = true, id = "allday"),
        )

        val free = Scheduling.freeSlots(events, at(9), at(18))

        assertEquals(1, free.size)
        assertEquals(at(9) to at(18), free[0].start to free[0].end)
    }

    @Test
    fun `events are clipped to the planning window`() {
        val events = listOf(event(8, 10))

        val free = Scheduling.freeSlots(events, at(9), at(12))

        assertEquals(1, free.size)
        assertEquals(at(10) to at(12), free[0].start to free[0].end)
    }

    @Test
    fun `conflicts report every overlapping event and ignore the edited one`() {
        val events = listOf(event(10, 11, id = "a"), event(10, 12, startMinute = 30, id = "b"))

        val all = Scheduling.conflicts(events, at(10, 15), at(10, 45))
        assertEquals(listOf("a", "b"), all.map { it.id })

        val ignoringA = Scheduling.conflicts(events, at(10, 15), at(10, 45), ignoreEventId = "a")
        assertEquals(listOf("b"), ignoringA.map { it.id })
    }

    @Test
    fun `touching events do not count as conflicts`() {
        val events = listOf(event(10, 11))

        assertTrue(Scheduling.conflicts(events, at(11), at(12)).isEmpty())
        assertTrue(Scheduling.conflicts(events, at(9), at(10)).isEmpty())
    }

    @Test
    fun `next free slot skips gaps too short for the task`() {
        // 30 minutes free at 10:00, then nothing until 12:00.
        val events = listOf(event(9, 10), event(10, 11, startMinute = 30), event(11, 12))

        val slot = Scheduling.nextFreeSlot(events, at(9), Duration.ofMinutes(45), at(18))

        assertEquals(at(12), slot?.start)
        assertEquals(at(12, 45), slot?.end)
    }

    @Test
    fun `next free slot is null when the day is full`() {
        val events = listOf(event(9, 18))

        assertNull(Scheduling.nextFreeSlot(events, at(9), Duration.ofMinutes(30), at(18)))
    }

    @Test
    fun `next free slot can take a short gap when the task is short`() {
        val events = listOf(event(9, 10), event(10, 18, startMinute = 30))

        val slot = Scheduling.nextFreeSlot(events, at(9), Duration.ofMinutes(20), at(18))

        assertEquals(at(10), slot?.start)
        assertEquals(at(10, 20), slot?.end)
    }

    @Test
    fun `booked duration counts merged busy time only`() {
        val events = listOf(event(9, 11), event(10, 12), event(14, 15, busy = false))

        val booked = Scheduling.bookedDuration(events, at(9), at(18))

        assertEquals(Duration.ofHours(3), booked)
    }

    @Test
    fun `round up moves to the next quarter hour`() {
        assertEquals(at(10, 15), Scheduling.roundUpTo(at(10, 7)))
        assertEquals(at(11), Scheduling.roundUpTo(at(10, 50)))
        assertEquals(at(10, 30), Scheduling.roundUpTo(at(10, 30)))
    }

    @Test
    fun `working window falls back to an hour when end is before start`() {
        val anchor = day.atStartOfDay(zone)

        val (start, end) = Scheduling.workingWindow(anchor, LocalTime.of(18, 0), LocalTime.of(9, 0))

        assertEquals(at(18), start)
        assertEquals(at(19), end)
    }

    @Test
    fun `an empty window yields no slots`() {
        assertTrue(Scheduling.freeSlots(emptyList(), at(9), at(9)).isEmpty())
    }
}

package com.blocktime.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RepeatTest {

    @Test
    fun `no repeat produces no rule`() {
        assertNull(Repeat.NONE.toRRule(null))
        assertNull(Repeat.NONE.toRRule(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `weekday repeat lists the five working days`() {
        assertEquals("RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", Repeat.WEEKDAYS.toRRule(null))
    }

    @Test
    fun `an end date is appended as a UTC UNTIL stamp`() {
        assertEquals(
            "RRULE:FREQ=DAILY;UNTIL=20261231T235900Z",
            Repeat.DAILY.toRRule(LocalDate.of(2026, 12, 31)),
        )
    }

    @Test
    fun `templates survive a serialize and parse round trip`() {
        val template = TaskTemplate("Deep work", 90, "9")

        val parsed = TaskTemplate.parse(template.serialize())

        assertEquals(template, parsed)
    }

    @Test
    fun `malformed template strings are dropped rather than crashing`() {
        assertNull(TaskTemplate.parse("just-a-title"))
        assertNull(TaskTemplate.parse("title|not-a-number"))
    }

    @Test
    fun `unknown colour ids fall back to the calendar default`() {
        assertEquals(GoogleEventColor.DEFAULT, GoogleEventColor.fromId("99"))
        assertEquals(GoogleEventColor.DEFAULT, GoogleEventColor.fromId(null))
        assertEquals(GoogleEventColor.TOMATO, GoogleEventColor.fromId("11"))
    }
}

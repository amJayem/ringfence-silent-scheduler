package com.ringfence.silentscheduler.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class RepeatDaysFormattingTest {

    @Test
    fun `every day`() {
        assertEquals("Every day", DayOfWeek.entries.toSet().toRepeatSummary())
    }

    @Test
    fun `weekdays only`() {
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
        assertEquals("Weekdays", weekdays.toRepeatSummary())
    }

    @Test
    fun `weekends only`() {
        assertEquals("Weekends", setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).toRepeatSummary())
    }

    @Test
    fun `arbitrary subset lists abbreviated days in week order`() {
        val days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertEquals("Mon, Wed, Fri", days.toRepeatSummary())
    }

    @Test
    fun `empty set`() {
        assertEquals("Never", emptySet<DayOfWeek>().toRepeatSummary())
    }
}

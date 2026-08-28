package com.ringfence.silentscheduler.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormattingTest {

    @Test
    fun `countdown clock under a minute`() {
        assertEquals("0:09", formatCountdownClock(9))
    }

    @Test
    fun `countdown clock under an hour`() {
        assertEquals("29:58", formatCountdownClock(29 * 60 + 58))
    }

    @Test
    fun `countdown clock past an hour includes hours`() {
        assertEquals("1:29:58", formatCountdownClock(60 * 60 + 29 * 60 + 58))
    }

    @Test
    fun `minutesBetween same-day window`() {
        assertEquals(20, minutesBetween(13 * 60 + 15, 13 * 60 + 35))
    }

    @Test
    fun `minutesBetween overnight window wraps past midnight`() {
        assertEquals(8 * 60, minutesBetween(23 * 60, 7 * 60))
    }

    @Test
    fun `minutesBetween equal start and end treated as a full day`() {
        assertEquals(24 * 60, minutesBetween(9 * 60, 9 * 60))
    }

    @Test
    fun `minutes only under an hour`() {
        assertEquals("24m", formatDurationMinutes(24))
    }

    @Test
    fun `hours and minutes under a day`() {
        assertEquals("2h 55m", formatDurationMinutes(2 * 60 + 55))
    }

    @Test
    fun `exactly one day rolls to day granularity`() {
        assertEquals("1d 0h", formatDurationMinutes(24 * 60))
    }

    @Test
    fun `days and hours under a week`() {
        assertEquals("3d 4h", formatDurationMinutes(3 * 24 * 60 + 4 * 60))
    }

    @Test
    fun `exactly one week rolls to week granularity`() {
        assertEquals("1w 0d", formatDurationMinutes(7 * 24 * 60))
    }

    @Test
    fun `weeks and days`() {
        assertEquals("1w 2d", formatDurationMinutes(9 * 24 * 60))
    }
}

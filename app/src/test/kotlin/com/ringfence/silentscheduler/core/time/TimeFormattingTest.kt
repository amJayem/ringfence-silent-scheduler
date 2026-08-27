package com.ringfence.silentscheduler.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormattingTest {

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

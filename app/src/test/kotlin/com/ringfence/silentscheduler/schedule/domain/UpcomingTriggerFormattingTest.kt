package com.ringfence.silentscheduler.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class UpcomingTriggerFormattingTest {

    @Test
    fun `under 12 hours away shows a live countdown`() {
        val now = LocalDateTime.of(2026, 3, 2, 9, 0)
        val start = LocalDateTime.of(2026, 3, 2, 20, 30) // 11h30m away
        assertEquals("Next in 11h 30m", formatUpcomingTrigger(now, start))
    }

    @Test
    fun `exactly 12 hours away switches to absolute time`() {
        val now = LocalDateTime.of(2026, 3, 2, 9, 0)
        val start = LocalDateTime.of(2026, 3, 2, 21, 0) // exactly 12h away, same day
        assertEquals("Today at 9:00 PM", formatUpcomingTrigger(now, start))
    }

    @Test
    fun `12h or more away on the next calendar day reads Tomorrow`() {
        val now = LocalDateTime.of(2026, 3, 7, 8, 0) // Saturday
        val start = LocalDateTime.of(2026, 3, 8, 9, 30) // Sunday
        assertEquals("Tomorrow at 9:30 AM", formatUpcomingTrigger(now, start))
    }

    @Test
    fun `weekday-only schedule viewed on Saturday reads Next Monday, not a 62h countdown`() {
        val now = LocalDateTime.of(2026, 3, 7, 8, 0) // Saturday
        val start = LocalDateTime.of(2026, 3, 9, 9, 30) // Monday
        assertEquals("Next Monday at 9:30 AM", formatUpcomingTrigger(now, start))
    }
}

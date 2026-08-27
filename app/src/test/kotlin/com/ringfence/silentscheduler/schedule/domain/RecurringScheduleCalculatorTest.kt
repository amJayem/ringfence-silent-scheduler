package com.ringfence.silentscheduler.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class RecurringScheduleCalculatorTest {

    private val everyDay = DayOfWeek.entries.toSet()
    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    )

    private fun schedule(
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        repeatDays: Set<DayOfWeek> = everyDay
    ) = Schedule(
        id = "test",
        label = "Test",
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        repeatDays = repeatDays,
        isEnabled = true
    )

    // Dhuhr prayer: 1:15 PM - 1:35 PM, every day
    private val sameDaySchedule = schedule(startMinuteOfDay = 13 * 60 + 15, endMinuteOfDay = 13 * 60 + 35)

    @Test
    fun `same-day window before start returns today`() {
        val now = LocalDateTime.of(2026, 3, 2, 9, 0) // Monday 9:00 AM
        val occurrence = RecurringScheduleCalculator.nextOccurrence(sameDaySchedule, now)
        assertEquals(LocalDateTime.of(2026, 3, 2, 13, 15), occurrence.start)
        assertEquals(LocalDateTime.of(2026, 3, 2, 13, 35), occurrence.end)
    }

    @Test
    fun `same-day window during the window returns today, already started`() {
        val now = LocalDateTime.of(2026, 3, 2, 13, 20) // Monday 1:20 PM, inside the window
        val occurrence = RecurringScheduleCalculator.nextOccurrence(sameDaySchedule, now)
        assertEquals(LocalDateTime.of(2026, 3, 2, 13, 15), occurrence.start)
        assertEquals(LocalDateTime.of(2026, 3, 2, 13, 35), occurrence.end)
    }

    @Test
    fun `same-day window after it ended rolls to tomorrow`() {
        val now = LocalDateTime.of(2026, 3, 2, 14, 0) // Monday 2:00 PM, after it ended
        val occurrence = RecurringScheduleCalculator.nextOccurrence(sameDaySchedule, now)
        assertEquals(LocalDateTime.of(2026, 3, 3, 13, 15), occurrence.start)
        assertEquals(LocalDateTime.of(2026, 3, 3, 13, 35), occurrence.end)
    }

    // Sleep: 11:00 PM - 7:00 AM, every day (overnight)
    private val overnightSchedule = schedule(startMinuteOfDay = 23 * 60, endMinuteOfDay = 7 * 60)

    @Test
    fun `overnight window before tonight's start returns tonight through tomorrow morning`() {
        val now = LocalDateTime.of(2026, 3, 2, 20, 0) // Monday 8:00 PM
        val occurrence = RecurringScheduleCalculator.nextOccurrence(overnightSchedule, now)
        assertEquals(LocalDateTime.of(2026, 3, 2, 23, 0), occurrence.start)
        assertEquals(LocalDateTime.of(2026, 3, 3, 7, 0), occurrence.end)
    }

    @Test
    fun `overnight window just after midnight returns last night's still-open window`() {
        val now = LocalDateTime.of(2026, 3, 3, 1, 0) // Tuesday 1:00 AM — inside last night's window
        val occurrence = RecurringScheduleCalculator.nextOccurrence(overnightSchedule, now)
        assertEquals("start should be Monday night, not Tuesday", LocalDateTime.of(2026, 3, 2, 23, 0), occurrence.start)
        assertEquals(LocalDateTime.of(2026, 3, 3, 7, 0), occurrence.end)
    }

    @Test
    fun `overnight window right at the boundary minute is still open`() {
        val now = LocalDateTime.of(2026, 3, 3, 6, 59) // one minute before it ends
        val occurrence = RecurringScheduleCalculator.nextOccurrence(overnightSchedule, now)
        assertEquals(LocalDateTime.of(2026, 3, 2, 23, 0), occurrence.start)
        assertEquals(LocalDateTime.of(2026, 3, 3, 7, 0), occurrence.end)
    }

    @Test
    fun `overnight window after this morning's end rolls to tonight, not last night`() {
        val now = LocalDateTime.of(2026, 3, 3, 10, 0) // Tuesday 10:00 AM, well after 7 AM end
        val occurrence = RecurringScheduleCalculator.nextOccurrence(overnightSchedule, now)
        assertEquals("should not re-fire last night's window", LocalDateTime.of(2026, 3, 3, 23, 0), occurrence.start)
        assertEquals(LocalDateTime.of(2026, 3, 4, 7, 0), occurrence.end)
    }

    // Standup: 9:30 AM - 10:00 AM, weekdays only
    private val weekdaySchedule = schedule(startMinuteOfDay = 9 * 60 + 30, endMinuteOfDay = 10 * 60, repeatDays = weekdays)

    @Test
    fun `weekday-only schedule skips the weekend`() {
        val fridayAfternoon = LocalDateTime.of(2026, 3, 6, 15, 0) // Friday, after Friday's window
        val occurrence = RecurringScheduleCalculator.nextOccurrence(weekdaySchedule, fridayAfternoon)
        assertEquals("should skip Sat/Sun to next Monday", LocalDateTime.of(2026, 3, 9, 9, 30), occurrence.start)
        assertEquals(LocalDateTime.of(2026, 3, 9, 10, 0), occurrence.end)
    }

    @Test
    fun `weekday-only schedule queried on a Saturday still resolves to Monday`() {
        val saturdayMorning = LocalDateTime.of(2026, 3, 7, 8, 0)
        val occurrence = RecurringScheduleCalculator.nextOccurrence(weekdaySchedule, saturdayMorning)
        assertEquals(LocalDateTime.of(2026, 3, 9, 9, 30), occurrence.start)
    }
}

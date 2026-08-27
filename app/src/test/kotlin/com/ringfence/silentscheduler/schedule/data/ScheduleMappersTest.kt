package com.ringfence.silentscheduler.schedule.data

import com.ringfence.silentscheduler.schedule.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class ScheduleMappersTest {

    @Test
    fun `domain to proto and back round-trips exactly`() {
        val original = Schedule(
            id = "abc-123",
            label = "Standup",
            startMinuteOfDay = 9 * 60 + 30,
            endMinuteOfDay = 10 * 60,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            isEnabled = true
        )

        val roundTripped = original.toProto().toDomain()

        assertEquals(original, roundTripped)
    }

    @Test
    fun `overnight window survives round-trip with end before start`() {
        val overnight = Schedule(
            id = "sleep",
            label = "Sleep",
            startMinuteOfDay = 23 * 60,
            endMinuteOfDay = 7 * 60,
            repeatDays = DayOfWeek.entries.toSet(),
            isEnabled = true
        )

        val roundTripped = overnight.toProto().toDomain()

        assertEquals(overnight, roundTripped)
    }
}

package com.ringfence.silentscheduler.schedule.data

import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
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
            isEnabled = true,
            silenceStyle = SilenceStyle.VIBRATE_ONLY,
            revertPolicy = RevertPolicy.SOUND
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
            isEnabled = true,
            silenceStyle = SilenceStyle.FULL_SILENT,
            revertPolicy = RevertPolicy.RESTORE
        )

        val roundTripped = overnight.toProto().toDomain()

        assertEquals(overnight, roundTripped)
    }

    @Test
    fun `schedule saved before the silence style field existed defaults to full silent`() {
        // Proto3's int32 default (0) for an unset silence_style field, e.g. a schedule
        // written by an older app version, must map back to FULL_SILENT, not crash or
        // silently pick VIBRATE_ONLY.
        val legacy = Schedule(
            id = "legacy",
            label = "Legacy",
            startMinuteOfDay = 0,
            endMinuteOfDay = 60,
            repeatDays = DayOfWeek.entries.toSet(),
            isEnabled = true
        )

        assertEquals(SilenceStyle.FULL_SILENT, legacy.toProto().toDomain().silenceStyle)
        assertEquals(RevertPolicy.RESTORE, legacy.toProto().toDomain().revertPolicy)
    }
}

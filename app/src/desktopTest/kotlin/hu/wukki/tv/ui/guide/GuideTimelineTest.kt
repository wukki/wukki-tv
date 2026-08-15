package hu.wukki.tv.ui.guide

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class GuideTimelineTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 8, 15)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `without future EPG the timeline contains today only`() {
        val timeline = guideTimeline(now, null)

        assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli(), timeline.start)
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), timeline.end)
    }

    @Test
    fun `timeline ends after the final day present in EPG data`() {
        val finalProgrammeEnd = today.plusDays(6).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val timeline = guideTimeline(now, finalProgrammeEnd)

        assertEquals(today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli(), timeline.end)
    }

    @Test
    fun `past-only EPG does not extend today`() {
        val finalProgrammeEnd = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val timeline = guideTimeline(now, finalProgrammeEnd)

        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), timeline.end)
    }
}

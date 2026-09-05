package hu.wukki.tv.ui.guide

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `viewport composes only nearby ticks on a long guide`() {
        val timeline = GuideTimeline(
            today.atStartOfDay(zone).toInstant().toEpochMilli(),
            today.plusDays(14).atStartOfDay(zone).toInstant().toEpochMilli()
        )

        val viewport = guideViewport(timeline, scrollPx = 6 * 60 * 24 * 6, viewportWidthPx = 1_920, pixelsPerMinute = 6f)

        assertTrue(viewport.tickIndices.count() < 30)
        assertTrue(viewport.from >= timeline.start)
        assertTrue(viewport.to <= timeline.end)
    }

    @Test
    fun `viewport is clamped at both timeline edges`() {
        val timeline = guideTimeline(now, null)

        val start = guideViewport(timeline, scrollPx = 0, viewportWidthPx = 1_000, pixelsPerMinute = 6f)
        val end = guideViewport(timeline, scrollPx = Int.MAX_VALUE, viewportWidthPx = 1_000, pixelsPerMinute = 6f)

        assertEquals(timeline.start, start.from)
        assertEquals(timeline.end, end.to)
        assertTrue(start.firstTickIndex >= 0)
        assertTrue(end.lastTickIndex <= timeline.halfHourTickCount - 1)
    }
}

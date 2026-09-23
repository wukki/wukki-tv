package hu.wukki.tv.webos

import hu.wukki.tv.Programme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebOsGuideStateTest {
    @Test
    fun `row virtualization keeps a bounded buffered window`() {
        assertEquals(7..18, guideVisibleRows(channelCount = 100, scrollTop = 10 * 94, viewportHeight = 5 * 94))
        assertEquals(0..8, guideVisibleRows(channelCount = 100, scrollTop = 0, viewportHeight = 5 * 94))
        assertTrue(guideVisibleRows(channelCount = 0, scrollTop = 0, viewportHeight = 500).isEmpty())
    }

    @Test
    fun `time viewport includes boundary crossing programmes`() {
        val before = programme("before", 50, 110)
        val inside = programme("inside", 120, 180)
        val after = programme("after", 200, 260)

        assertEquals(listOf(before, inside), guideVisibleProgrammes(listOf(before, inside, after), from = 100, to = 200))
    }

    @Test
    fun `focus chooses containing programme and moves horizontally`() {
        val first = programme("first", 100, 200)
        val second = programme("second", 220, 300)
        val programmes = listOf(first, second)

        assertEquals(first, guideProgrammeAt(programmes, 150))
        assertEquals(second, guideProgrammeAt(programmes, 210))
        assertEquals(second, guideAdjacentProgramme(programmes, first.webOsGuideKey(), 150, 1))
        assertNull(guideAdjacentProgramme(programmes, second.webOsGuideKey(), 250, 1))
    }

    @Test
    fun `time window follows focus and remains inside timeline`() {
        val timeline = WebOsGuideTimeline(start = 0, end = 12 * 60 * 60 * 1_000L)

        assertEquals(0, guideWindowStart(-1_000, timeline))
        assertEquals(8 * 60 * 60 * 1_000L, guideWindowStart(timeline.end, timeline))
        assertEquals(7 * 60 * 60 * 1_000L / 2, guideWindowContaining(7 * 60 * 60 * 1_000L, 0, timeline))
    }

    private fun programme(
        title: String,
        start: Long,
        end: Long,
    ) = Programme("channel", title, start, end)
}

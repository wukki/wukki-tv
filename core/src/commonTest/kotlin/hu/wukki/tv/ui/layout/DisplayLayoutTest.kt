package hu.wukki.tv.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayLayoutTest {
    @Test
    fun compactBoundaryUsesLogicalWidth() {
        assertTrue(DisplayLayout.isCompact(800f))
        assertTrue(DisplayLayout.isCompact(959f))
        assertFalse(DisplayLayout.isCompact(960f))
        assertFalse(DisplayLayout.isCompact(1920f))
    }

    @Test
    fun scalesStayReadableAndPreserveDesktopReference() {
        assertEquals(.70f, DisplayLayout.dashboardScale(320f, 240f))
        assertEquals(1f, DisplayLayout.dashboardScale(1470f, 920f))
        assertEquals(1.45f, DisplayLayout.dashboardScale(3840f, 2160f))
        assertEquals(1f, DisplayLayout.settingsScale(1920f, 1080f))
    }
}

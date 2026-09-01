package hu.wukki.tv

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopInfoPanelGeometryTest {
    @Test
    fun `full hd panel approximates the Android TV physical size`() {
        val baseScale = min(1920 / 1106f, 1080 / 762f)
        val geometry = desktopInfoPanelGeometry(1920, 1080, baseScale)

        assertTrue(geometry.width in 1507..1509)
        assertTrue(geometry.height in 320..322)
        assertEquals(1920 / 2, geometry.left + geometry.width / 2)
        assertEquals(1080 - geometry.outerMargin, geometry.top + geometry.height)
    }

    @Test
    fun `small viewport clamps the panel inside its margins`() {
        val geometry = desktopInfoPanelGeometry(400, 240, .45f)

        assertTrue(geometry.left >= geometry.outerMargin)
        assertTrue(geometry.top >= geometry.outerMargin)
        assertTrue(geometry.left + geometry.width <= 400 - geometry.outerMargin)
        assertTrue(geometry.top + geometry.height <= 240 - geometry.outerMargin)
    }

    @Test
    fun `desktop content uses the requested scale multiplier`() {
        val geometry = desktopInfoPanelGeometry(1920, 1080, 1f)

        assertEquals(DESKTOP_INFO_PANEL_SCALE, geometry.contentScale)
    }
}

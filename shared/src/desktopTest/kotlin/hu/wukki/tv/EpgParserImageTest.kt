package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpgParserImageTest {
    @Test
    fun `parses a standard XMLTV programme icon`() {
        val programme = EpgParser.parse(
            """<tv><programme channel="rtl" start="20260816180000 +0000" stop="20260816183000 +0000"><title>Híradó</title><icon src="https://images.example/rtl.jpg"/></programme></tv>"""
        ).single()

        assertEquals("https://images.example/rtl.jpg", programme.imageUrl)
    }

    @Test
    fun `uses image fallback and ignores unsafe artwork URLs`() {
        val programmes = EpgParser.parse(
            """<tv>
                <programme channel="rtl" start="20260816180000 +0000" stop="20260816183000 +0000"><title>Fallback</title><icon src="file:///private.jpg"/><image>https://images.example/fallback.jpg</image></programme>
                <programme channel="tv2" start="20260816180000 +0000" stop="20260816183000 +0000"><title>Unsafe</title><icon src="file:///private.jpg"/></programme>
            </tv>"""
        )

        assertEquals("https://images.example/fallback.jpg", programmes[0].imageUrl)
        assertNull(programmes[1].imageUrl)
    }

    @Test
    fun `old display settings enable programme images during normalisation`() {
        val normalized = AppState(
            settings = AppSettings(display = DisplaySettings(showProgrammeImages = null))
        ).normalized()

        assertEquals(true, normalized.settings?.display?.showProgrammeImages)
    }
}

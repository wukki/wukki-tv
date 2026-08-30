package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmXmlTvParserTest {
    @Test
    fun `preserves programme metadata image and timezone`() {
        val programme = JvmXmlTvParser.parse(
            """
            <tv>
              <programme channel="rtl" start="20260820180000 +0200" stop="20260820183000 +0200">
                <title>Híradó</title>
                <desc>Esti hírek</desc>
                <icon src="https://example.test/news.jpg"/>
              </programme>
            </tv>
            """.trimIndent()
        ).single()

        assertEquals("rtl", programme.channelId)
        assertEquals("Híradó", programme.title)
        assertEquals("Esti hírek", programme.description)
        assertEquals("https://example.test/news.jpg", programme.imageUrl)
        assertEquals(30L * 60L * 1000L, programme.end - programme.start)
    }

    @Test
    fun `uses image fallback and ignores unsafe artwork URLs`() {
        val programmes = JvmXmlTvParser.parse(
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

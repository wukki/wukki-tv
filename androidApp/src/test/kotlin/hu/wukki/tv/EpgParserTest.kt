package hu.wukki.tv

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class EpgParserTest {
    @Test
    fun `android parser preserves programme image and timezone`() {
        val programme = EpgParser.parse(
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
    fun `android parser rejects non-http programme image`() {
        val programme = EpgParser.parse(
            """
            <tv>
              <programme channel="rtl" start="20260820180000 +0000" stop="20260820183000 +0000">
                <title>Híradó</title>
                <icon src="file:///tmp/news.jpg"/>
              </programme>
            </tv>
            """.trimIndent()
        ).single()

        assertNull(programme.imageUrl)
    }
}

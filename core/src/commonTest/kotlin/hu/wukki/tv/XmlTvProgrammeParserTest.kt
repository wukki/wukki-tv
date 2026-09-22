package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class XmlTvProgrammeParserTest {
    @Test
    fun `parses identical metadata timezone and entities on every target`() {
        val programmes =
            XmlTvProgrammeParser.parse(
                """<tv>
                <programme channel="m2.hu" start="20260329013000 +0100" stop="20260329033000 +0200">
                  <title>M2 &amp; Petőfi</title><desc><![CDATA[Tavaszi műsor]]></desc>
                  <icon src="https://example.test/m2.jpg?x=1&amp;y=2"/>
                </programme>
                <programme channel="m2.hu" start="20260329033000 +0200" stop="20260329040000 +0200">
                  <title>Következő</title><image>https://example.test/next.jpg</image>
                </programme>
                </tv>""",
            )

        assertEquals(2, programmes.size)
        assertEquals("M2 & Petőfi", programmes[0].title)
        assertEquals("Tavaszi műsor", programmes[0].description)
        assertEquals("https://example.test/m2.jpg?x=1&y=2", programmes[0].imageUrl)
        assertEquals(60L * 60L * 1_000L, programmes[0].end - programmes[0].start)
        assertEquals(programmes[0].end, programmes[1].start)
    }

    @Test
    fun `batch parsing and full parsing return the same sorted programmes`() {
        val xml =
            """<tv>
              <programme channel="one" start="20260820190000 +0200" stop="20260820200000 +0200"><title>Second</title></programme>
              <programme channel="one" start="20260820180000 +0200" stop="20260820190000 +0200"><title>First</title></programme>
            </tv>"""
        val first = XmlTvProgrammeParser.parseBatch(xml, limit = 1)
        val second = XmlTvProgrammeParser.parseBatch(xml, first.nextOffset, limit = 1)

        assertEquals(listOf("Second"), first.programmes.map(Programme::title))
        assertEquals(listOf("First"), second.programmes.map(Programme::title))
        assertEquals(listOf("First", "Second"), XmlTvProgrammeParser.parse(xml).map(Programme::title))
    }

    @Test
    fun `rejects declarations and invalid programme boundaries`() {
        assertFailsWith<IllegalArgumentException> {
            XmlTvProgrammeParser.parse("<!DOCTYPE tv [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><tv/>")
        }
        assertEquals(
            emptyList(),
            XmlTvProgrammeParser.parse(
                """<tv><programme channel="one" start="20260820200000 +0200" stop="20260820190000 +0200"><title>Invalid</title></programme></tv>""",
            ),
        )
    }

    @Test
    fun `now next shift and progress share one boundary contract`() {
        val channel =
            Channel("one", "playlist", "One", "https://example/live", "one", "One", group = "TV", logo = null, epgChannelId = "ONE", tvgShiftHours = 1.0)
        val first = Programme("one", "First", 0L, 3_600_000L)
        val second = Programme("one", "Second", 3_600_000L, 7_200_000L)
        val index = EpgProgrammeIndex(listOf(second, first))
        val pair = index.nowAndNext(channel, 5_400_000L)

        assertEquals("First", pair.current?.title)
        assertEquals("Second", pair.next?.title)
        assertEquals(0.5, programmeProgress(pair.current, 5_400_000L))
        assertEquals(7_200_000L, index.nextBoundary(listOf(channel), 5_400_000L))
        assertNull(index.nowAndNext(channel, 20_000_000L).current)
    }
}

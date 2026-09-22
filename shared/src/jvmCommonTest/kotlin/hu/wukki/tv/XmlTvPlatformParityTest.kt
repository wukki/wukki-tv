package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class XmlTvPlatformParityTest {
    @Test
    fun `JVM production and common JS parser contract match at DST boundary`() {
        val xml =
            """<tv>
            <programme channel="m2.hu" start="20260329013000 +0100" stop="20260329033000 +0200">
              <title>M2 &amp; Petőfi</title><desc>Tavaszi műsor</desc>
              <icon src="https://example.test/m2.jpg"/>
            </programme>
            <programme channel="m2.hu" start="20260329033000 +0200" stop="20260329040000 +0200">
              <title>Következő</title>
            </programme>
            </tv>"""

        assertEquals(JvmXmlTvParser.parse(xml), XmlTvProgrammeParser.parse(xml))
    }
}

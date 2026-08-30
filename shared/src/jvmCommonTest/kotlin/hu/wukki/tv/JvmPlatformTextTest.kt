package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmPlatformTextTest {
    @Test
    fun `normalisation remains accent and case independent`() {
        assertEquals("arvizturo tukorfurogep", platformNormalize("Árvíztűrő TÜKÖRFÚRÓGÉP"))
    }

    @Test
    fun `stable channel identifier remains migration compatible`() {
        assertEquals(
            "c8d386c9-700e-3927-81c7-6ed55d3c27fb",
            stableChannelId("RTL|https://example.test/live.m3u8")
        )
    }
}

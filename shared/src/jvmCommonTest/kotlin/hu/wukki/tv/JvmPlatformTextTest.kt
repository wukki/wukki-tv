package hu.wukki.tv

import hu.wukki.tv.ui.components.platformText
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

    @Test
    fun `icon actions have localized accessible names`() {
        val labels =
            listOf(
                AppLanguage.HUNGARIAN to
                    mapOf(
                        "channels.search" to "Csatorna keresése",
                        "channels.search.close" to "Keresés bezárása",
                        "favourite.add" to "Kedvencekhez adom",
                        "favourite.remove" to "Eltávolítás a kedvencekből",
                    ),
                AppLanguage.ENGLISH to
                    mapOf(
                        "channels.search" to "Search channels",
                        "channels.search.close" to "Close search",
                        "favourite.add" to "Add to favorites",
                        "favourite.remove" to "Remove from favorites",
                    ),
            )
        labels.forEach { (language, expected) ->
            expected.forEach { (key, label) ->
                assertEquals(label, platformText(language, key, emptyList()))
            }
        }
    }
}

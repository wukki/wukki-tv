package hu.wukki.tv

import hu.wukki.tv.ui.components.displayCategoryName
import hu.wukki.tv.ui.components.tr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelCategoryLocalizationTest {
    @Test
    fun `official playlist categories display in both languages including Hungarian aliases`() {
        val categories =
            mapOf(
                "Comedy" to "Vígjáték",
                "Culture" to "Kultúra",
                "Documentary" to "Dokumentumfilmek",
                "Education" to "Oktatás",
                "Entertainment" to "Szórakozás",
                "General" to "Általános",
                "Kids" to "Gyerekek",
                "Movies" to "Filmek",
                "Music" to "Zene",
                "News" to "Hírek",
                "Outdoor" to "Szabadidő",
                "Sports" to "Sport",
            )
        categories.forEach { (english, hungarian) ->
            listOf(english, hungarian).forEach { source ->
                listOf(source, "  ${source.uppercase()}  ").forEach { variant ->
                    assertEquals(hungarian, variant.displayCategoryName(AppLanguage.HUNGARIAN), variant)
                    assertEquals(english, variant.displayCategoryName(AppLanguage.ENGLISH), variant)
                }
            }
        }
    }

    @Test
    fun `unknown and compound groups retain their exact source labels`() {
        listOf("Custom category", "Sports HD", "News;Movies", "  My channels  ").forEach { source ->
            AppLanguage.entries.forEach { language ->
                assertEquals(source, source.displayCategoryName(language))
            }
        }
    }

    @Test
    fun `missing and other category labels use the localized fallback`() {
        listOf(OTHER_CATEGORY_ID, "", "  ", "Other", " EGYÉB ").forEach { source ->
            assertEquals("Egyéb", source.displayCategoryName(AppLanguage.HUNGARIAN))
            assertEquals("Other", source.displayCategoryName(AppLanguage.ENGLISH))
        }
    }

    @Test
    fun `category empty message includes the localized category`() {
        AppLanguage.entries.forEach { language ->
            val label = "Documentary".displayCategoryName(language)
            assertTrue(tr(language, "channels.empty.category.description", label).contains(label))
        }
    }
}

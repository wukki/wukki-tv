package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertTrue

class LegalResourcesTest {
    @Test
    fun `privacy and licence notices are packaged for both supported languages`() {
        listOf(
            "legal/privacy_hu.txt",
            "legal/privacy_en.txt",
            "legal/vlc_notice_hu.txt",
            "legal/vlc_notice_en.txt"
        ).forEach { resource ->
            val content = javaClass.classLoader.getResourceAsStream(resource)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            assertTrue(content.isNotBlank(), "$resource must be available at runtime")
        }
    }
}

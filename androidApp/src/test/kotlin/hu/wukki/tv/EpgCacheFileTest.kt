package hu.wukki.tv

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpgCacheFileTest {
    @Test
    fun `compressed cache streams a round trip`() {
        val directory = File(System.getProperty("java.io.tmpdir"), "wukki-epg-cache-${System.nanoTime()}")
        assertTrue(directory.mkdirs())
        try {
            val cacheFile = EpgCacheFile(
                File(directory, "epg_cache.json.gz"),
                Json { encodeDefaults = true; ignoreUnknownKeys = true }
            )
            val cache = mapOf(
                "wukki-epg" to listOf(
                    Programme(channelId = "tv2.hu", start = 1_000L, end = 2_000L, title = "Test programme")
                )
            )

            assertTrue(cacheFile.write(cache))
            assertEquals(cache, cacheFile.read())
        } finally {
            directory.deleteRecursively()
        }
    }
}

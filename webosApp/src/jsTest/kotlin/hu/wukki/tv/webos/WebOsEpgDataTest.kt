package hu.wukki.tv.webos

import hu.wukki.tv.Programme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebOsEpgDataTest {
    @Test
    fun `parser yields between bounded batches and returns sorted data`() {
        val tasks = mutableListOf<() -> Unit>()
        var parsed = emptyList<Programme>()
        val parser = WebOsEpgParser(schedule = tasks::add, batchSize = 1)
        parser.parse(XML, { parsed = it }, { throw it })

        assertEquals(1, tasks.size)
        tasks.removeFirst()()
        assertEquals(1, tasks.size)
        assertTrue(parsed.isEmpty())
        tasks.removeFirst()()

        assertEquals(listOf("First", "Second"), parsed.map(Programme::title))
    }

    @Test
    fun `new parse cancels stale batches`() {
        val tasks = mutableListOf<() -> Unit>()
        val results = mutableListOf<List<Programme>>()
        val parser = WebOsEpgParser(schedule = tasks::add, batchSize = 1)
        parser.parse(XML, results::add, { throw it })
        parser.parse(XML.replace("First", "Current"), results::add, { throw it })

        while (tasks.isNotEmpty()) tasks.removeFirst()()

        assertEquals(1, results.size)
        assertEquals("Current", results.single().first().title)
    }

    @Test
    fun `EPG cache is versioned and independent`() {
        var stored: String? = null
        val store = WebOsEpgCacheStore({ stored }, { stored = it }, { stored = null }, now = { 0L })
        val cache = WebOsEpgCache("https://example.test/guide.xml", 42L, listOf(Programme("one", "News", 10L, 20L, "Description")))

        assertNull(store.save(cache))
        assertEquals(cache, store.load())
        assertNull(store.clear())
        assertNull(store.load())
    }

    @Test
    fun `large EPG cache keeps a bounded relevant snapshot`() {
        var stored: String? = null
        val programmes =
            (0 until 200).map { index ->
                Programme(
                    channelId = "channel-${index % 20}",
                    title = "Programme $index",
                    start = index * 1_000L,
                    end = (index + 1) * 1_000L,
                    description = "Description".repeat(20),
                )
            }
        val store =
            WebOsEpgCacheStore(
                read = { stored },
                write = { stored = it },
                remove = { stored = null },
                now = { 100_000L },
                targetCharacters = 8_000,
            )

        assertNull(store.save(WebOsEpgCache("https://example.test/guide.xml", 42L, programmes)))

        val loaded = store.load()
        assertTrue(stored.orEmpty().length <= 8_000)
        assertTrue(loaded != null && loaded.programmes.isNotEmpty())
        assertTrue(loaded.programmes.size < programmes.size)
    }

    @Test
    fun `quota failure retries with a smaller snapshot without losing previous data`() {
        var stored: String? = null
        var writes = 0
        val programmes =
            (0 until 80).map { index ->
                Programme("channel", "Programme $index", index * 1_000L, (index + 1) * 1_000L, "Long description".repeat(10))
            }
        val store =
            WebOsEpgCacheStore(
                read = { stored },
                write = { value ->
                    writes++
                    if (value.length > 2_000) throw IllegalStateException("quota")
                    stored = value
                },
                remove = { stored = null },
                now = { 0L },
            )

        assertNull(store.save(WebOsEpgCache("https://example.test/guide.xml", 42L, programmes)))
        assertTrue(writes > 1)
        assertTrue(store.load()?.programmes?.isNotEmpty() == true)

        val goodSnapshot = stored
        val failingStore = WebOsEpgCacheStore({ stored }, { throw IllegalStateException("quota") }, {}, now = { 0L })
        assertTrue(failingStore.save(WebOsEpgCache("https://example.test/guide.xml", 43L, programmes)).orEmpty().contains("quota"))
        assertEquals(goodSnapshot, stored)
    }

    @Test
    fun `local preview routes EPG through its same-origin proxy`() {
        val proxied =
            directEpgRequestUrl(
                url = "https://example.test/guide.xml",
                maxBytes = 1024,
                protocol = "http:",
                hostname = "localhost",
                origin = "http://localhost:4173",
                webOsRuntime = false,
                encodedUrl = "encoded-url",
            )

        assertEquals("http://localhost:4173/__wukki_proxy?url=encoded-url&maxBytes=1024", proxied)
    }

    @Test
    fun `packaged webOS fallback never targets the development proxy`() {
        val source = "https://example.test/guide.xml"

        assertEquals(
            source,
            directEpgRequestUrl(source, 1024, "file:", "", "null", webOsRuntime = true, encodedUrl = "encoded-url"),
        )
    }

    private companion object {
        val XML =
            """<tv>
            <programme channel="one" start="20260820190000 +0200" stop="20260820200000 +0200"><title>Second</title></programme>
            <programme channel="one" start="20260820180000 +0200" stop="20260820190000 +0200"><title>First</title></programme>
            </tv>"""
    }
}

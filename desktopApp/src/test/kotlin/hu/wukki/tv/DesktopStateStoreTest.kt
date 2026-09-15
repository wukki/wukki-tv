package hu.wukki.tv

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopStateStoreTest {
    private val json = Json { encodeDefaults = true }
    private val sample =
        AppState(
            lastChannelId = "rtl",
            recentChannelIds = listOf("rtl", "previous"),
            settings = AppSettings(language = AppLanguage.ENGLISH),
            epgSources = listOf(EpgSource("epg", "EPG", "https://example.test/epg", lastUpdatedAt = 100)),
            epgProgrammesBySource = mapOf("epg" to listOf(Programme("rtl", "News", 100, 200))),
        )

    @Test
    fun `old embedded JSON cache migrates and preference edits leave cache untouched`() =
        runBlocking {
            withDirectory { directory ->
                val preferences = directory.resolve("state.json")
                Files.writeString(preferences, json.encodeToString(AppState.serializer(), sample))
                val store = DesktopStateStore(directory)
                assertEquals(sample, store.load().state)
                assertTrue(json.decodeFromString<AppState>(Files.readString(preferences)).epgProgrammesBySource.isEmpty())
                val cache = directory.resolve("epg_cache.json.gz")
                val marker = FileTime.fromMillis(1_000)
                Files.setLastModifiedTime(cache, marker)
                val updated = sample.copy(settings = sample.settings.copy(language = AppLanguage.HUNGARIAN))
                store.save(updated)
                assertEquals(marker, Files.getLastModifiedTime(cache))
                assertEquals(updated, DesktopStateStore(directory).load().state)
            }
        }

    @Test
    fun `corrupt preferences are reported and never overwritten by legacy fallback`() =
        runBlocking {
            withDirectory { directory ->
                val preferences = directory.resolve("state.json")
                Files.writeString(preferences, "corrupt preferences")
                javaClass.getResourceAsStream("/legacy-state.bin").use { Files.copy(checkNotNull(it), directory.resolve("state.bin")) }
                assertFailsWith<Exception> { DesktopStateStore(directory).load() }
                assertEquals("corrupt preferences", Files.readString(preferences))
            }
        }

    @Test
    fun `unsupported atomic move falls back and failed save preserves previous preferences`() =
        runBlocking {
            withDirectory { directory ->
                var diskFull = false
                val store =
                    DesktopStateStore(directory) { source, target ->
                        if (diskFull) throw IOException("disk full")
                        throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "test")
                    }
                store.save(sample)
                diskFull = true
                assertFailsWith<IOException> { store.save(sample.copy(lastChannelId = "new")) }
                assertEquals(sample, DesktopStateStore(directory).load().state)
                Files.list(directory).use { paths -> assertFalse(paths.anyMatch { it.toString().endsWith(".tmp") }) }
                diskFull = false
                store.save(sample.copy(lastChannelId = "new"))
                assertEquals("new", DesktopStateStore(directory).load().state.lastChannelId)
            }
        }

    @Test
    fun `corrupt or mismatched cache preserves settings and is due for refresh`() =
        runBlocking {
            withDirectory { directory ->
                DesktopStateStore(directory).save(sample)
                Files.writeString(directory.resolve("epg_cache.json.gz"), "corrupt gzip")
                val recovered = DesktopStateStore(directory).load()
                assertTrue(recovered.cacheWarning)
                assertEquals(sample.settings, recovered.state.settings)
                assertEquals(sample.lastChannelId, recovered.state.lastChannelId)
                assertTrue(recovered.state.epgProgrammesBySource.isEmpty())
                assertEquals(
                    null,
                    recovered.state.epgSources
                        .single()
                        .lastUpdatedAt,
                )
            }
        }

    @Test
    fun `interrupted cache and preferences commit never attaches another source data`() =
        runBlocking {
            withDirectory { directory ->
                DesktopStateStore(directory).save(sample)
                val store =
                    DesktopStateStore(directory) { source, target ->
                        if (target.fileName.toString() == "state.json") throw IOException("interrupted")
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                val changed = sample.copy(epgSources = listOf(sample.epgSources.single().copy(url = "https://example.test/other")))
                assertFailsWith<IOException> { store.save(changed) }
                val recovered = DesktopStateStore(directory).load()
                assertTrue(recovered.cacheWarning)
                assertEquals(sample.settings, recovered.state.settings)
                assertTrue(recovered.state.epgProgrammesBySource.isEmpty())
            }
        }

    private suspend fun withDirectory(action: suspend (Path) -> Unit) {
        val directory = Files.createTempDirectory("wukki-desktop-store-test")
        try {
            action(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}

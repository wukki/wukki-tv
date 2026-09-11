package hu.wukki.tv

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidStateStoreTest {
    private val key = stringPreferencesKey("app_state")
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    private val sample =
        AppState(
            lastChannelId = "rtl",
            settings = AppSettings(language = AppLanguage.ENGLISH),
            epgSources = listOf(EpgSource("epg", "EPG", "https://example.test/epg", lastUpdatedAt = 100)),
            epgProgrammesBySource = mapOf("epg" to listOf(Programme("rtl", "News", 100, 200))),
        )

    @Test
    fun `DataStore keeps preferences and migrates embedded cache without blocking load`() =
        runBlocking {
            withStorage { dataStore, file ->
                dataStore.edit { it[key] = json.encodeToString(AppState.serializer(), sample) }
                val store = AndroidStateStore(dataStore, EpgCacheFile(file, json))
                assertEquals(sample, store.load().state)
                assertTrue(file.isFile)
                val lightweight = json.decodeFromString<AppState>(checkNotNull(dataStore.data.first()[key]))
                assertTrue(lightweight.epgProgrammesBySource.isEmpty())
                val bytes = file.readBytes().toList()
                val updated = sample.copy(lastChannelId = "next")
                store.save(updated)
                assertEquals(bytes, file.readBytes().toList())
                assertEquals(updated, AndroidStateStore(dataStore, EpgCacheFile(file, json)).load().state)
            }
        }

    @Test
    fun `invalid preference JSON is reported and preserved for retry`() =
        runBlocking {
            withStorage { dataStore, file ->
                dataStore.edit { it[key] = "broken JSON" }
                val store = AndroidStateStore(dataStore, EpgCacheFile(file, json))
                assertFailsWith<Exception> { store.load() }
                assertEquals("broken JSON", dataStore.data.first()[key])
                dataStore.edit { it[key] = json.encodeToString(AppState.serializer(), sample) }
                assertEquals(sample, store.load().state)
            }
        }

    @Test
    fun `cache failure is observable and corrupt cache retains user preferences`() =
        runBlocking {
            withStorage { dataStore, file ->
                val store = AndroidStateStore(dataStore, EpgCacheFile(file, json))
                store.save(sample)
                file.writeText("invalid gzip")
                val recovered = AndroidStateStore(dataStore, EpgCacheFile(file, json)).load()
                assertTrue(recovered.cacheWarning)
                assertEquals(sample.settings, recovered.state.settings)
                assertEquals(sample.lastChannelId, recovered.state.lastChannelId)
                assertTrue(recovered.state.epgProgrammesBySource.isEmpty())

                val badStore = AndroidStateStore(dataStore, EpgCacheFile(File(file, "invalid-child.gz"), json))
                assertFailsWith<IllegalStateException> { badStore.save(sample.copy(lastChannelId = "unsaved")) }
                assertEquals("rtl", json.decodeFromString<AppState>(checkNotNull(dataStore.data.first()[key])).lastChannelId)
            }
        }

    private suspend fun withStorage(
        action: suspend (androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>, File) -> Unit,
    ) {
        val directory = Files.createTempDirectory("wukki-android-store-test").toFile()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "state.preferences_pb") }
        try {
            action(dataStore, File(directory, "epg_cache.json.gz"))
        } finally {
            scope.cancel()
            job.join()
            directory.deleteRecursively()
        }
    }
}

package hu.wukki.tv

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationBootstrapTest {
    @Test
    fun `concurrent headless callers share one application runtime`() =
        runTest {
            var loads = 0
            val bootstrap =
                ApplicationBootstrap(
                    dependencies(
                        load = {
                            loads++
                            LoadStateResult(AppState())
                        },
                    ),
                    backgroundScope,
                )

            val first = async { bootstrap.awaitReady() }
            val second = async { bootstrap.awaitReady() }

            assertSame(first.await(), second.await())
            assertEquals(1, loads)
        }

    @Test
    fun `worker-only bootstrap refreshes and persists without presentation model`() =
        runTest {
            var persisted =
                AppState(
                    settings = AppSettings(playlistRefresh = RefreshInterval.SIX_HOURS),
                )
            val dispatcher = StandardTestDispatcher(testScheduler)
            val dependencies =
                dependencies(
                    load = { LoadStateResult(persisted) },
                    save = { persisted = it },
                    dispatcher = dispatcher,
                )
            val bootstrap = ApplicationBootstrap(dependencies, backgroundScope)

            val result = bootstrap.runRefresh(BackgroundRefreshType.PLAYLIST)

            assertTrue(result.succeeded)
            assertEquals(null, result.failure)
            assertEquals("One", persisted.channels.single().name)
            assertEquals(
                "Programme",
                persisted.epgProgrammesBySource.values
                    .single()
                    .single()
                    .title,
            )
        }

    private fun dependencies(
        load: suspend () -> LoadStateResult,
        save: suspend (AppState) -> Unit = {},
        dispatcher: CoroutineDispatcher? = null,
    ): WukkiAppDependencies {
        val selectedDispatcher = dispatcher ?: StandardTestDispatcher()
        return WukkiAppDependencies(
            stateStore =
                object : AppStateStore {
                    override suspend fun load() = load()

                    override suspend fun save(state: AppState) = save(state)
                },
            remoteTextLoader =
                RemoteTextLoader { url ->
                    if (url == OfficialWukkiSource.PLAYLIST_URL) {
                        """
                        #EXTM3U url-tvg="https://example.test/epg.xml"
                        #EXTINF:-1 tvg-id="one",One
                        https://example.test/one
                        """.trimIndent()
                    } else {
                        "<tv/>"
                    }
                },
            xmlTvParser = XmlTvParser { listOf(Programme("one", "Programme", 100, 200)) },
            deviceInfoProvider = DeviceInfoProvider { DeviceInfo("test", "1", "test", 0, 0) },
            clock = Clock { 1_000 },
            dispatchers = DispatcherProvider(selectedDispatcher, selectedDispatcher),
        )
    }
}

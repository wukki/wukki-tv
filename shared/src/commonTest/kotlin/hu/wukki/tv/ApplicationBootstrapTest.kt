package hu.wukki.tv

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    @Test
    fun `empty manual cache is refreshed before application becomes ready`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val bootstrap =
                ApplicationBootstrap(
                    dependencies(
                        load = {
                            LoadStateResult(
                                AppState(settings = AppSettings(playlistRefresh = RefreshInterval.MANUAL)),
                            )
                        },
                        dispatcher = dispatcher,
                    ),
                    backgroundScope,
                )

            val runtime = bootstrap.awaitReady()

            assertEquals(
                "One",
                runtime.application.channels.channels
                    .single()
                    .name,
            )
            assertEquals(1_000, runtime.application.channels.playlist.updatedAt)
            assertNull(runtime.initialPlaylistFailure)
        }

    @Test
    fun `failed initial refresh keeps cached channels and only reports an empty cache`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val cachedChannel =
                Channel(
                    id = "cached",
                    playlistId = OfficialWukkiSource.PLAYLIST_ID,
                    name = "Cached",
                    streamUrl = "https://example.test/cached",
                    tvgId = null,
                    tvgName = null,
                    group = "News",
                    logo = null,
                )
            val failingDependencies =
                dependencies(
                    load = {
                        LoadStateResult(
                            AppState(
                                playlists =
                                    listOf(
                                        PlaylistDefinition(
                                            OfficialWukkiSource.PLAYLIST_ID,
                                            OfficialWukkiSource.PLAYLIST_NAME,
                                            OfficialWukkiSource.PLAYLIST_URL,
                                            PlaylistSource.URL,
                                            0,
                                        ),
                                    ),
                                channels = listOf(cachedChannel),
                            ),
                        )
                    },
                    dispatcher = dispatcher,
                    playlistLoader = { throw IllegalStateException("offline") },
                )

            val cached = ApplicationBootstrap(failingDependencies, backgroundScope).awaitReady()
            val empty =
                ApplicationBootstrap(
                    failingDependencies.copy(stateStore = stateStore { LoadStateResult(AppState()) }),
                    backgroundScope,
                ).awaitReady()

            assertEquals(listOf(cachedChannel), cached.application.channels.channels)
            assertNull(cached.initialPlaylistFailure)
            assertTrue(
                empty.application.channels.channels
                    .isEmpty(),
            )
            assertEquals(AppFailure.NetworkUnavailable, empty.initialPlaylistFailure)
        }

    private fun dependencies(
        load: suspend () -> LoadStateResult,
        save: suspend (AppState) -> Unit = {},
        dispatcher: CoroutineDispatcher? = null,
        playlistLoader: (String) -> String = { url ->
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
    ): WukkiAppDependencies {
        val selectedDispatcher = dispatcher ?: StandardTestDispatcher()
        return WukkiAppDependencies(
            stateStore =
                object : AppStateStore {
                    override suspend fun load() = load()

                    override suspend fun save(state: AppState) = save(state)
                },
            remoteTextLoader = RemoteTextLoader { url -> playlistLoader(url) },
            xmlTvParser = XmlTvParser { listOf(Programme("one", "Programme", 100, 200)) },
            deviceInfoProvider = DeviceInfoProvider { DeviceInfo("test", "1", "test", 0, 0) },
            clock = Clock { 1_000 },
            dispatchers = DispatcherProvider(selectedDispatcher, selectedDispatcher),
        )
    }

    private fun stateStore(load: suspend () -> LoadStateResult): AppStateStore =
        object : AppStateStore {
            override suspend fun load() = load()

            override suspend fun save(state: AppState) = Unit
        }
}

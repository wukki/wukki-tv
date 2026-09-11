package hu.wukki.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationUseCasesTest {
    private val playlist = "#EXTM3U url-tvg=\"https://example.test/epg.xml\"\n#EXTINF:-1 tvg-id=\"one\",One\nhttps://example.test/one"
    private val programme = Programme("one", "Programme", 100, 200)

    @Test
    fun `headless refresh uses injected clock and separate download and parser dispatchers`() =
        runTest {
            val io = RecordingDispatcher(StandardTestDispatcher(testScheduler))
            val cpu = RecordingDispatcher(StandardTestDispatcher(testScheduler))
            val events = mutableListOf<RefreshEvent>()
            val saved = mutableListOf<AppState>()
            val app =
                WukkiApplication(
                    ApplicationStore(AppState(), { saved += it }),
                    RemoteTextLoader { if (it == OfficialWukkiSource.PLAYLIST_URL) playlist else "xml" },
                    XmlTvParser { listOf(programme) },
                    clock = Clock { 1234 },
                    dispatchers = DispatcherProvider(io, cpu),
                )
            assertTrue(app.refreshPlaylist(events::add, true))
            assertTrue(io.dispatches >= 2)
            assertTrue(cpu.dispatches >= 1)
            assertEquals(1234L, app.channels.playlist.updatedAt)
            assertEquals(
                1234L,
                app.epg.sources
                    .single()
                    .lastUpdatedAt,
            )
            assertEquals(listOf(programme), app.epg.programmes(app.channels.channels.single()))
            assertEquals(app.store.current, saved.last())
            assertEquals(RefreshEvent.PlaylistLoading, events.first())
            assertEquals(RefreshEvent.PlaylistLoaded(1), events.last())
        }

    @Test
    fun `EPG refresh uses streaming content boundary instead of text parser fallback`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            var contentLoads = 0
            var streamParses = 0
            var closed = false
            val app =
                WukkiApplication(
                    ApplicationStore(AppState(), {}),
                    RemoteTextLoader { playlist },
                    object : XmlTvParser {
                        override fun parse(xml: String): List<Programme> = error("Text fallback must not run")

                        override fun parse(content: RemoteContent): List<Programme> {
                            streamParses++
                            return listOf(programme)
                        }
                    },
                    dispatchers = DispatcherProvider(dispatcher, dispatcher),
                    contentLoader =
                        RemoteContentLoader {
                            contentLoads++
                            object : RemoteContent {
                                override fun read(
                                    buffer: ByteArray,
                                    offset: Int,
                                    length: Int,
                                ): Int = -1

                                override fun close() {
                                    closed = true
                                }
                            }
                        },
                )
            val source = app.epg.synchronize("https://example.test/epg.xml")!!

            assertTrue(app.refreshEpg(source.id, {}, false))

            assertEquals(1, contentLoads)
            assertEquals(1, streamParses)
            assertTrue(closed)
        }

    @Test
    fun `repository mutations share current state and selection does not persist last channel`() {
        val store = ApplicationStore(AppState(), {})
        val channels = StoreChannelRepository(store)
        val settings = UpdateSettings(StoreSettingsRepository(store))
        channels.replace(PlaylistParser.parse(playlist, OfficialWukkiSource.PLAYLIST_ID))
        val id = channels.channels.single().id
        ToggleFavorite(channels)(id)
        settings.playback { it.copy(volume = 999, reconnectAttempts = 0) }
        settings { it.copy(language = AppLanguage.ENGLISH, playlistRefresh = RefreshInterval.TWELVE_HOURS) }
        assertNotNull(SelectChannel(channels)(id))
        assertNull(SelectChannel(channels)("missing"))
        assertNull(channels.lastChannelId)
        channels.markPlaybackSuccessful(id)
        channels.replace(PlaylistParser.parse(playlist, OfficialWukkiSource.PLAYLIST_ID))
        assertTrue(channels.channels.single().favorite)
        assertEquals(id, channels.lastChannelId)
        assertEquals(100, store.current.settings.playback.volume)
        assertEquals(1, store.current.settings.playback.reconnectAttempts)
        assertEquals(AppLanguage.ENGLISH, store.current.settings.language)
        assertEquals(12, store.current.autoRefreshHours)
    }

    @Test
    fun `stale source response is rejected without changing replacement cache or user edits`() {
        val store = ApplicationStore(AppState(), {})
        val repository = StoreEpgRepository(store)
        val old = repository.synchronize("https://example.test/old.xml")!!
        val fresh = repository.synchronize("https://example.test/new.xml")!!
        StoreSettingsRepository(store).update { it.copy(language = AppLanguage.ENGLISH) }
        assertFalse(repository.replace(old, listOf(programme), 123))
        assertEquals(fresh, repository.sources.single())
        assertFalse(repository.hasProgrammes(fresh))
        assertEquals(AppLanguage.ENGLISH, store.current.settings.language)
    }

    @Test
    fun `network errors never become arbitrary localization keys`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val events = mutableListOf<RefreshEvent>()
            val app =
                WukkiApplication(
                    ApplicationStore(AppState(), {}),
                    RemoteTextLoader { error("error.attacker.controlled") },
                    XmlTvParser { emptyList() },
                    dispatchers = DispatcherProvider(dispatcher, dispatcher),
                )
            assertFalse(app.refreshPlaylist(events::add, false))
            val failure = events.single() as RefreshEvent.Failed
            assertEquals(AppFailure.NetworkUnavailable, failure.failure)
            assertTrue(failure.playlistUnavailable)
            assertEquals(UserMessage.Key("error.network.unavailable"), failure.failure.userMessage())
        }

    @Test
    fun `parser failure preserves previously cached EPG with typed feedback`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val events = mutableListOf<RefreshEvent>()
            val app =
                WukkiApplication(
                    ApplicationStore(AppState(), {}),
                    RemoteTextLoader { "bad xml" },
                    XmlTvParser { error("parser details") },
                    dispatchers = DispatcherProvider(dispatcher, dispatcher),
                )
            val source = app.epg.synchronize("https://example.test/epg.xml")!!
            app.epg.replace(source, listOf(programme), 50)
            val before = app.store.current
            assertFalse(app.refreshEpg(source.id, events::add, false))
            assertEquals(before, app.store.current)
            assertEquals(AppFailure.InvalidXmlTv, (events.single() as RefreshEvent.Failed).failure)
        }

    @Test
    fun `network failure wrapped by streaming parser remains retryable`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val events = mutableListOf<RefreshEvent>()
            val app =
                WukkiApplication(
                    ApplicationStore(AppState(), {}),
                    RemoteTextLoader { playlist },
                    object : XmlTvParser {
                        override fun parse(xml: String): List<Programme> = emptyList()

                        override fun parse(content: RemoteContent): List<Programme> =
                            throw IllegalStateException(
                                "SAX wrapper",
                                AppOperationException(AppFailure.NetworkUnavailable),
                            )
                    },
                    dispatchers = DispatcherProvider(dispatcher, dispatcher),
                    contentLoader = RemoteContentLoader { textBackedContentLoader(RemoteTextLoader { "" }).load(it) },
                )
            val source = app.epg.synchronize("https://example.test/epg.xml")!!

            assertFalse(app.refreshEpg(source.id, events::add, false))

            assertEquals(AppFailure.NetworkUnavailable, (events.single() as RefreshEvent.Failed).failure)
        }

    @Test
    fun `cancellation propagates and coordinator can be used again`() =
        runTest {
            var cancelled = true
            val dispatcher = StandardTestDispatcher(testScheduler)
            val events = mutableListOf<RefreshEvent>()
            val app =
                WukkiApplication(
                    ApplicationStore(AppState(), {}),
                    RemoteTextLoader { if (cancelled) throw CancellationException("cancel") else playlist },
                    XmlTvParser { listOf(programme) },
                    dispatchers = DispatcherProvider(dispatcher, dispatcher),
                )
            assertFailsWith<CancellationException> { app.refreshPlaylist(events::add, false) }
            assertTrue(events.isEmpty())
            assertTrue(app.channels.channels.isEmpty())
            cancelled = false
            assertTrue(app.refreshPlaylist(events::add, false))
            assertTrue(events.isEmpty())
        }

    @Test
    fun `injected time controls due checks without network in manual or fresh modes`() =
        runTest {
            var now = 100L
            var downloads = 0
            val dispatcher = StandardTestDispatcher(testScheduler)
            val app =
                WukkiApplication(
                    ApplicationStore(AppState(), {}),
                    RemoteTextLoader {
                        downloads++
                        playlist
                    },
                    XmlTvParser { listOf(programme) },
                    clock = Clock { now },
                    dispatchers = DispatcherProvider(dispatcher, dispatcher),
                )
            assertTrue(app.refreshDuePlaylist({}))
            assertEquals(0, downloads)
            app.updateSettings { it.copy(playlistRefresh = RefreshInterval.SIX_HOURS) }
            assertTrue(app.refreshDuePlaylist({}))
            val afterRefresh = downloads
            assertTrue(app.refreshDuePlaylist({}))
            assertEquals(afterRefresh, downloads)
            now += 6 * 60 * 60 * 1000L
            assertEquals(0L, app.playlistRefreshDelay())
            assertTrue(app.refreshDuePlaylist({}))
            assertEquals(afterRefresh + 1, downloads)
        }

    private class RecordingDispatcher(
        private val delegate: CoroutineDispatcher,
    ) : CoroutineDispatcher() {
        var dispatches = 0

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }
}

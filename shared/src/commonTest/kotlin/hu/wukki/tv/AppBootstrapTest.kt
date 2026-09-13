package hu.wukki.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppBootstrapTest {
    @Test
    fun `slow load leaves UI in loading and concurrent worker shares one model`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            var loads = 0
            val store =
                object : AppStateStore {
                    override suspend fun load(): LoadStateResult {
                        loads++
                        gate.await()
                        return LoadStateResult(AppState(settings = AppSettings(playback = PlaybackSettings(autoPlayOnLaunch = false))))
                    }

                    override suspend fun save(state: AppState) = Unit
                }
            val bootstrap = AppBootstrap(dependencies(store), backgroundScope)
            bootstrap.start()
            runCurrent()
            val worker = async { bootstrap.awaitReady() }
            runCurrent()
            assertIs<BootstrapState.Loading>(bootstrap.state.value)
            assertEquals(1, loads)
            gate.complete(Unit)
            val ready = worker.await()
            assertSame(ready, bootstrap.state.value)
            assertSame(ready.model, bootstrap.awaitReady().model)
            assertEquals(false, ready.model.settings.playback.autoPlayOnLaunch)
        }

    @Test
    fun `corrupt preferences do not create or save an empty model and can be retried`() =
        runTest {
            var failing = true
            var writes = 0
            val store =
                object : AppStateStore {
                    override suspend fun load(): LoadStateResult {
                        if (failing) error("invalid JSON")
                        return LoadStateResult(AppState())
                    }

                    override suspend fun save(state: AppState) {
                        writes++
                    }
                }
            val bootstrap = AppBootstrap(dependencies(store), backgroundScope)
            assertFailsWith<IllegalStateException> { bootstrap.awaitReady() }
            runCurrent()
            assertIs<BootstrapState.Failed>(bootstrap.state.value)
            assertEquals(0, writes)
            failing = false
            bootstrap.awaitReady()
            bootstrap.flush()
            assertIs<BootstrapState.Ready>(bootstrap.state.value)
            assertEquals(1, writes)
        }

    @Test
    fun `failed required playlist load reaches the ready model as localized feedback`() =
        runTest {
            val store =
                object : AppStateStore {
                    override suspend fun load() = LoadStateResult(AppState())

                    override suspend fun save(state: AppState) = Unit
                }
            val bootstrap = AppBootstrap(dependencies(store), backgroundScope)

            val ready = bootstrap.awaitReady()

            assertEquals(UserMessage.Key("error.wukki.playlist.unavailable", listOf(UserMessage.Key("error.network.unavailable"))), ready.model.error)
            assertTrue(ready.model.playlistLoadFailed)
            assertFalse(ready.model.playlistRefreshInProgress)
        }

    private fun dependencies(store: AppStateStore) =
        WukkiAppDependencies(
            stateStore = store,
            remoteTextLoader = RemoteTextLoader { error("Network must not run during bootstrap") },
            xmlTvParser = XmlTvParser { emptyList() },
            deviceInfoProvider = DeviceInfoProvider { DeviceInfo("test", "1", "test", 0, 0) },
        )
}

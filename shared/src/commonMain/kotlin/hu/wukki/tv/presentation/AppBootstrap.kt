package hu.wukki.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface BootstrapState {
    data object Loading : BootstrapState

    data class Ready(
        val model: WukkiModel,
        val writer: StateWriter,
        val cacheWarning: Boolean,
    ) : BootstrapState

    data class Failed(
        val cause: Exception,
    ) : BootstrapState
}

/** A single asynchronous initializer shared by the UI and background refresh callers. */
class AppBootstrap(
    private val dependencies: WukkiAppDependencies,
    private val scope: CoroutineScope,
    private val refreshService: RefreshService? = null,
) {
    private val initialization = Mutex()
    private val mutableState = MutableStateFlow<BootstrapState>(BootstrapState.Loading)
    val state = mutableState.asStateFlow()

    fun start() {
        scope.launch {
            try {
                awaitReady()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // awaitReady exposes the failure to the UI; retries are explicit.
            }
        }
    }

    suspend fun awaitReady(): BootstrapState.Ready =
        initialization.withLock {
            (mutableState.value as? BootstrapState.Ready)?.let { return@withLock it }
            mutableState.value = BootstrapState.Loading
            try {
                val loaded = dependencies.stateStore.load()
                val writer = StateWriter(scope, dependencies.stateStore)
                val model =
                    WukkiModel(
                        loaded.state,
                        dependencies.remoteTextLoader,
                        dependencies.xmlTvParser,
                        writer::submit,
                        refreshService,
                    )
                BootstrapState.Ready(model, writer, loaded.cacheWarning).also { mutableState.value = it }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = BootstrapState.Failed(exception)
                throw exception
            }
        }

    suspend fun flush() {
        (mutableState.value as? BootstrapState.Ready)?.writer?.flush()
    }
}

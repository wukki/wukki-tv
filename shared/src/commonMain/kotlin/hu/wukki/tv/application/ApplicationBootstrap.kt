package hu.wukki.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ApplicationRuntime(
    val application: WukkiApplication,
    val writer: StateWriter,
    val cacheWarning: Boolean,
    val initialPlaylistFailure: AppFailure? = null,
)

/** Initializes the application layer without constructing presentation or Compose state. */
class ApplicationBootstrap(
    private val dependencies: WukkiAppDependencies,
    private val scope: CoroutineScope,
    private val refreshService: RefreshService? = null,
) {
    private val initialization = Mutex()
    private var runtime: ApplicationRuntime? = null

    suspend fun awaitReady(): ApplicationRuntime =
        initialization.withLock {
            runtime?.let { return@withLock it }
            val loaded = dependencies.stateStore.load()
            val writer = StateWriter(scope, dependencies.stateStore)
            val store = ApplicationStore(loaded.state, writer::submit, dependencies.clock)
            val application =
                WukkiApplication(
                    store,
                    dependencies.remoteTextLoader,
                    dependencies.xmlTvParser,
                    refreshService,
                    dependencies.clock,
                    dependencies.dispatchers,
                    dependencies.remoteContentLoader,
                )
            var initialPlaylistFailure: AppFailure? = null
            if (application.channels.channels.isEmpty() || application.channels.playlist.updatedAt == 0L) {
                var refreshFailure: AppFailure? = null
                val refreshed =
                    application.refreshPlaylist(
                        emit = { event ->
                            if (event is RefreshEvent.Failed && event.playlistUnavailable) refreshFailure = event.failure
                        },
                        showFeedback = false,
                    )
                if (!refreshed && application.channels.channels.isEmpty()) {
                    initialPlaylistFailure = refreshFailure ?: AppFailure.Unknown
                }
            }
            ApplicationRuntime(
                application = application,
                writer = writer,
                cacheWarning = loaded.cacheWarning,
                initialPlaylistFailure = initialPlaylistFailure,
            ).also { runtime = it }
        }

    suspend fun flush() {
        runtime?.writer?.flush()
    }

    suspend fun runRefresh(type: BackgroundRefreshType): BackgroundRefreshResult {
        val ready = awaitReady()
        var failure: AppFailure? = null
        val emit: (RefreshEvent) -> Unit = { event ->
            if (event is RefreshEvent.Failed) failure = event.failure
        }
        val succeeded =
            try {
                when (type) {
                    BackgroundRefreshType.PLAYLIST -> {
                        ready.application.refreshDuePlaylist(emit)
                    }

                    BackgroundRefreshType.EPG -> {
                        ready.application.refreshDueEpg(
                            ready.application.settings.settings.epgRefresh,
                            emit,
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        ready.writer.flush()
        return BackgroundRefreshResult(succeeded, failure)
    }
}

enum class BackgroundRefreshType {
    PLAYLIST,
    EPG,
}

data class BackgroundRefreshResult(
    val succeeded: Boolean,
    val failure: AppFailure? = null,
)

package hu.wukki.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One lock for playlist and EPG; an optional process service also joins equal requests. */
class RefreshCoordinator(
    private val service: RefreshService? = null,
) {
    private val lock = Mutex()

    suspend fun run(
        key: String,
        action: suspend () -> Boolean,
    ): Boolean = service?.refresh(key) { lock.withLock { action() } } ?: lock.withLock { action() }
}

class RefreshOfficialEpg(
    private val repository: EpgRepository,
    private val loader: RemoteContentLoader,
    private val parser: XmlTvParser,
    private val coordinator: RefreshCoordinator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
        sourceId: String?,
        emit: (RefreshEvent) -> Unit,
        showFeedback: Boolean,
    ): Boolean = coordinator.run("epg:${sourceId.orEmpty()}") { execute(sourceId, emit, showFeedback) }

    /** Playlist-triggered loading already holds the coordinator lock. */
    internal suspend fun execute(
        sourceId: String?,
        emit: (RefreshEvent) -> Unit,
        showFeedback: Boolean,
    ): Boolean {
        val source = repository.sources.singleOrNull()?.takeIf { it.id == sourceId }
        if (source == null) {
            if (sourceId == null) emit(RefreshEvent.Failed(AppFailure.MissingEpgSource))
            return false
        }
        try {
            if (showFeedback) emit(RefreshEvent.EpgLoading(source.name))
            val programmes =
                loadProgrammes(
                    loader,
                    parser,
                    RemoteTextRequest(source.url, RemoteTextKind.EPG),
                    dispatchers,
                )
            if (!repository.replace(source, programmes, clock.nowMillis())) return false
            if (showFeedback) emit(RefreshEvent.EpgLoaded(source.name, programmes.size))
            return true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            emit(RefreshEvent.Failed(exception.appFailure(), source.name))
            return false
        }
    }
}

class RefreshOfficialPlaylist(
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    private val refreshEpg: RefreshOfficialEpg,
    private val loader: RemoteTextLoader,
    private val coordinator: RefreshCoordinator,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(
        emit: (RefreshEvent) -> Unit,
        showFeedback: Boolean,
    ): Boolean =
        coordinator.run("playlist") {
            try {
                if (showFeedback) emit(RefreshEvent.PlaylistLoading)
                val text = loadText(loader, RemoteTextRequest(OfficialWukkiSource.PLAYLIST_URL, RemoteTextKind.PLAYLIST), dispatchers)
                val parsed =
                    parseContent(dispatchers, AppFailure.InvalidPlaylist) {
                        PlaylistParser.parse(text, OfficialWukkiSource.PLAYLIST_ID)
                    }
                channels.replace(parsed)
                val source = epg.synchronize(PlaylistParser.epgUrl(text))
                val epgReady =
                    when {
                        source == null -> {
                            emit(RefreshEvent.Failed(AppFailure.MissingEpgSource))
                            false
                        }

                        epg.hasProgrammes(source) -> {
                            true
                        }

                        else -> {
                            refreshEpg.execute(source.id, emit, showFeedback)
                        }
                    }
                channels.markRefreshed(clock.nowMillis())
                if (showFeedback && epgReady) emit(RefreshEvent.PlaylistLoaded(parsed.size))
                true
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                emit(RefreshEvent.Failed(exception.appFailure(), playlistUnavailable = channels.channels.isEmpty()))
                false
            }
        }
}

private suspend fun loadText(
    loader: RemoteTextLoader,
    request: RemoteTextRequest,
    dispatchers: DispatcherProvider,
): String =
    withContext(dispatchers.io) {
        try {
            loader.load(request)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AppOperationException) {
            throw exception
        } catch (exception: Exception) {
            throw AppOperationException(AppFailure.NetworkUnavailable, exception)
        }
    }

private suspend fun loadProgrammes(
    loader: RemoteContentLoader,
    parser: XmlTvParser,
    request: RemoteTextRequest,
    dispatchers: DispatcherProvider,
): List<Programme> =
    withContext(dispatchers.io) {
        var content: RemoteContent? = null
        try {
            content = loader.load(request)
            parser.parse(content).also { programmes ->
                if (programmes.isEmpty()) throw AppOperationException(AppFailure.InvalidXmlTv)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: AppOperationException) {
            throw exception
        } catch (exception: Exception) {
            exception.nestedOperationFailure()?.let { throw it }
            val failure = if (content == null) AppFailure.NetworkUnavailable else AppFailure.InvalidXmlTv
            throw AppOperationException(failure, exception)
        } finally {
            content?.close()
        }
    }

private fun Throwable.nestedOperationFailure(): AppOperationException? {
    var current: Throwable? = cause
    while (current != null && current !== this) {
        if (current is AppOperationException) return current
        current = current.cause
    }
    return null
}

private suspend fun <T> parseContent(
    dispatchers: DispatcherProvider,
    failure: AppFailure,
    parse: () -> List<T>,
): List<T> =
    withContext(dispatchers.computation) {
        try {
            parse().also { if (it.isEmpty()) throw AppOperationException(failure) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw AppOperationException(failure, exception)
        }
    }

private fun Exception.appFailure(): AppFailure = (this as? AppOperationException)?.failure ?: AppFailure.Unknown

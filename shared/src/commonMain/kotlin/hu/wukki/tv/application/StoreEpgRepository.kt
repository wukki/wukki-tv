package hu.wukki.tv

class StoreEpgRepository(
    private val store: ApplicationStore,
) : EpgRepository {
    private var indexedSources: Map<String, List<Programme>>? = null
    private var index = ProgrammeIndex(emptyMap())
    private var latestChannels: List<Channel>? = null
    private var latestSources: Map<String, List<Programme>>? = null
    private var latestEnd: Long? = null
    override val sources: List<EpgSource> get() = store.current.epgSources

    override fun synchronize(url: String?): EpgSource? {
        val previous = sources.singleOrNull()
        val sameUrl = previous?.url?.equals(url, ignoreCase = true) == true
        val source =
            url?.let {
                EpgSource(
                    id = OfficialWukkiSource.EPG_SOURCE_ID,
                    name = "${OfficialWukkiSource.PLAYLIST_NAME} EPG",
                    url = it,
                    lastUpdatedAt = previous?.lastUpdatedAt?.takeIf { sameUrl },
                    managedByPlaylist = true,
                )
            }
        store.update { state ->
            val cache =
                when {
                    source == null -> emptyMap()
                    sameUrl -> state.epgProgrammesBySource
                    else -> mapOf(source.id to emptyList())
                }
            state.copy(
                epgSources = listOfNotNull(source),
                epgProgrammesBySource = cache,
                epgUrl = url.orEmpty(),
                programmes = emptyList(),
                channels = EpgMatcher.matchFromSources(state.channels, listOfNotNull(source), cache),
            )
        }
        return source
    }

    override fun replace(
        source: EpgSource,
        programmes: List<Programme>,
        at: Long,
    ): Boolean {
        // Never publish a late response for a source that was replaced while the request was running.
        if (sources.singleOrNull()?.let { it.id == source.id && it.url == source.url } != true) return false
        store.update { state ->
            val updatedSources = listOf(source.copy(lastUpdatedAt = at))
            val cache = mapOf(source.id to programmes)
            state.copy(
                epgSources = updatedSources,
                epgProgrammesBySource = cache,
                programmes = emptyList(),
                epgUrl = source.url,
                channels = EpgMatcher.matchFromSources(state.channels, updatedSources, cache),
            )
        }
        return true
    }

    override fun hasProgrammes(source: EpgSource): Boolean =
        store.current.epgProgrammesBySource[source.id]
            .orEmpty()
            .isNotEmpty()

    override fun programmes(channel: Channel): List<Programme> = currentIndex().programmes(channel)

    override fun latestEnd(channels: List<Channel>): Long? {
        val sources = store.current.epgProgrammesBySource
        if (channels !== latestChannels || sources !== latestSources) {
            latestEnd = currentIndex().latestEnd(channels)
            latestChannels = channels
            latestSources = sources
        }
        return latestEnd
    }

    private fun currentIndex(): ProgrammeIndex {
        val sources = store.current.epgProgrammesBySource
        if (sources !== indexedSources) {
            index = ProgrammeIndex(sources)
            indexedSources = sources
        }
        return index
    }
}

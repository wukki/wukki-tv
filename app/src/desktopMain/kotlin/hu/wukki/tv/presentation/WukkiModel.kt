package hu.wukki.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.URI
import java.util.zip.GZIPInputStream

/** Network boundary used by the fixed Wukki source and injectable in model tests. */
fun interface RemoteTextLoader {
    fun load(url: String): String
}

class WukkiModel(
    initialState: AppState = LocalStore.load(),
    private val sourceLoader: RemoteTextLoader = RemoteTextLoader(::readRemoteText),
    private val stateSaver: (AppState) -> Unit = LocalStore::save
) {
    private val refreshingEpgSourceIds = mutableSetOf<String>()
    private val provisionedState = OfficialWukkiSource.provision(initialState)

    var state by mutableStateOf(provisionedState)
    var selectedPlaylistId by mutableStateOf(OfficialWukkiSource.PLAYLIST_ID)
    var selectedChannelId by mutableStateOf(
        state.lastChannelId?.takeIf { savedId -> state.channels.any { it.id == savedId } }
            ?: state.channels.firstOrNull()?.id
    )
    var query by mutableStateOf("")
    var category by mutableStateOf<String?>(null)
    var onlyFavorites by mutableStateOf(false)
    var status by mutableStateOf<UserMessage?>(null)
    var error by mutableStateOf<UserMessage?>(null)
    /** Increments only for an explicit request to start the selected channel. */
    var playbackRequestToken by mutableIntStateOf(0)
        private set

    init {
        // Persist the one-time migration immediately, before any remote work can fail.
        if (provisionedState != initialState.normalized()) persist()
    }

    val settings: AppSettings get() = state.settings ?: AppSettings()
    val epgSources: List<EpgSource> get() = state.epgSources.orEmpty()
    val officialPlaylist: PlaylistDefinition get() = state.playlists.single()
    val officialEpgSource: EpgSource? get() = epgSources.singleOrNull()
    val hasChannels: Boolean get() = state.channels.isNotEmpty()

    /** For diagnostics that do not have a translation key yet. */
    fun showRawError(message: String) { error = UserMessage.Raw(message); status = null }
    fun setLanguage(language: AppLanguage) = updateSettings { it.copy(language = language) }
    fun setPlaylistRefresh(interval: RefreshInterval) = updateSettings { it.copy(playlistRefresh = interval) }
    fun setEpgRefresh(interval: RefreshInterval) = updateSettings { it.copy(epgRefresh = interval) }
    fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) = updateSettings { it.copy(playback = transform(it.playback)) }
    fun updateDisplay(transform: (DisplaySettings) -> DisplaySettings) = updateSettings { it.copy(display = transform(it.display)) }

    /** Fetches the fixed M3U and updates its single, header-managed EPG source. */
    suspend fun refreshOfficialPlaylist(showFeedback: Boolean = true): Boolean {
        try {
            if (showFeedback) showStatus("status.playlist.refreshing", OfficialWukkiSource.PLAYLIST_NAME)
            val playlistText = withContext(Dispatchers.IO) { sourceLoader.load(OfficialWukkiSource.PLAYLIST_URL) }
            val refreshedChannels = withContext(Dispatchers.Default) {
                PlaylistParser.parse(playlistText, OfficialWukkiSource.PLAYLIST_ID)
            }
            if (refreshedChannels.isEmpty()) throw IllegalArgumentException("error.playlist.empty")

            val previousChannels = state.channels
            val previousSelected = selectedChannelId
            val previousLast = state.lastChannelId
            val channels = refreshedChannels.map { fresh ->
                val previous = previousChannels.firstOrNull { OfficialWukkiSource.sameChannel(it, fresh) }
                fresh.copy(favorite = previous?.favorite == true)
            }
            val restoredLastChannelId = matchingChannelId(previousLast, previousChannels, channels)
            state = state.copy(
                playlists = listOf(officialPlaylist.copy(updatedAt = System.currentTimeMillis())),
                channels = channels,
                lastChannelId = restoredLastChannelId
            )
            selectedPlaylistId = OfficialWukkiSource.PLAYLIST_ID
            selectedChannelId = matchingChannelId(previousSelected, previousChannels, channels)
                ?: restoredLastChannelId
                ?: channels.firstOrNull()?.id
            synchronizeOfficialEpg(playlistText)
            rematchChannels()
            persist()
            if (showFeedback && error == null) showStatus("status.playlist.refreshed", channels.size)
            return true
        } catch (exception: Exception) {
            if (state.channels.isEmpty()) {
                showErrorKey("error.wukki.playlist.unavailable", messageArgument(exception))
            } else if (showFeedback) {
                showErrorKey("error.playlist.refresh", messageArgument(exception))
            }
            return false
        }
    }

    /** Manually refreshes the one EPG URL currently declared by the official M3U. */
    suspend fun refreshOfficialEpg(): Boolean {
        val source = officialEpgSource ?: run {
            showErrorKey("error.wukki.epg.missing")
            return false
        }
        return refreshEpgSource(source.id)
    }

    suspend fun refreshEpgSource(sourceId: String): Boolean {
        val source = officialEpgSource?.takeIf { it.id == sourceId } ?: return false
        if (!refreshingEpgSourceIds.add(source.id)) return false
        try {
            showStatus("status.epg.loading", source.name)
            val xml = withContext(Dispatchers.IO) { sourceLoader.load(source.url) }
            val programmes = withContext(Dispatchers.Default) { EpgParser.parse(xml) }
            if (programmes.isEmpty()) throw IllegalArgumentException("error.epg.empty")
            state = state.copy(
                epgSources = listOf(source.copy(lastUpdatedAt = System.currentTimeMillis())),
                epgProgrammesBySource = mapOf(OfficialWukkiSource.EPG_SOURCE_ID to programmes),
                programmes = programmes,
                epgUrl = source.url
            )
            rematchChannels()
            persist()
            showStatus("status.epg.loaded", programmes.size, source.name)
            return true
        } catch (exception: Exception) {
            // The previous cache deliberately stays intact when the XMLTV download fails.
            showErrorKey("error.epg.load", source.name, messageArgument(exception))
            return false
        } finally {
            refreshingEpgSourceIds.remove(source.id)
        }
    }

    /** Refreshes only the fixed source when it is due according to the user's schedule. */
    suspend fun refreshDueEpgSources(interval: RefreshInterval, now: Long = System.currentTimeMillis()): Boolean {
        if (interval.hours <= 0) return true
        val source = officialEpgSource ?: return true
        return if (source.isEpgRefreshDue(interval, now)) refreshEpgSource(source.id) else true
    }

    fun nextEpgRefreshDelayMillis(interval: RefreshInterval, now: Long = System.currentTimeMillis()): Long =
        nextEpgRefreshDelayMillis(epgSources, interval, now)

    fun toggleFavorite(id: String) {
        state = state.copy(channels = state.channels.map { channel -> if (channel.id == id) channel.copy(favorite = !channel.favorite) else channel })
        persist()
    }

    fun selectChannel(id: String) {
        if (state.channels.none { it.id == id }) return
        selectedChannelId = id
        requestPlayback()
    }

    fun requestPlayback() {
        if (selectedChannel() != null) playbackRequestToken++
    }

    /** Persists only channels that libVLC has confirmed as successfully playing. */
    fun markChannelPlaybackSuccessful(id: String) {
        if (state.lastChannelId == id || state.channels.none { it.id == id }) return
        state = state.copy(lastChannelId = id)
        persist()
    }

    fun selectedChannel(): Channel? = state.channels.firstOrNull { it.id == selectedChannelId }
    fun categories(): List<String> = state.channels.asSequence()
        .map(::channelCategoryName)
        .distinct()
        .sorted()
        .toList()

    fun filteredChannels(): List<Channel> = state.channels.filter { channel ->
        (!onlyFavorites || channel.favorite) &&
            (category == null || channelCategoryName(channel) == category) &&
            (query.isBlank() || normalize(channel.name).contains(normalize(query)))
    }.sortedChannels()

    /** Returns every fixed Wukki channel, independently of the directory filters. */
    fun guideChannels(): List<Channel> = state.channels.sortedChannels()

    /** The continuous guide only spans programmes that can actually be shown for this playlist. */
    fun guideLatestProgrammeEnd(): Long? = guideChannels().asSequence()
        .flatMap { channel -> channelProgrammes(channel).asSequence() }
        .maxOfOrNull { programme -> programme.end }

    fun currentProgram(channel: Channel, now: Long = System.currentTimeMillis()): Programme? =
        channelProgrammes(channel).firstOrNull { now in it.start until it.end }

    fun nextProgram(channel: Channel, current: Programme): Programme? =
        channelProgrammes(channel).firstOrNull { it.start >= current.end }

    /** Returns this channel's programmes that overlap the requested time range. */
    fun programmesFor(channel: Channel, from: Long, to: Long): List<Programme> =
        channelProgrammes(channel).filter { programme -> programme.end > from && programme.start < to }

    fun moveChannel(delta: Int) {
        val channels = filteredChannels()
        if (channels.isEmpty()) return
        val index = channels.indexOfFirst { it.id == selectedChannelId }.let { if (it < 0) 0 else it }
        selectChannel(channels[(index + delta).floorMod(channels.size)].id)
    }

    fun selectChannelByNumber(number: String): Boolean {
        val requestedNumber = number.toIntOrNull()?.takeIf { it > 0 } ?: return false
        val channels = guideChannels()
        val channel = channels.firstOrNull { it.tvgChno == requestedNumber }
            ?: channels.getOrNull(requestedNumber - 1)
            ?: return false
        selectChannel(channel.id)
        return true
    }

    private suspend fun synchronizeOfficialEpg(playlistText: String) {
        val url = PlaylistParser.epgUrl(playlistText)
        if (url == null) {
            state = state.copy(epgSources = emptyList(), epgProgrammesBySource = emptyMap(), programmes = emptyList(), epgUrl = "")
            rematchChannels()
            persist()
            showErrorKey("error.wukki.epg.missing")
            return
        }

        val previous = officialEpgSource
        val sameUrl = previous?.url?.equals(url, ignoreCase = true) == true
        val cachedProgrammes = if (sameUrl) state.epgProgrammesBySource.orEmpty()[OfficialWukkiSource.EPG_SOURCE_ID].orEmpty() else emptyList()
        val source = EpgSource(
            id = OfficialWukkiSource.EPG_SOURCE_ID,
            name = "${OfficialWukkiSource.PLAYLIST_NAME} EPG",
            url = url,
            enabled = true,
            priority = 0,
            lastUpdatedAt = previous?.lastUpdatedAt?.takeIf { sameUrl },
            managedByPlaylist = true
        )
        state = state.copy(
            epgSources = listOf(source),
            epgProgrammesBySource = mapOf(OfficialWukkiSource.EPG_SOURCE_ID to cachedProgrammes),
            programmes = cachedProgrammes,
            epgUrl = url
        )
        rematchChannels()
        persist()
        if (cachedProgrammes.isEmpty()) refreshEpgSource(source.id)
    }

    private fun matchingChannelId(
        previousChannelId: String?,
        previousChannels: List<Channel>,
        refreshedChannels: List<Channel>
    ): String? {
        val previous = previousChannels.firstOrNull { it.id == previousChannelId } ?: return null
        return refreshedChannels.firstOrNull { OfficialWukkiSource.sameChannel(previous, it) }?.id
    }

    private fun rematchChannels() {
        state = state.copy(channels = EpgMatcher.matchFromSources(state.channels, epgSources, state.epgProgrammesBySource.orEmpty()))
    }

    private fun channelProgrammes(channel: Channel): List<Programme> {
        val epgChannelId = channel.epgChannelId ?: return emptyList()
        val programmes = if (channel.epgSourceId == OfficialWukkiSource.EPG_SOURCE_ID) {
            state.epgProgrammesBySource.orEmpty()[OfficialWukkiSource.EPG_SOURCE_ID].orEmpty()
        } else {
            emptyList()
        }
        return programmes.filter { programme -> programme.channelId.equals(epgChannelId, ignoreCase = true) }.sortedBy { it.start }
    }

    private fun channelCategoryName(channel: Channel): String = channel.group.ifBlank { OTHER_CATEGORY_ID }
    private fun showStatus(key: String, vararg args: Any?) { status = UserMessage.Key(key, args.toList()); error = null }
    private fun showErrorKey(key: String, vararg args: Any?) { error = UserMessage.Key(key, args.toList()); status = null }
    private fun messageArgument(exception: Exception): UserMessage = exception.message?.let { message ->
        if (message.startsWith("error.")) UserMessage.Key(message) else UserMessage.Raw(message)
    } ?: UserMessage.Key("error.unknown")
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(settings)
        state = state.copy(settings = updated, autoRefreshHours = updated.playlistRefresh.hours)
        persist()
    }
    private fun persist() = stateSaver(state)
}

const val OTHER_CATEGORY_ID = "__wukki_other__"

sealed interface UserMessage {
    data class Key(val key: String, val arguments: List<Any?> = emptyList()) : UserMessage
    data class Raw(val value: String) : UserMessage
}

internal fun EpgSource.isEpgRefreshDue(interval: RefreshInterval, now: Long): Boolean =
    enabled && interval.hours > 0 && (lastUpdatedAt == null || now - lastUpdatedAt >= interval.hours * 60L * 60L * 1000L)

internal fun nextEpgRefreshDelayMillis(sources: List<EpgSource>, interval: RefreshInterval, now: Long): Long {
    val intervalMillis = interval.hours * 60L * 60L * 1000L
    if (intervalMillis <= 0L) return Long.MAX_VALUE
    val nextDueAt = sources.asSequence().filter { it.enabled }.map { source ->
        (source.lastUpdatedAt ?: now) + intervalMillis
    }.minOrNull() ?: (now + intervalMillis)
    return (nextDueAt - now).coerceAtLeast(0L)
}

private fun List<Channel>.sortedChannels(): List<Channel> =
    sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalize(it.name) })

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

private fun readRemoteText(url: String): String {
    val connection = URI(url).toURL().openConnection().apply {
        connectTimeout = 15_000
        readTimeout = 30_000
    }
    BufferedInputStream(connection.getInputStream()).use { buffered ->
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val decoded = if (gzip) GZIPInputStream(buffered) else buffered
        return decoded.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

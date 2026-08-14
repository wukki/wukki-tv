package hu.wukki.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.GZIPInputStream

class WukkiModel {
    var state by mutableStateOf(LocalStore.load().normalized())
    var selectedPlaylistId by mutableStateOf(state.playlists.firstOrNull()?.id)
    var selectedChannelId by mutableStateOf(
        state.lastChannelId?.takeIf { savedId -> state.channels.any { it.id == savedId } } ?: state.channels.firstOrNull()?.id
    )
    var query by mutableStateOf("")
    var category by mutableStateOf<String?>(null)
    var onlyFavorites by mutableStateOf(false)
    var status by mutableStateOf<UserMessage?>(null)
    var error by mutableStateOf<UserMessage?>(null)
    /** Increments only for an explicit request to start the selected channel. */
    var playbackRequestToken by mutableIntStateOf(0)
        private set

    val settings: AppSettings get() = state.settings ?: AppSettings()
    val epgSources: List<EpgSource> get() = state.epgSources.orEmpty().sortedBy { it.priority }

    /** For diagnostics that do not have a translation key yet. */
    fun showRawError(message: String) { error = UserMessage.Raw(message); status = null }
    fun setLanguage(language: AppLanguage) = updateSettings { it.copy(language = language) }
    fun setPlaylistRefresh(interval: RefreshInterval) = updateSettings { it.copy(playlistRefresh = interval) }
    fun setEpgRefresh(interval: RefreshInterval) = updateSettings { it.copy(epgRefresh = interval) }
    fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) = updateSettings { it.copy(playback = transform(it.playback)) }
    fun updateDisplay(transform: (DisplaySettings) -> DisplaySettings) = updateSettings { it.copy(display = transform(it.display)) }

    suspend fun addPlaylistFromUrl(url: String) = loadPlaylist(playlistName(url), url, PlaylistSource.URL)
    suspend fun addPlaylistFromFile(file: File) = loadPlaylist(file.nameWithoutExtension.ifBlank { file.name }, file.absolutePath, PlaylistSource.FILE)

    suspend fun refreshSelected() {
        val playlist = state.playlists.firstOrNull { it.id == selectedPlaylistId } ?: return
        refreshPlaylist(playlist, showFeedback = true)
    }

    fun refreshAllPlaylists(scope: CoroutineScope) {
        state.playlists.filter { it.source == PlaylistSource.URL }.forEach { playlist -> scope.launch { refreshPlaylist(playlist, showFeedback = false) } }
    }

    suspend fun importEpg(url: String) = addEpgSource(sourceName(url), url, managedByPlaylist = false)

    suspend fun addEpgSource(name: String, url: String, managedByPlaylist: Boolean = false) {
        val normalizedUrl = url.trim()
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            showErrorKey("error.epg.url")
            return
        }
        val existing = epgSources.firstOrNull { it.url.equals(normalizedUrl, ignoreCase = true) }
        val source = existing ?: EpgSource(UUID.randomUUID().toString(), name.ifBlank { sourceName(normalizedUrl) }, normalizedUrl, priority = epgSources.size, managedByPlaylist = managedByPlaylist)
        if (existing == null) {
            state = state.copy(epgSources = state.epgSources.orEmpty() + source)
            persist()
        }
        refreshEpgSource(source.id)
    }

    suspend fun refreshEpgSource(sourceId: String) {
        val source = epgSources.firstOrNull { it.id == sourceId } ?: return
        try {
            showStatus("status.epg.loading", source.name)
            val xml = withContext(Dispatchers.IO) { readLocation(source.url, PlaylistSource.URL) }
            val programmes = withContext(Dispatchers.Default) { EpgParser.parse(xml) }
            if (programmes.isEmpty()) throw IllegalArgumentException("error.epg.empty")
            val cache = state.epgProgrammesBySource.orEmpty().toMutableMap().apply { put(source.id, programmes) }
            val updatedSources = state.epgSources.orEmpty().map { if (it.id == source.id) it.copy(lastUpdatedAt = System.currentTimeMillis()) else it }
            state = state.copy(epgSources = updatedSources, epgProgrammesBySource = cache, programmes = programmes, epgUrl = source.url)
            rematchChannels()
            persist()
            showStatus("status.epg.loaded", programmes.size, source.name)
        } catch (exception: Exception) {
            showErrorKey("error.epg.load", source.name, messageArgument(exception))
        }
    }

    fun refreshAllEpg(scope: CoroutineScope) {
        epgSources.filter { it.enabled }.forEach { source -> scope.launch { refreshEpgSource(source.id) } }
    }

    fun renameEpgSource(id: String, name: String) = updateEpgSource(id) { it.copy(name = name.trim().ifBlank { it.name }) }
    fun setEpgSourceEnabled(id: String, enabled: Boolean) = updateEpgSource(id) { it.copy(enabled = enabled) }
    fun removeEpgSource(id: String) {
        state = state.copy(
            epgSources = state.epgSources.orEmpty().filterNot { it.id == id }.withPriorities(),
            epgProgrammesBySource = state.epgProgrammesBySource.orEmpty() - id
        )
        rematchChannels()
        persist()
    }
    fun moveEpgSource(id: String, direction: Int) {
        val ordered = epgSources.toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target !in ordered.indices) return
        val item = ordered.removeAt(index)
        ordered.add(target, item)
        state = state.copy(epgSources = ordered.withPriorities())
        rematchChannels()
        persist()
    }

    fun renamePlaylist(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        state = state.copy(playlists = state.playlists.map { if (it.id == id) it.copy(name = trimmed) else it })
        persist()
    }

    fun removeSelectedPlaylist() {
        val playlistId = selectedPlaylistId ?: return
        state = state.copy(playlists = state.playlists.filterNot { it.id == playlistId }, channels = state.channels.filterNot { it.playlistId == playlistId })
        selectedPlaylistId = state.playlists.firstOrNull()?.id
        selectedChannelId = state.channels.firstOrNull()?.id
        persist()
        showStatus("status.playlist.removed")
    }

    /** Compatibility bridge for the first dashboard version. */
    fun setAutoRefresh(hours: Int) = setPlaylistRefresh(RefreshInterval.entries.first { it.hours == hours })
    fun toggleFavorite(id: String) { state = state.copy(channels = state.channels.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }); persist() }
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
        .filter { selectedPlaylistId == null || it.playlistId == selectedPlaylistId }
        .map(::channelCategoryName)
        .distinct()
        .sorted()
        .toList()
    fun filteredChannels(): List<Channel> = state.channels.filter { channel ->
        (selectedPlaylistId == null || channel.playlistId == selectedPlaylistId) && (!onlyFavorites || channel.favorite) &&
            (category == null || channelCategoryName(channel) == category) && (query.isBlank() || normalize(channel.name).contains(normalize(query)))
    }.sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalize(it.name) })

    /** Returns every channel from the active playlist, independently of the channel directory filters. */
    fun guideChannels(): List<Channel> = state.channels.filter { channel ->
        selectedPlaylistId == null || channel.playlistId == selectedPlaylistId
    }.sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalize(it.name) })

    fun currentProgram(channel: Channel, now: Long = System.currentTimeMillis()): Programme? = channelProgrammes(channel).firstOrNull { now in it.start until it.end }
    fun nextProgram(channel: Channel, current: Programme): Programme? = channelProgrammes(channel).firstOrNull { it.start >= current.end }

    /** Returns this channel's programmes that overlap the requested time range. */
    fun programmesFor(channel: Channel, from: Long, to: Long): List<Programme> =
        channelProgrammes(channel).filter { programme -> programme.end > from && programme.start < to }

    fun moveChannel(delta: Int) {
        val channels = filteredChannels(); if (channels.isEmpty()) return
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
    private suspend fun loadPlaylist(name: String, location: String, source: PlaylistSource) {
        try {
            showStatus("status.playlist.loading")
            val text = withContext(Dispatchers.IO) { readLocation(location, source) }
            val playlistId = UUID.randomUUID().toString()
            val channels = PlaylistParser.parse(text, playlistId)
            if (channels.isEmpty()) throw IllegalArgumentException("error.playlist.empty")
            state = state.copy(
                playlists = state.playlists + PlaylistDefinition(playlistId, name, location, source, System.currentTimeMillis()),
                channels = state.channels + channels
            )
            selectedPlaylistId = playlistId; selectedChannelId = channels.first().id
            ensurePlaylistEpg(text, name)
            rematchChannels(); persist()
            showStatus("status.playlist.loaded", channels.size, name)
        } catch (exception: Exception) { showErrorKey("error.playlist.load", messageArgument(exception)) }
    }

    private suspend fun refreshPlaylist(playlist: PlaylistDefinition, showFeedback: Boolean) {
        try {
            if (showFeedback) showStatus("status.playlist.refreshing", playlist.name)
            val text = withContext(Dispatchers.IO) { readLocation(playlist.location, playlist.source) }
            val refreshed = PlaylistParser.parse(text, playlist.id)
            state = state.copy(
                playlists = state.playlists.map { if (it.id == playlist.id) it.copy(updatedAt = System.currentTimeMillis()) else it },
                channels = state.channels.filterNot { it.playlistId == playlist.id } + refreshed
            )
            ensurePlaylistEpg(text, playlist.name)
            rematchChannels(); persist()
            if (showFeedback) showStatus("status.playlist.refreshed", refreshed.size)
        } catch (exception: Exception) { if (showFeedback) showErrorKey("error.playlist.refresh", messageArgument(exception)) }
    }

    private suspend fun ensurePlaylistEpg(playlistText: String, playlistName: String) {
        val url = PlaylistParser.epgUrl(playlistText) ?: return
        val source = epgSources.firstOrNull { it.url.equals(url, true) } ?: EpgSource(UUID.randomUUID().toString(), "$playlistName EPG", url, priority = epgSources.size, managedByPlaylist = true).also {
            state = state.copy(epgSources = state.epgSources.orEmpty() + it)
        }
        if (state.epgProgrammesBySource.orEmpty()[source.id].isNullOrEmpty()) refreshEpgSource(source.id)
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(settings)
        state = state.copy(settings = updated, autoRefreshHours = updated.playlistRefresh.hours)
        persist()
    }
    private fun updateEpgSource(id: String, transform: (EpgSource) -> EpgSource) {
        state = state.copy(epgSources = state.epgSources.orEmpty().map { if (it.id == id) transform(it) else it })
        rematchChannels(); persist()
    }
    private fun rematchChannels() {
        state = state.copy(channels = EpgMatcher.matchFromSources(state.channels, epgSources, state.epgProgrammesBySource.orEmpty()))
    }
    private fun channelProgrammes(channel: Channel): List<Programme> {
        val epgChannelId = channel.epgChannelId ?: return emptyList()
        val sourceProgrammes = channel.epgSourceId?.let { sourceId -> state.epgProgrammesBySource.orEmpty()[sourceId] }
        val programmes = if (sourceProgrammes != null) {
            sourceProgrammes.filter { programme -> programme.channelId.equals(epgChannelId, ignoreCase = true) }
        } else {
            // Compatibility path for state saved before multiple EPG sources were introduced.
            state.programmes.filter { programme -> programme.channelId.equals(epgChannelId, ignoreCase = true) }
        }
        return programmes.sortedBy { it.start }
    }
    private fun channelCategoryName(channel: Channel): String = channel.group.ifBlank { OTHER_CATEGORY_ID }
    private fun showStatus(key: String, vararg args: Any?) { status = UserMessage.Key(key, args.toList()); error = null }
    private fun showErrorKey(key: String, vararg args: Any?) { error = UserMessage.Key(key, args.toList()); status = null }
    private fun messageArgument(exception: Exception): UserMessage = exception.message?.let { message ->
        if (message.startsWith("error.")) UserMessage.Key(message) else UserMessage.Raw(message)
    } ?: UserMessage.Key("error.unknown")
    private fun persist() = LocalStore.save(state)
}

const val OTHER_CATEGORY_ID = "__wukki_other__"

sealed interface UserMessage {
    data class Key(val key: String, val arguments: List<Any?> = emptyList()) : UserMessage
    data class Raw(val value: String) : UserMessage
}

private fun List<EpgSource>.withPriorities(): List<EpgSource> = mapIndexed { index, source -> source.copy(priority = index) }
private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
private fun playlistName(url: String): String = runCatching { URI(url).host.removePrefix("www.").ifBlank { url } }.getOrDefault(url)
private fun sourceName(url: String): String = playlistName(url)
private fun readLocation(location: String, source: PlaylistSource): String {
    val input = when (source) {
        PlaylistSource.FILE -> Files.newInputStream(Path.of(location))
        PlaylistSource.URL -> URI(location).toURL().openConnection().apply { connectTimeout = 15_000; readTimeout = 30_000 }.getInputStream()
    }
    BufferedInputStream(input).use { buffered ->
        buffered.mark(2); val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b; buffered.reset()
        val decoded = if (gzip) GZIPInputStream(buffered) else buffered
        return decoded.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

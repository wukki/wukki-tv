package hu.wukki.tv

import androidx.compose.runtime.getValue
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
    var status by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    val settings: AppSettings get() = state.settings ?: AppSettings()
    val epgSources: List<EpgSource> get() = state.epgSources.orEmpty().sortedBy { it.priority }

    private var numberBuffer = ""
    private var lastNumberInput = 0L

    fun showError(message: String) { error = message; status = null }
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
            showError("Az EPG URL-nek http:// vagy https:// címmel kell kezdődnie.")
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
            showStatus("EPG frissítése: ${source.name}…")
            val xml = withContext(Dispatchers.IO) { readLocation(source.url, PlaylistSource.URL) }
            val programmes = withContext(Dispatchers.Default) { EpgParser.parse(xml) }
            if (programmes.isEmpty()) throw IllegalArgumentException("Az XMLTV fájl nem tartalmaz feldolgozható műsort.")
            val cache = state.epgProgrammesBySource.orEmpty().toMutableMap().apply { put(source.id, programmes) }
            val updatedSources = state.epgSources.orEmpty().map { if (it.id == source.id) it.copy(lastUpdatedAt = System.currentTimeMillis()) else it }
            state = state.copy(epgSources = updatedSources, epgProgrammesBySource = cache, programmes = programmes, epgUrl = source.url)
            rematchChannels()
            persist()
            showStatus("${programmes.size} EPG műsor betöltve: ${source.name}")
        } catch (exception: Exception) {
            showError("Az EPG nem tölthető be (${source.name}): ${exception.message ?: "ismeretlen hiba"}")
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
        showStatus("Playlist eltávolítva.")
    }

    /** Compatibility bridge for the first dashboard version. */
    fun setAutoRefresh(hours: Int) = setPlaylistRefresh(RefreshInterval.entries.first { it.hours == hours })
    fun toggleFavorite(id: String) { state = state.copy(channels = state.channels.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }); persist() }
    fun selectChannel(id: String) {
        if (state.channels.none { it.id == id }) return
        selectedChannelId = id
        state = state.copy(lastChannelId = id)
        persist()
    }
    fun selectedChannel(): Channel? = state.channels.firstOrNull { it.id == selectedChannelId }
    fun categories(): List<String> = state.channels.map { it.group.ifBlank { "Egyéb" } }.distinct().sorted()
    fun filteredChannels(): List<Channel> = state.channels.filter { channel ->
        (selectedPlaylistId == null || channel.playlistId == selectedPlaylistId) && (!onlyFavorites || channel.favorite) &&
            (category == null || channel.group == category) && (query.isBlank() || normalize(channel.name).contains(normalize(query)))
    }.sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalize(it.name) })

    fun currentProgram(channel: Channel, now: Long = System.currentTimeMillis()): Programme? = programmesFor(channel).firstOrNull { now in it.start until it.end }
    fun nextProgram(channel: Channel, current: Programme): Programme? = programmesFor(channel).firstOrNull { it.start >= current.end }

    fun moveChannel(delta: Int) {
        val channels = filteredChannels(); if (channels.isEmpty()) return
        val index = channels.indexOfFirst { it.id == selectedChannelId }.let { if (it < 0) 0 else it }
        selectChannel(channels[(index + delta).floorMod(channels.size)].id)
    }
    fun selectChannelByNumber(digit: String) {
        val now = System.currentTimeMillis()
        numberBuffer = if (now - lastNumberInput > 1300) digit else (numberBuffer + digit).takeLast(3)
        lastNumberInput = now
        val index = numberBuffer.toIntOrNull()?.minus(1) ?: return
        filteredChannels().getOrNull(index)?.let { selectChannel(it.id); showStatus("${numberBuffer}. ${it.name}") }
    }
    private suspend fun loadPlaylist(name: String, location: String, source: PlaylistSource) {
        try {
            showStatus("Playlist betöltése…")
            val text = withContext(Dispatchers.IO) { readLocation(location, source) }
            val playlistId = UUID.randomUUID().toString()
            val channels = PlaylistParser.parse(text, playlistId)
            if (channels.isEmpty()) throw IllegalArgumentException("Az M3U fájlban nem találtam lejátszható csatornát.")
            state = state.copy(
                playlists = state.playlists + PlaylistDefinition(playlistId, name, location, source, System.currentTimeMillis()),
                channels = state.channels + channels
            )
            selectedPlaylistId = playlistId; selectedChannelId = channels.first().id
            ensurePlaylistEpg(text, name)
            rematchChannels(); persist()
            showStatus("${channels.size} csatorna betöltve: $name")
        } catch (exception: Exception) { showError("A playlist nem tölthető be: ${exception.message ?: "ismeretlen hiba"}") }
    }

    private suspend fun refreshPlaylist(playlist: PlaylistDefinition, showFeedback: Boolean) {
        try {
            if (showFeedback) showStatus("${playlist.name} frissítése…")
            val text = withContext(Dispatchers.IO) { readLocation(playlist.location, playlist.source) }
            val refreshed = PlaylistParser.parse(text, playlist.id)
            state = state.copy(
                playlists = state.playlists.map { if (it.id == playlist.id) it.copy(updatedAt = System.currentTimeMillis()) else it },
                channels = state.channels.filterNot { it.playlistId == playlist.id } + refreshed
            )
            ensurePlaylistEpg(text, playlist.name)
            rematchChannels(); persist()
            if (showFeedback) showStatus("${refreshed.size} csatorna frissítve.")
        } catch (exception: Exception) { if (showFeedback) showError("A frissítés sikertelen: ${exception.message ?: "ismeretlen hiba"}") }
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
    private fun programmesFor(channel: Channel): List<Programme> = state.epgProgrammesBySource.orEmpty()[channel.epgSourceId].orEmpty().ifEmpty { state.programmes.filter { it.channelId == channel.epgChannelId } }
    private fun showStatus(message: String) { status = message; error = null }
    private fun persist() = LocalStore.save(state)
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

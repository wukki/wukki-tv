package hu.wukki.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.BufferedInputStream
import java.util.zip.GZIPInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class WukkiModel {
    var state by mutableStateOf(LocalStore.load())
    var selectedPlaylistId by mutableStateOf(state.playlists.firstOrNull()?.id)
    var selectedChannelId by mutableStateOf(state.lastChannelId ?: state.channels.firstOrNull()?.id)
    var query by mutableStateOf("")
    var category by mutableStateOf<String?>(null)
    var onlyFavorites by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    private var numberBuffer = ""
    private var lastNumberInput = 0L

    fun showError(message: String) {
        error = message
        status = null
    }

    suspend fun addPlaylistFromUrl(url: String) = loadPlaylist(playlistName(url), url, PlaylistSource.URL)
    suspend fun addPlaylistFromFile(file: File) = loadPlaylist(file.nameWithoutExtension.ifBlank { file.name }, file.absolutePath, PlaylistSource.FILE)

    suspend fun refreshSelected() {
        val playlist = state.playlists.firstOrNull { it.id == selectedPlaylistId } ?: return
        try {
            showStatus("${playlist.name} frissítése…")
            val text = withContext(Dispatchers.IO) { readLocation(playlist.location, playlist.source) }
            val refreshed = EpgMatcher.match(PlaylistParser.parse(text, playlist.id), state.programmes)
            state = state.copy(
                playlists = state.playlists.map { if (it.id == playlist.id) it.copy(updatedAt = System.currentTimeMillis()) else it },
                channels = state.channels.filterNot { it.playlistId == playlist.id } + refreshed
            )
            if (selectedChannelId !in state.channels.map { it.id }) selectedChannelId = refreshed.firstOrNull()?.id
            persist()
            val importedProgrammes = PlaylistParser.epgUrl(text)?.let { importEpgSilently(it) }
            showStatus(buildRefreshStatus(refreshed.size, importedProgrammes))
        } catch (exception: Exception) {
            showError("A frissítés sikertelen: ${exception.message ?: "ismeretlen hiba"}")
        }
    }

    fun refreshAll(scope: CoroutineScope) {
        state.playlists.filter { it.source == PlaylistSource.URL }.forEach { playlist ->
            scope.launch { refresh(playlist) }
        }
    }

    suspend fun importEpg(url: String) {
        try {
            showStatus("XMLTV betöltése…")
            val programmes = loadEpg(url)
            showStatus("${programmes.size} EPG műsor betöltve és párosítva.")
        } catch (exception: Exception) {
            showError("Az EPG nem tölthető be: ${exception.message ?: "ismeretlen hiba"}")
        }
    }

    fun removeSelectedPlaylist() {
        val playlistId = selectedPlaylistId ?: return
        state = state.copy(
            playlists = state.playlists.filterNot { it.id == playlistId },
            channels = state.channels.filterNot { it.playlistId == playlistId }
        )
        selectedPlaylistId = state.playlists.firstOrNull()?.id
        selectedChannelId = state.channels.firstOrNull()?.id
        persist()
        showStatus("Playlist eltávolítva.")
    }

    fun setAutoRefresh(hours: Int) { state = state.copy(autoRefreshHours = hours); persist() }
    fun toggleFavorite(id: String) { state = state.copy(channels = state.channels.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }); persist() }
    fun selectChannel(id: String) { selectedChannelId = id; state = state.copy(lastChannelId = id); persist() }
    fun selectedChannel(): Channel? = state.channels.firstOrNull { it.id == selectedChannelId }
    fun categories(): List<String> = state.channels.map { it.group.ifBlank { "Egyéb" } }.distinct().sorted()
    fun filteredChannels(): List<Channel> = state.channels.filter { channel ->
        (selectedPlaylistId == null || channel.playlistId == selectedPlaylistId) &&
            (!onlyFavorites || channel.favorite) &&
            (category == null || channel.group == category) &&
            (query.isBlank() || normalize(channel.name).contains(normalize(query)))
    }.sortedWith(compareBy<Channel> { it.tvgChno ?: Int.MAX_VALUE }.thenBy { normalize(it.name) })
    fun currentProgram(channel: Channel, now: Long = System.currentTimeMillis()): Programme? = state.programmes.firstOrNull { it.channelId == channel.epgChannelId && now in it.start until it.end }
    fun nextProgram(channel: Channel, current: Programme): Programme? = state.programmes.firstOrNull { it.channelId == channel.epgChannelId && it.start >= current.end }

    fun moveChannel(delta: Int) {
        val channels = filteredChannels()
        if (channels.isEmpty()) return
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

    fun openSelectedStream() {
        val channel = selectedChannel() ?: return
        try {
            Desktop.getDesktop().browse(URI(channel.streamUrl))
            showStatus("Stream megnyitva: ${channel.name}")
        } catch (exception: Exception) {
            showError("A stream nem nyitható meg: ${exception.message}")
        }
    }

    private suspend fun loadPlaylist(name: String, location: String, source: PlaylistSource) {
        try {
            showStatus("Playlist betöltése…")
            val text = withContext(Dispatchers.IO) { readLocation(location, source) }
            val playlistId = UUID.randomUUID().toString()
            val channels = PlaylistParser.parse(text, playlistId)
            if (channels.isEmpty()) throw IllegalArgumentException("Az M3U fájlban nem találtam lejátszható csatornát.")
            val playlist = PlaylistDefinition(playlistId, name, location, source, System.currentTimeMillis())
            state = state.copy(playlists = state.playlists + playlist, channels = state.channels + EpgMatcher.match(channels, state.programmes))
            selectedPlaylistId = playlistId
            selectedChannelId = channels.first().id
            persist()
            val importedProgrammes = PlaylistParser.epgUrl(text)?.let { importEpgSilently(it) }
            showStatus(buildImportStatus(channels.size, name, importedProgrammes))
        } catch (exception: Exception) {
            showError("A playlist nem tölthető be: ${exception.message ?: "ismeretlen hiba"}")
        }
    }

    private suspend fun refresh(playlist: PlaylistDefinition) {
        try {
            val text = withContext(Dispatchers.IO) { readLocation(playlist.location, playlist.source) }
            val refreshed = EpgMatcher.match(PlaylistParser.parse(text, playlist.id), state.programmes)
            state = state.copy(
                playlists = state.playlists.map { if (it.id == playlist.id) it.copy(updatedAt = System.currentTimeMillis()) else it },
                channels = state.channels.filterNot { it.playlistId == playlist.id } + refreshed
            )
            PlaylistParser.epgUrl(text)?.let { importEpgSilently(it) }
            persist()
        } catch (_: Exception) {
            // A következő ütemezett ciklus újra próbálkozik.
        }
    }

    private fun showStatus(message: String) { status = message; error = null }
    private fun persist() = LocalStore.save(state)

    private suspend fun loadEpg(url: String): List<Programme> {
        val xml = withContext(Dispatchers.IO) { readLocation(url, PlaylistSource.URL) }
        val programmes = withContext(Dispatchers.Default) { EpgParser.parse(xml) }
        if (programmes.isEmpty()) throw IllegalArgumentException("Az XMLTV fájl nem tartalmaz feldolgozható műsort.")
        state = state.copy(epgUrl = url, programmes = programmes, channels = EpgMatcher.match(state.channels, programmes))
        persist()
        return programmes
    }

    private suspend fun importEpgSilently(url: String): Int? = try {
        loadEpg(url).size
    } catch (_: Exception) {
        null
    }

    private fun buildImportStatus(channelCount: Int, playlistName: String, programmeCount: Int?): String = when (programmeCount) {
        null -> "$channelCount csatorna betöltve: $playlistName"
        else -> "$channelCount csatorna és $programmeCount EPG műsor betöltve: $playlistName"
    }

    private fun buildRefreshStatus(channelCount: Int, programmeCount: Int?): String = when (programmeCount) {
        null -> "$channelCount csatorna frissítve."
        else -> "$channelCount csatorna és $programmeCount EPG műsor frissítve."
    }
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
private fun playlistName(url: String): String = runCatching { URI(url).host.removePrefix("www.").ifBlank { url } }.getOrDefault(url)
private fun readLocation(location: String, source: PlaylistSource): String {
    val input = when (source) {
        PlaylistSource.FILE -> Files.newInputStream(Path.of(location))
        PlaylistSource.URL -> URI(location).toURL().openConnection().apply { connectTimeout = 15_000; readTimeout = 30_000 }.getInputStream()
    }
    BufferedInputStream(input).use { buffered ->
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val decoded = if (gzip) GZIPInputStream(buffered) else buffered
        return decoded.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

package hu.wukki.tv

import java.io.Serializable

enum class PlaylistSource { URL, FILE }

data class PlaylistDefinition(
    val id: String,
    val name: String,
    val location: String,
    val source: PlaylistSource,
    val updatedAt: Long
) : Serializable

data class Channel(
    val id: String,
    val playlistId: String,
    val name: String,
    val streamUrl: String,
    val tvgId: String?,
    val tvgName: String?,
    val tvgChno: Int? = null,
    val group: String,
    val logo: String?,
    val favorite: Boolean = false,
    val epgChannelId: String? = null
) : Serializable {
    companion object {
        /** Preserves compatibility with playlists saved before `tvg-chno` was added. */
        @JvmField
        val serialVersionUID: Long = -1321689634413548830L
    }
}

data class Programme(
    val channelId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val description: String? = null
) : Serializable

data class AppState(
    val playlists: List<PlaylistDefinition> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val epgUrl: String = "",
    val autoRefreshHours: Int = 0,
    val lastChannelId: String? = null
) : Serializable

package hu.wukki.tv

/** Capabilities and selection read from the currently loaded media, never persisted. */
data class PlaybackQuickSettings(
    val aspect: AspectRatioMode? = null,
    val audio: List<PlaybackTrack> = emptyList(),
    val subtitles: List<PlaybackTrack> = emptyList(),
)

data class PlaybackTrack(
    val id: String,
    val name: String?,
    val selected: Boolean,
    val supported: Boolean = true,
)

enum class QuickSetting { ASPECT, AUDIO, SUBTITLES }

fun PlaybackQuickSettings.availableSettings(): List<QuickSetting> =
    buildList {
        if (aspect != null) add(QuickSetting.ASPECT)
        if (audio.size > 1) add(QuickSetting.AUDIO)
        if (subtitles.any { it.id != "off" }) add(QuickSetting.SUBTITLES)
    }

fun PlaybackQuickSettings.nextTrack(
    setting: QuickSetting,
    delta: Int,
): PlaybackTrack? {
    val tracks = if (setting == QuickSetting.AUDIO) audio else subtitles
    if (tracks.isEmpty()) return null
    val current = tracks.indexOfFirst { it.selected }.coerceAtLeast(0)
    return tracks[(current + delta + tracks.size) % tracks.size]
}

/** Native APIs differ in whether they include an explicit disabled subtitle track. */
fun selectablePlaybackTracks(
    tracks: List<PlaybackTrack>,
    setting: QuickSetting,
): List<PlaybackTrack> {
    val available = tracks.filter { it.supported && it.id != "off" }
    if (setting != QuickSetting.SUBTITLES || available.isEmpty()) return available
    return listOf(PlaybackTrack("off", null, available.none { it.selected })) + available
}

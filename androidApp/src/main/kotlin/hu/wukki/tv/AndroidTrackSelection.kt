package hu.wukki.tv

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride

internal fun Player.playbackTracks(setting: QuickSetting): List<PlaybackTrack> {
    val type = if (setting == QuickSetting.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
    val tracks =
        currentTracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).map { index ->
                val format = group.getTrackFormat(index)
                PlaybackTrack("${group.mediaTrackGroup.id}:$index", format.label ?: format.language, group.isTrackSelected(index), group.isTrackSupported(index))
            }
        }
    return selectablePlaybackTracks(tracks, setting)
}

internal fun Player.selectPlaybackTrack(
    setting: QuickSetting,
    id: String,
) {
    val type = if (setting == QuickSetting.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
    val builder = trackSelectionParameters.buildUpon()
    if (setting == QuickSetting.SUBTITLES && id == "off") {
        trackSelectionParameters = builder.clearOverridesOfType(type).setTrackTypeDisabled(type, true).build()
        return
    }
    currentTracks.groups.filter { it.type == type }.forEach { group ->
        (0 until group.length).firstOrNull { group.isTrackSupported(it) && "${group.mediaTrackGroup.id}:$it" == id }?.let { index ->
            trackSelectionParameters =
                builder
                    .setTrackTypeDisabled(type, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
            return
        }
    }
}

package hu.wukki.tv.ui.app

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.PlaybackOverlayData
import hu.wukki.tv.PlaybackBufferingOverlay
import hu.wukki.tv.PlaybackProgrammeOverlay
import hu.wukki.tv.PlaybackState
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.components.displayTitle
import hu.wukki.tv.ui.components.displayName
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.navigation.DashboardSection

internal fun playbackOverlayData(
    channel: Channel,
    currentProgramme: Programme?,
    nextProgramme: Programme?,
    now: Long,
    section: DashboardSection,
    showProgrammeInfo: Boolean,
    channelNumberInput: String,
    language: AppLanguage,
    showLogos: Boolean,
    showProgrammeImages: Boolean,
    playbackState: PlaybackState,
    playbackDetail: String?
): PlaybackOverlayData {
    val playbackStatus = when (playbackState) {
        PlaybackState.IDLE, PlaybackState.PLAYING, PlaybackState.BUFFERING -> null
        PlaybackState.OPENING -> tr(language, "playback.opening")
        PlaybackState.RECONNECTING -> tr(language, "playback.reconnecting")
        PlaybackState.ERROR -> tr(language, "playback.error")
    }?.let { label -> listOf(label, playbackDetail).filterNotNull().joinToString(" · ") }
    val currentStart = currentProgramme?.start
    val currentEnd = currentProgramme?.end
    val hasTiming = currentStart != null && currentEnd != null && currentEnd > currentStart
    return PlaybackOverlayData(
        channelId = channel.id,
        channelNumber = channel.tvgChno?.toString() ?: "–",
        channelName = channel.displayName(language),
        logoUrl = channel.logo?.takeIf { showLogos },
        programmeImageUrl = currentProgramme?.imageUrl?.takeIf {
            showProgrammeImages && section == DashboardSection.LIVE
        },
        showProgrammeInfo = section == DashboardSection.LIVE && showProgrammeInfo,
        showPreviewLogo = section == DashboardSection.CHANNELS,
        channelNumberInput = channelNumberInput.takeIf { section == DashboardSection.LIVE && it.isNotEmpty() },
        programme = PlaybackProgrammeOverlay(
            title = currentProgramme?.displayTitle(language) ?: tr(language, "epg.none"),
            timeRange = if (hasTiming) "${formatTime(currentStart)} – ${formatTime(currentEnd)}" else null,
            progress = if (hasTiming) {
                ((now - currentStart).toFloat() / (currentEnd - currentStart)).coerceIn(0f, 1f)
            } else null,
            nextLine = nextProgramme?.displayTitle(language)?.let { "${tr(language, "epg.next")}: $it" }
        ),
        playbackStatus = playbackStatus,
        playbackError = playbackState == PlaybackState.ERROR,
        buffering = PlaybackBufferingOverlay(tr(language, "playback.buffering"))
            .takeIf { playbackState == PlaybackState.BUFFERING }
    )
}

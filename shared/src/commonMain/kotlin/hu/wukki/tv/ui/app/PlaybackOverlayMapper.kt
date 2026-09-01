package hu.wukki.tv.ui.app

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Channel
import hu.wukki.tv.PlaybackOverlayData
import hu.wukki.tv.PlaybackState
import hu.wukki.tv.Programme
import hu.wukki.tv.ui.components.displayTitle
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
    return PlaybackOverlayData(
        channelId = channel.id,
        channelNumber = channel.tvgChno?.toString() ?: "–",
        channelName = channel.name,
        logoUrl = channel.logo?.takeIf { showLogos },
        programmeImageUrl = currentProgramme?.imageUrl?.takeIf {
            showProgrammeImages && section == DashboardSection.LIVE
        },
        showProgrammeInfo = section == DashboardSection.LIVE && showProgrammeInfo,
        showPreviewLogo = section == DashboardSection.CHANNELS,
        channelNumberInput = channelNumberInput.takeIf { section == DashboardSection.LIVE && it.isNotEmpty() },
        noEpgLabel = tr(language, "epg.none"),
        nextLabel = tr(language, "epg.next"),
        currentTitle = currentProgramme?.displayTitle(language),
        currentStart = currentProgramme?.start,
        currentEnd = currentProgramme?.end,
        nextTitle = nextProgramme?.displayTitle(language),
        now = now,
        playbackStatus = playbackStatus,
        playbackError = playbackState == PlaybackState.ERROR,
        showBufferingSpinner = playbackState == PlaybackState.BUFFERING,
        bufferingLabel = tr(language, "playback.buffering")
    )
}

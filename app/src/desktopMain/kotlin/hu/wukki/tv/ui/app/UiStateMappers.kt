package hu.wukki.tv.ui.app

import hu.wukki.tv.WukkiModel
import hu.wukki.tv.ui.channels.ChannelBrowserUiState
import hu.wukki.tv.ui.channels.ChannelProgrammeUiState
import hu.wukki.tv.ui.channels.ChannelRowUiState
import hu.wukki.tv.ui.guide.GuideUiState
import hu.wukki.tv.ui.guide.GuideDataSource
import hu.wukki.tv.ui.settings.PlaylistSummaryUiState
import hu.wukki.tv.ui.settings.SettingsSection
import hu.wukki.tv.ui.settings.SettingsUiState

/** The only model-to-UI projection layer. Feature composables consume these immutable values. */
internal fun WukkiModel.channelBrowserUiState(now: Long): ChannelBrowserUiState {
    val selected = selectedChannel()
    return ChannelBrowserUiState(
        language = settings.language,
        channels = filteredChannels().mapIndexed { index, channel ->
            ChannelRowUiState(channel, index + 1, currentProgram(channel, now))
        },
        categories = categories(),
        query = query,
        selectedCategory = category,
        onlyFavorites = onlyFavorites,
        selectedChannel = selected?.let { ChannelProgrammeUiState(it, currentProgram(it, now)) },
        showChannelProgramme = settings.display.showChannelProgramme,
        showMiniGuide = settings.display.showMiniGuide,
        now = now
    )
}

internal fun WukkiModel.guideUiState(now: Long) =
    GuideUiState(settings.language, guideChannels(), now)

/** Bridges the presentation model to the guide feature without exposing it to guide composables. */
internal fun WukkiModel.guideDataSource(): GuideDataSource = object : GuideDataSource {
    override val language get() = settings.language
    override val selectedChannelId get() = this@guideDataSource.selectedChannelId
    override fun channels() = guideChannels()
    override fun programmesFor(channel: hu.wukki.tv.Channel, from: Long, to: Long) =
        this@guideDataSource.programmesFor(channel, from, to)
}

internal fun WukkiModel.settingsUiState(selectedSection: SettingsSection?) =
    SettingsUiState(
        language = settings.language,
        settings = settings,
        epgSources = epgSources,
        playlists = state.playlists.map { playlist ->
            PlaylistSummaryUiState(playlist, state.channels.count { it.playlistId == playlist.id })
        },
        selectedSection = selectedSection
    )

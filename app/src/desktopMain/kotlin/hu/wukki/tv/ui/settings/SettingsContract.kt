package hu.wukki.tv.ui.settings

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.AppSettings
import hu.wukki.tv.EpgSource
import hu.wukki.tv.PlaylistDefinition

/** Immutable input for the settings feature; mutations are represented by SettingsActions. */
data class SettingsUiState(
    val language: AppLanguage,
    val settings: AppSettings,
    val epgSources: List<EpgSource>,
    val playlists: List<PlaylistSummaryUiState>,
    val selectedSection: SettingsSection?
)

data class PlaylistSummaryUiState(val definition: PlaylistDefinition, val channelCount: Int)

interface SettingsActions {
    fun selectSection(section: SettingsSection?)
    fun setLanguage(language: AppLanguage)
}

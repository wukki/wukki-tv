package hu.wukki.tv.ui.settings

import hu.wukki.tv.AppLanguage
import hu.wukki.tv.AppSettings
import hu.wukki.tv.DeviceInfo
import hu.wukki.tv.DisplaySettings
import hu.wukki.tv.PlaybackSettings
import hu.wukki.tv.RefreshInterval

data class SettingsSourceUiState(
    val name: String,
    val location: String,
    val updatedAt: Long?
)

data class SettingsUiState(
    val settings: AppSettings,
    val playlistSource: SettingsSourceUiState,
    val epgSource: SettingsSourceUiState?,
    val channelCount: Int,
    val deviceInfo: DeviceInfo?,
    val playbackEngineLabel: String
) {
    val language: AppLanguage get() = settings.language
}

data class SettingsCallbacks(
    val updatePlayback: ((PlaybackSettings) -> PlaybackSettings) -> Unit,
    val updateDisplay: ((DisplaySettings) -> DisplaySettings) -> Unit,
    val setPlaylistRefresh: (RefreshInterval) -> Unit,
    val setEpgRefresh: (RefreshInterval) -> Unit,
    val setLanguage: (AppLanguage) -> Unit,
    val refreshPlaylist: () -> Unit,
    val refreshEpg: () -> Unit
)

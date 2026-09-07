package hu.wukki.tv

import hu.wukki.tv.ui.navigation.*
import hu.wukki.tv.ui.settings.SettingsSection

/** Shared value changes for remote navigation and touch controls. Persists through the model API. */
fun WukkiModel.adjustSetting(option: SettingsOptionId, delta: Int) {
    when (option) {
        is PlaybackSettingsOption -> when (option) {
            PlaybackSettingsOption.VOLUME -> setVolume(settings.playback.volume + delta * 5)
            PlaybackSettingsOption.BUFFER -> setBufferProfile(BufferProfile.entries[
                (settings.playback.bufferProfile.ordinal + delta).mod(BufferProfile.entries.size)])
            PlaybackSettingsOption.AUTOPLAY -> updatePlayback { it.copy(autoPlayOnLaunch = !it.autoPlayOnLaunch) }
            PlaybackSettingsOption.ASPECT_RATIO -> updatePlayback { it.copy(aspectRatio = AspectRatioMode.entries[
                (it.aspectRatio.ordinal + delta).mod(AspectRatioMode.entries.size)]) }
            PlaybackSettingsOption.RECONNECT -> updatePlayback { it.copy(autoReconnect = !it.autoReconnect) }
            PlaybackSettingsOption.RETRIES -> updatePlayback { it.copy(reconnectAttempts = it.reconnectAttempts + delta) }
        }
        is DisplaySettingsOption -> adjustDisplayOption(option, delta)
        EpgSettingsOption.SCHEDULE -> cycleRefresh(SettingsSection.EPG, delta)
        PlaylistSettingsOption.SCHEDULE -> cycleRefresh(SettingsSection.PLAYLISTS, delta)
        LanguageSettingsOption.LANGUAGE -> setLanguage(if (settings.language == AppLanguage.HUNGARIAN) AppLanguage.ENGLISH else AppLanguage.HUNGARIAN)
        EpgSettingsOption.REFRESH, PlaylistSettingsOption.REFRESH -> Unit
        is ParentalSettingsOption, is AboutSettingsOption -> Unit
    }
}

fun WukkiModel.setVolume(value: Int) = updatePlayback { it.copy(volume = value) }
fun WukkiModel.setBufferProfile(value: BufferProfile) = updatePlayback { it.copy(bufferProfile = value) }

private fun WukkiModel.cycleRefresh(section: SettingsSection, delta: Int) {
    val isEpg = section == SettingsSection.EPG
    val intervals = if (isEpg) RefreshInterval.entries.toList() else
        listOf(RefreshInterval.MANUAL, RefreshInterval.SIX_HOURS, RefreshInterval.DAILY)
    val current = if (isEpg) settings.epgRefresh else settings.playlistRefresh
    val next = intervals[(intervals.indexOf(current).coerceAtLeast(0) + delta).mod(intervals.size)]
    if (isEpg) setEpgRefresh(next) else setPlaylistRefresh(next)
}

private fun WukkiModel.adjustDisplayOption(option: DisplaySettingsOption, delta: Int) = updateDisplay { display ->
    when (option) {
        DisplaySettingsOption.UI_SCALE -> {
            val values = listOf(.9f, 1f, 1.15f)
            val current = values.indexOf(display.uiScale).coerceAtLeast(0)
            display.copy(uiScale = values[(current + delta).mod(values.size)])
        }
        DisplaySettingsOption.CHANNEL_LIST -> {
            val current = display.channelListMode.ordinal
            display.copy(channelListMode = ChannelListDisplayMode.entries[(current + delta).mod(ChannelListDisplayMode.entries.size)])
        }
        DisplaySettingsOption.PROGRAMME -> display.copy(showChannelProgramme = !display.showChannelProgramme)
        DisplaySettingsOption.MINI_GUIDE -> display.copy(showMiniGuide = !display.showMiniGuide)
        DisplaySettingsOption.LOGOS -> display.copy(showLogos = !display.showLogos)
        DisplaySettingsOption.PROGRAMME_IMAGES -> display.copy(showProgrammeImages = !display.showProgrammeImages)
    }
}

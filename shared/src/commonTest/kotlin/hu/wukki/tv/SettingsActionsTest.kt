package hu.wukki.tv

import hu.wukki.tv.ui.navigation.LanguageSettingsOption
import hu.wukki.tv.ui.navigation.PlaybackSettingsOption
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsActionsTest {
    private fun model() = WukkiModel(AppState(), RemoteTextLoader { error("No network expected") },
        XmlTvParser { emptyList() }, {})

    @Test
    fun `volume uses five point steps and shared limits for direct and remote edits`() {
        val model = model()
        model.setVolume(0)
        model.adjustSetting(PlaybackSettingsOption.VOLUME, -1)
        assertEquals(0, model.settings.playback.volume)
        model.adjustSetting(PlaybackSettingsOption.VOLUME, 1)
        assertEquals(5, model.settings.playback.volume)
        model.setVolume(99)
        model.adjustSetting(PlaybackSettingsOption.VOLUME, 1)
        assertEquals(100, model.settings.playback.volume)
        model.setVolume(-10)
        assertEquals(0, model.settings.playback.volume)
    }

    @Test
    fun `reconnect attempts stay within one to ten for every entry point`() {
        val model = model()
        model.updatePlayback { it.copy(reconnectAttempts = 1) }
        model.adjustSetting(PlaybackSettingsOption.RETRIES, -1)
        assertEquals(1, model.settings.playback.reconnectAttempts)
        repeat(20) { model.adjustSetting(PlaybackSettingsOption.RETRIES, 1) }
        assertEquals(10, model.settings.playback.reconnectAttempts)
        model.updatePlayback { it.copy(reconnectAttempts = -100) }
        assertEquals(1, model.settings.playback.reconnectAttempts)
        model.updatePlayback { it.copy(reconnectAttempts = 100) }
        assertEquals(10, model.settings.playback.reconnectAttempts)
    }

    @Test
    fun `buffer wraps and language toggles from a directly selected value`() {
        val model = model()
        model.setBufferProfile(BufferProfile.entries.last())
        model.adjustSetting(PlaybackSettingsOption.BUFFER, 1)
        assertEquals(BufferProfile.entries.first(), model.settings.playback.bufferProfile)
        model.adjustSetting(PlaybackSettingsOption.BUFFER, -1)
        assertEquals(BufferProfile.entries.last(), model.settings.playback.bufferProfile)
        model.setLanguage(AppLanguage.HUNGARIAN)
        model.adjustSetting(LanguageSettingsOption.LANGUAGE, 1)
        assertEquals(AppLanguage.ENGLISH, model.settings.language)
        model.adjustSetting(LanguageSettingsOption.LANGUAGE, -1)
        assertEquals(AppLanguage.HUNGARIAN, model.settings.language)
    }
}

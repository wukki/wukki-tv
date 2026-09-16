package hu.wukki.tv

import androidx.compose.ui.input.key.Key
import hu.wukki.tv.ui.navigation.AppRemoteEffect
import hu.wukki.tv.ui.navigation.AppRemoteKey
import hu.wukki.tv.ui.navigation.AppRemoteState
import hu.wukki.tv.ui.navigation.RemoteKey
import hu.wukki.tv.ui.navigation.reduce
import hu.wukki.tv.ui.navigation.toAppRemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackQuickSettingsTest {
    @Test
    fun `unsupported native tracks are hidden and subtitle off is synthesized exactly once`() {
        val tracks =
            listOf(
                PlaybackTrack("bad", "Unsupported", false, supported = false),
                PlaybackTrack("good", "Hungarian", true),
                PlaybackTrack("off", "Disable", false),
            )
        assertEquals(listOf("good"), selectablePlaybackTracks(tracks, QuickSetting.AUDIO).map { it.id })
        val subtitles = selectablePlaybackTracks(tracks, QuickSetting.SUBTITLES)
        assertEquals(listOf("off", "good"), subtitles.map { it.id })
        assertFalse(subtitles.first().selected)
        assertTrue(selectablePlaybackTracks(listOf(tracks.first()), QuickSetting.SUBTITLES).isEmpty())
    }

    @Test
    fun `unknown capabilities and a single audio track do not create selectors`() {
        assertTrue(PlaybackQuickSettings().availableSettings().isEmpty())
        val state = PlaybackQuickSettings(AspectRatioMode.AUTO, listOf(PlaybackTrack("a", "English", true)))
        assertEquals(listOf(QuickSetting.ASPECT), state.availableSettings())
    }

    @Test
    fun `subtitles include off and track cycling wraps both ways`() {
        val state =
            PlaybackQuickSettings(
                audio = listOf(PlaybackTrack("a", "English", true), PlaybackTrack("b", "Hungarian", false)),
                subtitles = listOf(PlaybackTrack("off", null, true), PlaybackTrack("c", "Hungarian", false)),
            )
        assertEquals(listOf(QuickSetting.AUDIO, QuickSetting.SUBTITLES), state.availableSettings())
        assertEquals("b", state.nextTrack(QuickSetting.AUDIO, 1)?.id)
        assertEquals("b", state.nextTrack(QuickSetting.AUDIO, -1)?.id)
        assertEquals("c", state.nextTrack(QuickSetting.SUBTITLES, 1)?.id)
        assertTrue(state.copy(audio = emptyList(), subtitles = listOf(PlaybackTrack("off", null, true))).availableSettings().isEmpty())
    }

    @Test
    fun `live confirm then right opens quick panel and dedicated keys share the command`() {
        val first = AppRemoteState().reduce(AppRemoteKey(remote = RemoteKey.CONFIRM))
        assertTrue(AppRemoteEffect.ShowOverlay in first.effects)
        val second = first.state.copy(overlayVisible = true).reduce(AppRemoteKey(remote = RemoteKey.RIGHT))
        assertTrue(AppRemoteEffect.ShowQuickSettings in second.effects)
        assertTrue(Key.F9.toAppRemoteKey().quickSettings)
        assertTrue(Key.Menu.toAppRemoteKey().quickSettings)
        assertFalse(AppRemoteEffect.ShowQuickSettings in AppRemoteState(dialogVisible = true).reduce(Key.F9.toAppRemoteKey()).effects)
    }
}

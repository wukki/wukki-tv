package hu.wukki.tv.ui.app

import hu.wukki.tv.PlaybackOverlayAction
import hu.wukki.tv.ui.navigation.RemoteKey
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackRecoveryNavigationTest {
    private val actions =
        listOf(
            PlaybackOverlayAction.RETRY,
            PlaybackOverlayAction.CHANNELS,
            PlaybackOverlayAction.DETAILS,
        )

    @Test
    fun `retry is focused by default and confirm activates it`() {
        val result = PlaybackRecoveryNavigationState().reduce(RemoteKey.CONFIRM, actions)

        assertEquals(0, result.state.focusedIndex)
        assertEquals(
            PlaybackRecoveryNavigationEffect.Activate(PlaybackOverlayAction.RETRY),
            result.effect,
        )
    }

    @Test
    fun `direction keys clamp focus and channels is one move away`() {
        val channels = PlaybackRecoveryNavigationState().reduce(RemoteKey.RIGHT, actions)
        val activated = channels.state.reduce(RemoteKey.CONFIRM, actions)

        assertEquals(1, channels.state.focusedIndex)
        assertEquals(
            PlaybackRecoveryNavigationEffect.Activate(PlaybackOverlayAction.CHANNELS),
            activated.effect,
        )
        assertEquals(0, PlaybackRecoveryNavigationState().reduce(RemoteKey.LEFT, actions).state.focusedIndex)
        assertEquals(2, PlaybackRecoveryNavigationState(2).reduce(RemoteKey.RIGHT, actions).state.focusedIndex)
    }
}

package hu.wukki.tv.ui.app

import hu.wukki.tv.PlaybackEngine
import hu.wukki.tv.PlaybackOverlayAction
import hu.wukki.tv.ui.navigation.AppRemoteKey

internal class PlaybackRecoveryController(
    private val session: AppSessionState,
    private val playback: PlaybackEngine,
    private val openChannels: () -> Unit,
) {
    fun perform(action: PlaybackOverlayAction) {
        if (action !in playback.recovery?.actions.orEmpty()) return
        when (action) {
            PlaybackOverlayAction.RETRY -> {
                playback.retry()
            }

            PlaybackOverlayAction.CHANNELS -> {
                playback.stop()
                openChannels()
            }

            PlaybackOverlayAction.CANCEL_RECONNECT -> {
                playback.cancelReconnect()
            }

            PlaybackOverlayAction.DETAILS -> {
                session.playbackRecoveryNavigation =
                    session.playbackRecoveryNavigation.copy(
                        showTechnicalDetail = !session.playbackRecoveryNavigation.showTechnicalDetail,
                    )
                return
            }
        }
        session.playbackRecoveryNavigation = PlaybackRecoveryNavigationState()
    }

    fun handleRemote(key: AppRemoteKey): Boolean {
        val recovery = playback.recovery ?: return false
        key.remote?.let { remoteKey ->
            val result = session.playbackRecoveryNavigation.reduce(remoteKey, recovery.actions)
            session.playbackRecoveryNavigation = result.state
            val effect = result.effect
            if (effect is PlaybackRecoveryNavigationEffect.Activate) perform(effect.action)
            return true
        }
        if ((key.back || key.escape) && session.playbackRecoveryNavigation.showTechnicalDetail) {
            session.playbackRecoveryNavigation =
                session.playbackRecoveryNavigation.copy(showTechnicalDetail = false)
            return true
        }
        return false
    }
}

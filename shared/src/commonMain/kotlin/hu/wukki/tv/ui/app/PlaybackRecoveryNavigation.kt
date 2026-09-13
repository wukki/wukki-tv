package hu.wukki.tv.ui.app

import hu.wukki.tv.PlaybackOverlayAction
import hu.wukki.tv.ui.navigation.RemoteKey

data class PlaybackRecoveryNavigationState(
    val focusedIndex: Int = 0,
    val showTechnicalDetail: Boolean = false,
)

internal sealed interface PlaybackRecoveryNavigationEffect {
    data object None : PlaybackRecoveryNavigationEffect

    data class Activate(
        val action: PlaybackOverlayAction,
    ) : PlaybackRecoveryNavigationEffect
}

internal data class PlaybackRecoveryNavigationResult(
    val state: PlaybackRecoveryNavigationState,
    val effect: PlaybackRecoveryNavigationEffect = PlaybackRecoveryNavigationEffect.None,
)

internal fun PlaybackRecoveryNavigationState.reduce(
    key: RemoteKey,
    actions: List<PlaybackOverlayAction>,
): PlaybackRecoveryNavigationResult {
    if (actions.isEmpty()) return PlaybackRecoveryNavigationResult(this)
    val safeIndex = focusedIndex.coerceIn(0, actions.lastIndex)
    val nextState = copy(focusedIndex = safeIndex)
    return when (key) {
        RemoteKey.LEFT, RemoteKey.UP -> {
            PlaybackRecoveryNavigationResult(nextState.copy(focusedIndex = (safeIndex - 1).coerceAtLeast(0)))
        }

        RemoteKey.RIGHT, RemoteKey.DOWN -> {
            PlaybackRecoveryNavigationResult(nextState.copy(focusedIndex = (safeIndex + 1).coerceAtMost(actions.lastIndex)))
        }

        RemoteKey.CONFIRM -> {
            PlaybackRecoveryNavigationResult(nextState, PlaybackRecoveryNavigationEffect.Activate(actions[safeIndex]))
        }
    }
}

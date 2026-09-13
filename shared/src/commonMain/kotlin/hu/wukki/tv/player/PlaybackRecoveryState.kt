package hu.wukki.tv

enum class PlaybackFailureType { STREAM, TIMEOUT, NETWORK }

enum class PlaybackOverlayAction { RETRY, CHANNELS, CANCEL_RECONNECT, DETAILS }

data class PlaybackRecoveryState(
    val failureType: PlaybackFailureType,
    val reconnectAttempt: Int?,
    val reconnectAttempts: Int,
    val technicalDetail: String?,
) {
    val actions: List<PlaybackOverlayAction>
        get() =
            if (reconnectAttempt != null) {
                listOf(PlaybackOverlayAction.CANCEL_RECONNECT)
            } else {
                listOf(PlaybackOverlayAction.RETRY, PlaybackOverlayAction.CHANNELS, PlaybackOverlayAction.DETAILS)
            }
}

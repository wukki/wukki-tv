package hu.wukki.tv.ui.navigation

/** Ephemeral channel selection shown by the Live TV programme-information panel. */
data class LiveChannelPreviewState(
    val channelId: String? = null,
    val interactionSequence: Int = 0
) {
    val isActive: Boolean get() = channelId != null
}

enum class LiveChannelPreviewEvent { PREVIOUS, NEXT, CONFIRM, CANCEL, TIMEOUT }

sealed interface LiveChannelPreviewEffect {
    data object None : LiveChannelPreviewEffect
    data object Dismiss : LiveChannelPreviewEffect
    data class OpenChannel(val channelId: String) : LiveChannelPreviewEffect
}

data class LiveChannelPreviewResult(
    val state: LiveChannelPreviewState,
    val effect: LiveChannelPreviewEffect = LiveChannelPreviewEffect.None,
    val handled: Boolean = true
)

fun LiveChannelPreviewState.reduce(
    event: LiveChannelPreviewEvent,
    channelIds: List<String>,
    activeChannelId: String?,
    panelVisible: Boolean
): LiveChannelPreviewResult = when (event) {
    LiveChannelPreviewEvent.PREVIOUS, LiveChannelPreviewEvent.NEXT -> {
        if (activeChannelId == null) {
            LiveChannelPreviewResult(this, handled = false)
        } else {
            val direction = if (event == LiveChannelPreviewEvent.NEXT) 1 else -1
            val currentId = channelId ?: activeChannelId
            val targetId = when {
                channelId == null && !panelVisible -> currentId
                channelIds.isEmpty() -> currentId
                else -> {
                    val currentIndex = channelIds.indexOf(currentId)
                    when {
                        currentIndex >= 0 -> channelIds[(currentIndex + direction).mod(channelIds.size)]
                        direction > 0 -> channelIds.first()
                        else -> channelIds.last()
                    }
                }
            }
            LiveChannelPreviewResult(copy(channelId = targetId, interactionSequence = interactionSequence + 1))
        }
    }

    LiveChannelPreviewEvent.CONFIRM -> channelId?.let { selectedId ->
        LiveChannelPreviewResult(
            state = copy(channelId = null, interactionSequence = interactionSequence + 1),
            effect = LiveChannelPreviewEffect.OpenChannel(selectedId)
        )
    } ?: LiveChannelPreviewResult(this, handled = false)

    LiveChannelPreviewEvent.CANCEL, LiveChannelPreviewEvent.TIMEOUT -> LiveChannelPreviewResult(
        state = copy(channelId = null, interactionSequence = interactionSequence + 1),
        effect = LiveChannelPreviewEffect.Dismiss,
        handled = isActive
    )
}

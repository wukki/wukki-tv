package hu.wukki.tv.ui.navigation

data class LiveNavigationVisibilityState(
    val visible: Boolean = true,
    val interactionSequence: Int = 0
)

sealed interface LiveNavigationVisibilityEvent {
    data object EnterLive : LiveNavigationVisibilityEvent
    data class Reveal(val focusNavigation: Boolean) : LiveNavigationVisibilityEvent
    data object Interact : LiveNavigationVisibilityEvent
    data object Timeout : LiveNavigationVisibilityEvent
    data object LeaveLive : LiveNavigationVisibilityEvent
}

enum class LiveNavigationVisibilityEffect {
    NONE,
    FOCUS_NAVIGATION,
    FOCUS_CONTENT_IF_NAVIGATION_FOCUSED
}

data class LiveNavigationVisibilityResult(
    val state: LiveNavigationVisibilityState,
    val effect: LiveNavigationVisibilityEffect = LiveNavigationVisibilityEffect.NONE
)

fun LiveNavigationVisibilityState.reduce(event: LiveNavigationVisibilityEvent): LiveNavigationVisibilityResult = when (event) {
    LiveNavigationVisibilityEvent.EnterLive,
    LiveNavigationVisibilityEvent.Interact -> LiveNavigationVisibilityResult(
        copy(visible = true, interactionSequence = interactionSequence + 1)
    )
    is LiveNavigationVisibilityEvent.Reveal -> LiveNavigationVisibilityResult(
        copy(visible = true, interactionSequence = interactionSequence + 1),
        if (event.focusNavigation) LiveNavigationVisibilityEffect.FOCUS_NAVIGATION
        else LiveNavigationVisibilityEffect.NONE
    )
    LiveNavigationVisibilityEvent.Timeout -> LiveNavigationVisibilityResult(
        copy(visible = false),
        if (visible) LiveNavigationVisibilityEffect.FOCUS_CONTENT_IF_NAVIGATION_FOCUSED
        else LiveNavigationVisibilityEffect.NONE
    )
    LiveNavigationVisibilityEvent.LeaveLive -> LiveNavigationVisibilityResult(copy(visible = false))
}

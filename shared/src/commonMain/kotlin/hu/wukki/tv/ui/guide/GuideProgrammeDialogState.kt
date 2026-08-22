package hu.wukki.tv.ui.guide

/** Pure, platform-independent remote-control state for the Guide programme dialog. */
enum class GuideProgrammeDialogAction { CANCEL, OPEN }
enum class GuideProgrammeDialogEvent { LEFT, RIGHT, CONFIRM, BACK }
enum class GuideProgrammeDialogEffect { NONE, DISMISS, OPEN_CHANNEL }

data class GuideProgrammeDialogState(
    val focusedAction: GuideProgrammeDialogAction = GuideProgrammeDialogAction.OPEN,
    val canOpenChannel: Boolean = true
) {
    fun reduce(event: GuideProgrammeDialogEvent): GuideProgrammeDialogTransition = when (event) {
        GuideProgrammeDialogEvent.LEFT, GuideProgrammeDialogEvent.RIGHT -> GuideProgrammeDialogTransition(
            copy(focusedAction = if (focusedAction == GuideProgrammeDialogAction.OPEN) GuideProgrammeDialogAction.CANCEL else GuideProgrammeDialogAction.OPEN)
        )
        GuideProgrammeDialogEvent.BACK -> GuideProgrammeDialogTransition(this, GuideProgrammeDialogEffect.DISMISS)
        GuideProgrammeDialogEvent.CONFIRM -> when (focusedAction) {
            GuideProgrammeDialogAction.CANCEL -> GuideProgrammeDialogTransition(this, GuideProgrammeDialogEffect.DISMISS)
            GuideProgrammeDialogAction.OPEN -> GuideProgrammeDialogTransition(
                this,
                if (canOpenChannel) GuideProgrammeDialogEffect.OPEN_CHANNEL else GuideProgrammeDialogEffect.DISMISS
            )
        }
    }
}

data class GuideProgrammeDialogTransition(
    val state: GuideProgrammeDialogState,
    val effect: GuideProgrammeDialogEffect = GuideProgrammeDialogEffect.NONE
)

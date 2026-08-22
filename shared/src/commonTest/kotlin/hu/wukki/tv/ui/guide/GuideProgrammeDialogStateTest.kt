package hu.wukki.tv.ui.guide

import kotlin.test.Test
import kotlin.test.assertEquals

class GuideProgrammeDialogStateTest {
    @Test
    fun `the dialog opens with Open focused`() {
        assertEquals(GuideProgrammeDialogAction.OPEN, GuideProgrammeDialogState().focusedAction)
    }

    @Test
    fun `left and right toggle the focused action`() {
        val cancel = GuideProgrammeDialogState().reduce(GuideProgrammeDialogEvent.LEFT).state
        assertEquals(GuideProgrammeDialogAction.CANCEL, cancel.focusedAction)
        assertEquals(GuideProgrammeDialogAction.OPEN, cancel.reduce(GuideProgrammeDialogEvent.RIGHT).state.focusedAction)
    }

    @Test
    fun `confirm dispatches the focused action`() {
        assertEquals(
            GuideProgrammeDialogEffect.OPEN_CHANNEL,
            GuideProgrammeDialogState().reduce(GuideProgrammeDialogEvent.CONFIRM).effect
        )
        assertEquals(
            GuideProgrammeDialogEffect.DISMISS,
            GuideProgrammeDialogState(GuideProgrammeDialogAction.CANCEL).reduce(GuideProgrammeDialogEvent.CONFIRM).effect
        )
    }

    @Test
    fun `back always dismisses without opening a channel`() {
        GuideProgrammeDialogAction.entries.forEach { action ->
            assertEquals(
                GuideProgrammeDialogEffect.DISMISS,
                GuideProgrammeDialogState(action).reduce(GuideProgrammeDialogEvent.BACK).effect
            )
        }
    }

    @Test
    fun `a dialog without an openable channel never emits open`() {
        assertEquals(
            GuideProgrammeDialogEffect.DISMISS,
            GuideProgrammeDialogState(canOpenChannel = false).reduce(GuideProgrammeDialogEvent.CONFIRM).effect
        )
    }
}

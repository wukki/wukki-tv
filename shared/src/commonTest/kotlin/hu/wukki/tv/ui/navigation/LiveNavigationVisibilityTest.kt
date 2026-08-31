package hu.wukki.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveNavigationVisibilityTest {
    @Test
    fun `entering live shows navigation and starts a fresh timeout sequence`() {
        val result = LiveNavigationVisibilityState(visible = false, interactionSequence = 3)
            .reduce(LiveNavigationVisibilityEvent.EnterLive)

        assertTrue(result.state.visible)
        assertEquals(4, result.state.interactionSequence)
        assertEquals(LiveNavigationVisibilityEffect.NONE, result.effect)
    }

    @Test
    fun `navigation starts visible and timeout hides it`() {
        val result = LiveNavigationVisibilityState().reduce(LiveNavigationVisibilityEvent.Timeout)

        assertFalse(result.state.visible)
        assertEquals(
            LiveNavigationVisibilityEffect.FOCUS_CONTENT_IF_NAVIGATION_FOCUSED,
            result.effect
        )
    }

    @Test
    fun `back reveal shows and focuses navigation`() {
        val result = LiveNavigationVisibilityState(visible = false)
            .reduce(LiveNavigationVisibilityEvent.Reveal(focusNavigation = true))

        assertTrue(result.state.visible)
        assertEquals(1, result.state.interactionSequence)
        assertEquals(LiveNavigationVisibilityEffect.FOCUS_NAVIGATION, result.effect)
    }

    @Test
    fun `touch reveal does not force d-pad focus`() {
        val result = LiveNavigationVisibilityState(visible = false)
            .reduce(LiveNavigationVisibilityEvent.Reveal(focusNavigation = false))

        assertTrue(result.state.visible)
        assertEquals(LiveNavigationVisibilityEffect.NONE, result.effect)
    }

    @Test
    fun `navigation interaction restarts visibility sequence`() {
        val result = LiveNavigationVisibilityState(visible = true, interactionSequence = 4)
            .reduce(LiveNavigationVisibilityEvent.Interact)

        assertTrue(result.state.visible)
        assertEquals(5, result.state.interactionSequence)
    }

    @Test
    fun `leaving live hides navigation without focus effect`() {
        val result = LiveNavigationVisibilityState().reduce(LiveNavigationVisibilityEvent.LeaveLive)

        assertFalse(result.state.visible)
        assertEquals(LiveNavigationVisibilityEffect.NONE, result.effect)
    }

    @Test
    fun `timeout on already hidden navigation does not move focus`() {
        val result = LiveNavigationVisibilityState(visible = false)
            .reduce(LiveNavigationVisibilityEvent.Timeout)

        assertFalse(result.state.visible)
        assertEquals(LiveNavigationVisibilityEffect.NONE, result.effect)
    }
}

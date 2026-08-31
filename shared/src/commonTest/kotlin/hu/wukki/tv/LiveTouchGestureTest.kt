package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveTouchGestureTest {
    private val threshold = 48f

    @Test
    fun `upward flick selects the next channel`() {
        assertEquals(LiveTouchAction.NEXT_CHANNEL, classifyLiveTouch(4f, -72f, 180L, threshold, 300L))
    }

    @Test
    fun `downward flick selects the previous channel`() {
        assertEquals(LiveTouchAction.PREVIOUS_CHANNEL, classifyLiveTouch(2f, 72f, 180L, threshold, 300L))
    }

    @Test
    fun `downward flick from top edge shows navigation instead of changing channel`() {
        assertEquals(
            LiveTouchAction.SHOW_NAVIGATION,
            classifyLiveTouch(2f, 72f, 180L, threshold, 300L, startedInTopEdge = true)
        )
        assertEquals(
            LiveTouchAction.PREVIOUS_CHANNEL,
            classifyLiveTouch(2f, 72f, 180L, threshold, 300L, startedInTopEdge = false)
        )
    }

    @Test
    fun `short still touch toggles information`() {
        assertEquals(LiveTouchAction.TAP, classifyLiveTouch(5f, 3f, 120L, threshold, 300L))
    }

    @Test
    fun `diagonal or long touch does not trigger a live action`() {
        assertEquals(LiveTouchAction.NONE, classifyLiveTouch(60f, 60f, 160L, threshold, 300L))
        assertEquals(LiveTouchAction.NONE, classifyLiveTouch(3f, 3f, 400L, threshold, 300L))
    }
}

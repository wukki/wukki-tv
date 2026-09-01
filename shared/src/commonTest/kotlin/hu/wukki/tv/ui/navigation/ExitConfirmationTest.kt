package hu.wukki.tv.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ExitConfirmationTest {
    @Test
    fun `first back requests a hint and second quick back exits`() {
        val first = ExitConfirmationState().requestExit(nowMillis = 1_000L)
        val second = first.state.requestExit(nowMillis = 2_500L)

        assertEquals(ExitConfirmationEffect.SHOW_HINT, first.effect)
        assertEquals(ExitConfirmationEffect.EXIT, second.effect)
        assertEquals(ExitConfirmationState(), second.state)
    }

    @Test
    fun `late second back starts a new confirmation window`() {
        val first = ExitConfirmationState().requestExit(nowMillis = 1_000L)
        val late = first.state.requestExit(nowMillis = 3_001L)

        assertEquals(ExitConfirmationEffect.SHOW_HINT, late.effect)
        assertEquals(3_001L, late.state.firstBackAtMillis)
    }

    @Test
    fun `clock moving backwards cannot accidentally exit`() {
        val result = ExitConfirmationState(firstBackAtMillis = 2_000L).requestExit(nowMillis = 1_500L)

        assertEquals(ExitConfirmationEffect.SHOW_HINT, result.effect)
    }
}

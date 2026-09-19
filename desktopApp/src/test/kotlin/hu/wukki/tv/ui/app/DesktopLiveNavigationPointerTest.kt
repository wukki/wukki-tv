package hu.wukki.tv.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.LiveNavigationVisibilityEffect
import hu.wukki.tv.ui.navigation.LiveNavigationVisibilityEvent
import hu.wukki.tv.ui.navigation.LiveNavigationVisibilityState
import hu.wukki.tv.ui.navigation.reduce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class DesktopLiveNavigationPointerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `movement reveals without focus change and restarts five second timeout`() =
        runTest {
            val state = mutableStateOf(LiveNavigationVisibilityState(visible = false))
            val scene =
                ImageComposeScene(width = 400, height = 300, coroutineContext = coroutineContext) {
                    LiveNavigationTimeout(DashboardSection.LIVE, state.value, true) {
                        state.value = state.value.reduce(LiveNavigationVisibilityEvent.Timeout).state
                    }
                    Box(
                        Modifier.fillMaxSize().then(
                            desktopLiveNavigationPointerModifier {
                                val result = state.value.reduce(LiveNavigationVisibilityEvent.Reveal(focusNavigation = false))
                                assertEquals(LiveNavigationVisibilityEffect.NONE, result.effect)
                                state.value = result.state
                            },
                        ),
                    )
                }

            fun frame() {
                scene.render().close()
                runCurrent()
            }
            try {
                frame()
                scene.sendPointerEvent(PointerEventType.Move, Offset(50f, 10f), type = PointerType.Mouse)
                frame()
                assertTrue(state.value.visible)
                advanceTimeBy(4_000)
                scene.sendPointerEvent(PointerEventType.Move, Offset(51f, 10f), type = PointerType.Mouse)
                frame()
                advanceTimeBy(4_999)
                runCurrent()
                assertTrue(state.value.visible)
                advanceTimeBy(1)
                runCurrent()
                assertFalse(state.value.visible)
            } finally {
                scene.close()
            }
        }

    @Test
    fun `top edge excludes outside coordinates and lower boundary`() {
        assertFalse(isInDesktopNavigationEdge(-1f))
        assertTrue(isInDesktopNavigationEdge(0f))
        assertTrue(isInDesktopNavigationEdge(19f))
        assertFalse(isInDesktopNavigationEdge(20f))
    }

    @Test
    fun `mouse movement over clickable content uses logical pixels and latest callback`() {
        var first = 0
        var latest = 0
        val callback = mutableStateOf<() -> Unit>({ first++ })
        val scene =
            ImageComposeScene(width = 400, height = 300, density = Density(2f)) {
                Box(Modifier.fillMaxSize().then(desktopLiveNavigationPointerModifier(callback.value))) {
                    Box(Modifier.fillMaxSize().clickable {})
                }
            }
        try {
            scene.render().close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(50f, 40f), type = PointerType.Mouse)
            assertEquals(0, first)
            scene.sendPointerEvent(PointerEventType.Move, Offset(50f, 38f), type = PointerType.Mouse)
            assertTrue(first > 0)
            val before = first
            callback.value = { latest++ }
            scene.render().close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(51f, 38f), type = PointerType.Mouse)
            assertEquals(before, first)
            assertEquals(1, latest)
            scene.sendPointerEvent(PointerEventType.Move, Offset(51f, 80f), type = PointerType.Mouse)
            assertEquals(1, latest)
        } finally {
            scene.close()
        }
    }
}

package hu.wukki.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenWakeCoordinatorTest {
    @Test
    fun `acquires and releases once for foreground transitions`() {
        val controller = RecordingScreenWakeController()
        val coordinator = ScreenWakeCoordinator(controller)

        coordinator.update(true)
        coordinator.update(true)
        coordinator.update(false)
        coordinator.update(false)

        assertEquals(1, controller.acquireCount)
        assertEquals(1, controller.releaseCount)
    }

    @Test
    fun `close releases an active wake lease`() {
        val controller = RecordingScreenWakeController()
        val coordinator = ScreenWakeCoordinator(controller)

        coordinator.update(true)
        coordinator.close()

        assertEquals(1, controller.releaseCount)
    }

    private class RecordingScreenWakeController : ScreenWakeController {
        var acquireCount = 0
        var releaseCount = 0

        override fun acquire() { acquireCount++ }
        override fun release() { releaseCount++ }
    }
}

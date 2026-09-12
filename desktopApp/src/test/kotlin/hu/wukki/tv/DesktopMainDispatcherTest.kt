package hu.wukki.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopMainDispatcherTest {
    @Test
    fun `main dispatcher runs on the Swing event thread`() =
        runBlocking {
            val runsOnEventThread =
                withContext(Dispatchers.Main.immediate) {
                    EventQueue.isDispatchThread()
                }

            assertTrue(runsOnEventThread)
        }
}

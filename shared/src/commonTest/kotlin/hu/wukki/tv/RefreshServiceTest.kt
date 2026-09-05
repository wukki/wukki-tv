package hu.wukki.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RefreshServiceTest {
    @Test
    fun `worker and manual request share one winning refresh commit`() = runBlocking {
        val service = RefreshService(this)
        val started = CompletableDeferred<Unit>()
        val downloaded = CompletableDeferred<Unit>()
        var commits = 0
        val worker = async {
            service.refresh("playlist") {
                started.complete(Unit)
                downloaded.await()
                commits++
                true
            }
        }
        started.await()
        val manual = async(start = CoroutineStart.UNDISPATCHED) {
            service.refresh("playlist") { error("Duplicate refresh") }
        }
        downloaded.complete(Unit)
        assertTrue(worker.await())
        assertTrue(manual.await())
        assertEquals(1, commits)
    }

    @Test
    fun `activity cancellation does not cancel process refresh and EPG waits its turn`() = runBlocking {
        val service = RefreshService(this)
        val started = CompletableDeferred<Unit>()
        val downloaded = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val activity = async {
            service.refresh("playlist") {
                started.complete(Unit)
                downloaded.await()
                events += "playlist commit"
                true
            }
        }
        started.await()
        activity.cancel()
        val epg = async { service.refresh("epg") { events += "epg commit"; true } }
        yield()
        assertTrue(events.isEmpty())
        downloaded.complete(Unit)
        assertTrue(epg.await())
        assertEquals(listOf("playlist commit", "epg commit"), events)
    }
}

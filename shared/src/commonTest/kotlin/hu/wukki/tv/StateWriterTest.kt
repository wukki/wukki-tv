package hu.wukki.tv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StateWriterTest {
    @Test
    fun `ten thousand rapid edits persist only the latest snapshot`() =
        runTest {
            val saved = mutableListOf<AppState>()
            val writer = StateWriter(backgroundScope, store { saved += it })
            repeat(10_000) { writer.submit(AppState(lastChannelId = "$it")) }
            runCurrent()
            assertEquals(0, saved.size)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("9999"), saved.map { it.lastChannelId })
            writer.flush()
            assertEquals(1, saved.size)
        }

    @Test
    fun `slow write and concurrent flushes serialize and preserve the newest edit`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val saved = mutableListOf<AppState>()
            var inFlight = 0
            var maxInFlight = 0
            val writer =
                StateWriter(
                    backgroundScope,
                    store {
                        inFlight++
                        maxInFlight = maxOf(maxInFlight, inFlight)
                        if (it.lastChannelId == "first") gate.await()
                        saved += it
                        inFlight--
                    },
                )
            writer.submit(AppState(lastChannelId = "first"))
            val firstFlush = async { writer.flush() }
            runCurrent()
            repeat(10_000) { writer.submit(AppState(lastChannelId = "$it")) }
            val lastFlush = async { writer.flush() }
            runCurrent()
            assertFalse(lastFlush.isCompleted)
            gate.complete(Unit)
            firstFlush.await()
            lastFlush.await()
            assertEquals(listOf("first", "9999"), saved.map { it.lastChannelId })
            assertEquals(1, maxInFlight)
        }

    @Test
    fun `save failure remains visible and flush retries without losing pending state`() =
        runTest {
            var failing = true
            var saved: AppState? = null
            val writer =
                StateWriter(
                    backgroundScope,
                    store {
                        if (failing) error("disk full")
                        saved = it
                    },
                )
            val expected = AppState(lastChannelId = "last")
            writer.submit(expected)
            runCurrent()
            advanceTimeBy(200)
            runCurrent()
            assertNotNull(writer.failure.value)
            assertFailsWith<IllegalStateException> { writer.flush() }
            failing = false
            writer.flush()
            assertEquals(expected, saved)
            assertNull(writer.failure.value)
        }

    @Test
    fun `cancelled flush releases the lock and leaves the snapshot retryable`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val saved = mutableListOf<AppState>()
            val expected = AppState(lastChannelId = "pending")
            val writer =
                StateWriter(
                    backgroundScope,
                    store {
                        gate.await()
                        saved += it
                    },
                )
            writer.submit(expected)
            val interrupted = async { writer.flush() }
            runCurrent()
            interrupted.cancelAndJoin()
            assertNull(writer.failure.value)
            gate.complete(Unit)
            writer.flush()
            assertEquals(listOf(expected), saved)
        }

    private fun store(save: suspend (AppState) -> Unit): AppStateStore =
        object : AppStateStore {
            override suspend fun load() = LoadStateResult(AppState())

            override suspend fun save(state: AppState) = save.invoke(state)
        }
}

package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebOsPlaybackSessionTest {
    private class Clock : WebOsPlaybackScheduler {
        data class Task(
            val delay: Int,
            val action: () -> Unit,
            var cancelled: Boolean = false,
        )

        val tasks = mutableListOf<Task>()

        override fun after(
            delayMillis: Int,
            action: () -> Unit,
        ): WebOsPlaybackCancellation =
            Task(delayMillis, action).also(tasks::add).let { task ->
                WebOsPlaybackCancellation { task.cancelled = true }
            }

        fun runNext(): Int {
            val task = tasks.first { !it.cancelled }
            tasks.remove(task)
            task.action()
            return task.delay
        }

        fun runCancelledTasks() {
            tasks.toList().forEach { task ->
                tasks.remove(task)
                task.action()
            }
        }
    }

    private data class Harness(
        val clock: Clock,
        val session: WebOsPlaybackSession,
        val starts: MutableList<Pair<String, Long>>,
        val played: MutableList<String>,
        val stops: () -> Int,
    )

    @Test
    fun `events and failures from a replaced source cannot win`() {
        val harness = harness()
        harness.session.play(channel("one"), policy())
        val oldToken = harness.starts.last().second
        harness.session.play(channel("two"), policy())
        val currentToken = harness.starts.last().second

        harness.session.playing(oldToken)
        harness.session.failed(oldToken, "stale network error")

        assertEquals(PlaybackState.OPENING, harness.session.snapshot.state)
        assertEquals(
            "two",
            harness.session.snapshot.channel
                ?.id,
        )
        assertTrue(harness.played.isEmpty())

        harness.session.playing(currentToken)
        assertEquals(PlaybackState.PLAYING, harness.session.snapshot.state)
        assertEquals(listOf("two"), harness.played)
    }

    @Test
    fun `configured reconnect count controls backoff and final error`() {
        val harness = harness()
        harness.session.play(channel("one"), policy(reconnectAttempts = 2))

        harness.session.failed(harness.starts.last().second, "network")
        assertEquals(PlaybackState.RECONNECTING, harness.session.snapshot.state)
        assertEquals(1_000, harness.clock.runNext())

        harness.session.failed(harness.starts.last().second, "network")
        assertEquals(2_000, harness.clock.runNext())

        harness.session.failed(harness.starts.last().second, "network")
        assertEquals(PlaybackState.ERROR, harness.session.snapshot.state)
        assertEquals(3, harness.starts.size)
        assertEquals(4, harness.stops())
    }

    @Test
    fun `stop cancels reconnect and stale timer cannot restart media`() {
        val harness = harness()
        harness.session.play(channel("one"), policy())
        harness.session.failed(harness.starts.last().second, "network")

        harness.session.stop()
        harness.clock.runCancelledTasks()

        assertEquals(PlaybackState.IDLE, harness.session.snapshot.state)
        assertEquals(1, harness.starts.size)
    }

    @Test
    fun `manual retry starts a new generation after terminal failure`() {
        val harness = harness()
        harness.session.play(channel("one"), policy(autoReconnect = false))
        val failedToken = harness.starts.last().second
        harness.session.failed(failedToken, "unsupported")

        assertEquals(PlaybackState.ERROR, harness.session.snapshot.state)
        harness.session.retry()

        assertEquals(PlaybackState.OPENING, harness.session.snapshot.state)
        assertEquals(2, harness.starts.size)
        harness.session.playing(failedToken)
        assertEquals(PlaybackState.OPENING, harness.session.snapshot.state)
    }

    @Test
    fun `buffering is debounced and returns to the preceding state`() {
        val harness = harness()
        harness.session.play(channel("one"), policy())
        val token = harness.starts.last().second

        harness.session.bufferingStarted(token)
        assertEquals(PlaybackState.OPENING, harness.session.snapshot.state)
        assertEquals(250, harness.clock.runNext())
        assertEquals(PlaybackState.BUFFERING, harness.session.snapshot.state)

        harness.session.bufferingEnded(token)
        assertEquals(PlaybackState.OPENING, harness.session.snapshot.state)
        harness.session.playing(token)
        assertEquals(PlaybackState.PLAYING, harness.session.snapshot.state)
    }

    private fun harness(): Harness {
        val clock = Clock()
        val starts = mutableListOf<Pair<String, Long>>()
        val played = mutableListOf<String>()
        var stops = 0
        lateinit var session: WebOsPlaybackSession
        session =
            WebOsPlaybackSession(
                scheduler = clock,
                startMedia = { channel, token -> starts += channel.id to token },
                stopMedia = { stops++ },
                onSnapshot = {},
                onPlaying = played::add,
            )
        return Harness(clock, session, starts, played) { stops }
    }

    private fun policy(
        autoReconnect: Boolean = true,
        reconnectAttempts: Int = 3,
    ) = WebOsPlaybackPolicy(autoReconnect, reconnectAttempts)

    private fun channel(id: String) = Channel(id, "webos", id, "https://example.test/$id.m3u8", id, null, group = "Teszt", logo = null)
}

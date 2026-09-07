package hu.wukki.tv

import kotlin.test.*

class PlaybackSessionTest {
    private class Clock : PlaybackScheduler {
        data class Task(val delay: Long, val action: () -> Unit, var cancelled: Boolean = false)
        val tasks = mutableListOf<Task>()
        override fun after(delayMillis: Long, action: () -> Unit): PlaybackCancellation {
            val task = Task(delayMillis, action)
            tasks += task
            return PlaybackCancellation { task.cancelled = true }
        }
        fun next(): Long {
            val task = tasks.first { !it.cancelled }
            tasks.remove(task)
            task.action()
            return task.delay
        }
    }
    private class Adapter : PlaybackAdapter {
        var token = 0L
        var starts = 0
        var policy: PlaybackBufferPolicy? = null
        override fun play(channel: Channel, buffers: PlaybackBufferPolicy, generation: Long) {
            starts++; token = generation; policy = buffers
        }
        override fun stop() {}
        override fun volume(value: Int) {}
        override fun aspect(value: AspectRatioMode) {}
    }
    private val channel = Channel("one", "list", "One", "https://example.test/one", null, null, group = "", logo = null)

    @Test
    fun `three retries use the same backoff and then fail on every adapter`() {
        val clock = Clock(); val adapter = Adapter(); val session = PlaybackSession(adapter, clock)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        assertEquals(PlaybackState.OPENING, session.state)
        repeat(3) { index ->
            session.failed(adapter.token)
            session.failed(adapter.token) // duplicate native error must not consume another attempt
            assertEquals(PlaybackState.RECONNECTING, session.state)
            assertEquals((index + 1) * 1000L, clock.next())
            assertEquals(PlaybackState.OPENING, session.state)
        }
        session.failed(adapter.token)
        assertEquals(PlaybackState.ERROR, session.state)
        assertEquals(4, adapter.starts)
    }

    @Test
    fun `buffering is delayed and playing resets the retry budget`() {
        val clock = Clock(); val adapter = Adapter(); val session = PlaybackSession(adapter, clock)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        session.bufferingStarted(adapter.token)
        assertEquals(PlaybackState.OPENING, session.state)
        assertEquals(250L, clock.next())
        assertEquals(PlaybackState.BUFFERING, session.state)
        session.failed(adapter.token); clock.next()
        session.playing(adapter.token)
        assertEquals(PlaybackState.PLAYING, session.state)
        assertEquals(channel.id, session.successfullyPlayedChannelId)
        session.failed(adapter.token)
        assertEquals(1000L, clock.next())
    }

    @Test
    fun `cancelled buffering indicator cannot replace playing state`() {
        val clock = Clock(); val adapter = Adapter(); val session = PlaybackSession(adapter, clock)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        session.bufferingStarted(adapter.token)
        val queuedCallback = clock.tasks.last().action
        session.playing(adapter.token)
        queuedCallback()
        assertEquals(PlaybackState.PLAYING, session.state)
    }

    @Test
    fun `all profiles reach native adapters through the common policy`() {
        val adapter = Adapter(); val session = PlaybackSession(adapter, Clock())
        for (profile in BufferProfile.entries) {
            session.play(channel, PlaybackSettings(bufferProfile = profile), AppLanguage.ENGLISH)
            assertEquals(profile.bufferPolicy(), adapter.policy)
        }
        assertEquals(PlaybackBufferPolicy(1000, 3000, 15000, 1000, 2000), BufferProfile.BALANCED.bufferPolicy())
        assertEquals(listOf(300, 1000, 3000), BufferProfile.entries.map { it.bufferPolicy().networkCacheMs })
    }

    @Test
    fun `stop channel change and release reject stale events and timers`() {
        val clock = Clock(); val adapter = Adapter(); val session = PlaybackSession(adapter, clock)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        val old = adapter.token
        session.failed(old)
        val staleTimer = clock.tasks.last().action
        session.stop(); staleTimer(); session.playing(old)
        assertEquals(PlaybackState.IDLE, session.state)
        assertEquals(1, adapter.starts)
        session.play(channel.copy(id = "two", streamUrl = "https://example.test/two"), PlaybackSettings(), AppLanguage.ENGLISH)
        session.failed(old)
        assertEquals(PlaybackState.OPENING, session.state)
        session.release()
        session.bufferingStarted(adapter.token)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        assertEquals(PlaybackState.IDLE, session.state)
        assertEquals(2, adapter.starts)
    }

    @Test
    fun `disabled reconnect fails immediately and background resume opens once`() {
        val adapter = Adapter()
        val session = PlaybackSession(adapter, Clock())
        session.play(channel, PlaybackSettings(autoReconnect = false), AppLanguage.ENGLISH)
        session.failed(adapter.token)
        assertEquals(PlaybackState.ERROR, session.state)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        session.pauseForBackground()
        assertEquals(PlaybackState.IDLE, session.state)
        session.resumeAfterBackground()
        session.resumeAfterBackground()
        assertEquals(3, adapter.starts)
    }

    @Test
    fun `quick buffering completion cancels the delayed indicator`() {
        val clock = Clock()
        val adapter = Adapter()
        val session = PlaybackSession(adapter, clock)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        session.bufferingStarted(adapter.token)
        val delayedIndicator = clock.tasks.last()

        session.bufferingEnded(adapter.token)
        delayedIndicator.action()

        assertTrue(delayedIndicator.cancelled)
        assertEquals(PlaybackState.OPENING, session.state)
    }

    @Test
    fun `completed initial buffering returns to opening`() {
        val clock = Clock()
        val adapter = Adapter()
        val session = PlaybackSession(adapter, clock)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        session.bufferingStarted(adapter.token)
        assertEquals(250L, clock.next())
        assertEquals(PlaybackState.BUFFERING, session.state)

        session.bufferingEnded(adapter.token)

        assertEquals(PlaybackState.OPENING, session.state)
    }

    @Test
    fun `completed rebuffering returns to playing`() {
        val clock = Clock()
        val adapter = Adapter()
        val session = PlaybackSession(adapter, clock)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        session.playing(adapter.token)
        session.bufferingStarted(adapter.token)
        assertEquals(250L, clock.next())
        assertEquals(PlaybackState.BUFFERING, session.state)

        session.bufferingEnded(adapter.token)

        assertEquals(PlaybackState.PLAYING, session.state)
    }

    @Test
    fun `stop and channel change reject stale buffering callbacks`() {
        val clock = Clock()
        val adapter = Adapter()
        val session = PlaybackSession(adapter, clock)
        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        session.bufferingStarted(adapter.token)
        val stoppedIndicator = clock.tasks.last().action

        session.stop()
        stoppedIndicator()
        assertEquals(PlaybackState.IDLE, session.state)

        session.play(channel, PlaybackSettings(), AppLanguage.ENGLISH)
        val oldToken = adapter.token
        session.bufferingStarted(oldToken)
        assertEquals(250L, clock.next())
        session.play(
            channel.copy(id = "two", streamUrl = "https://example.test/two"),
            PlaybackSettings(),
            AppLanguage.ENGLISH,
        )

        session.bufferingEnded(oldToken)

        assertEquals(PlaybackState.OPENING, session.state)
    }
}

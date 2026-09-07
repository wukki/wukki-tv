package hu.wukki.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.components.displayName

/** Engine-independent buffer budgets. Adapters translate these to their native load controls. */
data class PlaybackBufferPolicy(
    val networkCacheMs: Int,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackMs: Int,
    val rebufferMs: Int
)

fun BufferProfile.bufferPolicy(): PlaybackBufferPolicy = when (this) {
    BufferProfile.LOW_LATENCY -> PlaybackBufferPolicy(300, 1_000, 6_000, 500, 1_000)
    BufferProfile.BALANCED -> PlaybackBufferPolicy(1_000, 3_000, 15_000, 1_000, 2_000)
    BufferProfile.STABLE -> PlaybackBufferPolicy(3_000, 8_000, 30_000, 2_000, 4_000)
}

interface PlaybackAdapter {
    fun play(channel: Channel, buffers: PlaybackBufferPolicy, generation: Long)
    fun stop()
    fun volume(value: Int)
    fun aspect(value: AspectRatioMode)
}

fun interface PlaybackCancellation { fun cancel() }
fun interface PlaybackScheduler {
    /** Execute on the same event loop as session commands and native event delivery. */
    fun after(delayMillis: Long, action: () -> Unit): PlaybackCancellation
}

/** Sole owner of playback/retry policy. All calls must run on the host UI event loop. */
class PlaybackSession(private val adapter: PlaybackAdapter, private val scheduler: PlaybackScheduler) {
    var state by mutableStateOf(PlaybackState.IDLE)
        private set
    var detail by mutableStateOf<String?>(null)
        private set
    var successfullyPlayedChannelId by mutableStateOf<String?>(null)
        private set
    private var channel: Channel? = null
    private var settings = PlaybackSettings()
    private var language = AppLanguage.HUNGARIAN
    private var generation = 0L
    private var attempts = 0
    private var timerVersion = 0L
    private var retry: PlaybackCancellation? = null
    private var buffering: PlaybackCancellation? = null
    private var released = false
    private var paused = false

    fun play(next: Channel?, nextSettings: PlaybackSettings, nextLanguage: AppLanguage) {
        if (released || next == null) return
        val restart = next.streamUrl != channel?.streamUrl || settings.bufferProfile != nextSettings.bufferProfile ||
            state == PlaybackState.IDLE || state == PlaybackState.ERROR
        channel = next
        settings = nextSettings.copy(volume = nextSettings.volume.coerceIn(0, 100), reconnectAttempts = nextSettings.reconnectAttempts.coerceIn(1, 10))
        language = nextLanguage
        paused = false
        adapter.volume(settings.volume)
        adapter.aspect(settings.aspectRatio)
        if (restart) {
            attempts = 0
            start()
        } else if (state == PlaybackState.RECONNECTING && (!settings.autoReconnect || attempts > settings.reconnectAttempts)) {
            cancelTimers()
            state = PlaybackState.ERROR
            detail = tr(language, "playback.stream.failed", next.displayName(language), tr(language, "error.unknown"))
        }
    }

    fun updateSettings(value: PlaybackSettings) = play(channel, value, language)

    private fun start() {
        val selected = channel ?: return
        cancelTimers()
        generation++
        state = PlaybackState.OPENING
        detail = tr(language, "playback.channel.opening", selected.displayName(language))
        adapter.stop()
        try {
            adapter.play(selected, settings.bufferProfile.bufferPolicy(), generation)
            adapter.volume(settings.volume)
            adapter.aspect(settings.aspectRatio)
        } catch (exception: Exception) {
            failed(generation, exception.message)
        }
    }

    fun buffering(token: Long) {
        if (!accepts(token) || retry != null || buffering != null || state == PlaybackState.BUFFERING) return
        val version = timerVersion
        buffering = scheduler.after(250L) {
            if (accepts(token) && version == timerVersion && retry == null) {
                buffering = null
                state = PlaybackState.BUFFERING
                detail = null
            }
        }
    }

    fun playing(token: Long) {
        if (!accepts(token) || retry != null) return
        cancelTimers()
        attempts = 0
        state = PlaybackState.PLAYING
        detail = null
        successfullyPlayedChannelId = channel?.id
    }

    fun failed(token: Long, reason: String? = null) {
        if (!accepts(token) || retry != null) return
        cancelTimers()
        val selected = channel ?: return
        if (!settings.autoReconnect || attempts >= settings.reconnectAttempts) {
            state = PlaybackState.ERROR
            detail = tr(language, "playback.stream.failed", selected.displayName(language), reason ?: tr(language, "error.unknown"))
            return
        }
        attempts++
        state = PlaybackState.RECONNECTING
        detail = tr(language, "playback.reconnect.attempt", selected.displayName(language), attempts, settings.reconnectAttempts)
        val version = timerVersion
        retry = scheduler.after(attempts * 1_000L) {
            if (accepts(token) && version == timerVersion) {
                retry = null
                start()
            }
        }
    }

    fun stop() {
        paused = false
        generation++
        cancelTimers()
        state = PlaybackState.IDLE
        detail = null
        adapter.stop()
    }

    fun pauseForBackground() {
        val shouldResume = state in setOf(PlaybackState.OPENING, PlaybackState.BUFFERING, PlaybackState.PLAYING, PlaybackState.RECONNECTING)
        stop()
        paused = shouldResume
    }

    fun resumeAfterBackground() {
        if (!released && paused) {
            paused = false
            attempts = 0
            start()
        }
    }

    fun release() {
        if (released) return
        stop()
        released = true
    }

    private fun accepts(token: Long) = !released && token == generation && state != PlaybackState.IDLE && state != PlaybackState.ERROR
    private fun cancelTimers() {
        timerVersion++
        retry?.cancel()
        retry = null
        buffering?.cancel()
        buffering = null
    }
}

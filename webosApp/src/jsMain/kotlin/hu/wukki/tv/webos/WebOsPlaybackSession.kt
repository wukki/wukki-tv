package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.PlaybackState

internal data class WebOsPlaybackPolicy(
    val autoReconnect: Boolean,
    val reconnectAttempts: Int,
)

internal data class WebOsPlaybackSnapshot(
    val state: PlaybackState = PlaybackState.IDLE,
    val channel: Channel? = null,
    val detail: String? = null,
    val reconnectAttempt: Int? = null,
    val reconnectAttempts: Int = 0,
    val technicalDetail: String? = null,
)

internal fun interface WebOsPlaybackCancellation {
    fun cancel()
}

internal fun interface WebOsPlaybackScheduler {
    fun after(
        delayMillis: Int,
        action: () -> Unit,
    ): WebOsPlaybackCancellation
}

/** Serializes media commands and rejects callbacks from replaced sources. */
internal class WebOsPlaybackSession(
    private val scheduler: WebOsPlaybackScheduler,
    private val startMedia: (Channel, Long) -> Unit,
    private val stopMedia: () -> Unit,
    private val onSnapshot: (WebOsPlaybackSnapshot) -> Unit,
    private val onPlaying: (String) -> Unit,
) {
    var snapshot = WebOsPlaybackSnapshot()
        private set
    private var policy = WebOsPlaybackPolicy(autoReconnect = true, reconnectAttempts = 3)
    private var generation = 0L
    private var attempts = 0
    private var timerVersion = 0L
    private var retry: WebOsPlaybackCancellation? = null
    private var buffering: WebOsPlaybackCancellation? = null
    private var stateBeforeBuffering: PlaybackState? = null
    private var backgroundChannel: Channel? = null
    private var backgrounded = false

    fun play(
        channel: Channel,
        requestedPolicy: WebOsPlaybackPolicy,
    ) {
        backgroundChannel = null
        backgrounded = false
        policy = requestedPolicy.copy(reconnectAttempts = requestedPolicy.reconnectAttempts.coerceIn(1, 10))
        val restart =
            channel.streamUrl != snapshot.channel?.streamUrl ||
                snapshot.state == PlaybackState.IDLE ||
                snapshot.state == PlaybackState.ERROR
        if (!restart) {
            update(snapshot.copy(channel = channel, reconnectAttempts = policy.reconnectAttempts))
            if (snapshot.state == PlaybackState.RECONNECTING && !policy.autoReconnect) cancelReconnect()
            return
        }
        attempts = 0
        start(channel)
    }

    fun updatePolicy(requestedPolicy: WebOsPlaybackPolicy) {
        policy = requestedPolicy.copy(reconnectAttempts = requestedPolicy.reconnectAttempts.coerceIn(1, 10))
        update(snapshot.copy(reconnectAttempts = policy.reconnectAttempts))
        if (snapshot.state == PlaybackState.RECONNECTING && !policy.autoReconnect) cancelReconnect()
    }

    fun playing(token: Long) {
        if (!accepts(token) || retry != null) return
        cancelTimers()
        attempts = 0
        update(snapshot.copy(state = PlaybackState.PLAYING, detail = null, reconnectAttempt = null, technicalDetail = null))
        snapshot.channel?.id?.let(onPlaying)
    }

    fun bufferingStarted(token: Long) {
        if (!accepts(token) || retry != null || buffering != null || snapshot.state == PlaybackState.BUFFERING) return
        if (snapshot.state != PlaybackState.OPENING && snapshot.state != PlaybackState.PLAYING) return
        stateBeforeBuffering = snapshot.state
        val version = timerVersion
        buffering =
            scheduler.after(250) {
                if (accepts(token) && version == timerVersion && retry == null) {
                    update(snapshot.copy(state = PlaybackState.BUFFERING, detail = null))
                }
            }
    }

    fun bufferingEnded(token: Long) {
        if (!accepts(token) || (buffering == null && snapshot.state != PlaybackState.BUFFERING)) return
        val previous = stateBeforeBuffering
        timerVersion++
        buffering?.cancel()
        buffering = null
        stateBeforeBuffering = null
        if (snapshot.state == PlaybackState.BUFFERING && previous != null) update(snapshot.copy(state = previous))
    }

    fun failed(
        token: Long,
        reason: String?,
    ) {
        if (!accepts(token) || retry != null) return
        cancelTimers()
        val channel = snapshot.channel ?: return
        if (!policy.autoReconnect || attempts >= policy.reconnectAttempts) {
            stopMedia()
            finalFailure(reason)
            return
        }
        attempts++
        update(
            snapshot.copy(
                state = PlaybackState.RECONNECTING,
                detail = "${displayName(channel)} újracsatlakoztatása ($attempts/${policy.reconnectAttempts})…",
                reconnectAttempt = attempts,
                reconnectAttempts = policy.reconnectAttempts,
                technicalDetail = reason,
            ),
        )
        val version = timerVersion
        retry =
            scheduler.after(attempts * 1_000) {
                if (accepts(token) && version == timerVersion) {
                    retry = null
                    start(channel)
                }
            }
    }

    fun retry() {
        if (snapshot.state != PlaybackState.ERROR) return
        attempts = 0
        snapshot.channel?.let(::start)
    }

    fun cancelReconnect() {
        if (snapshot.state != PlaybackState.RECONNECTING) return
        generation++
        cancelTimers()
        stopMedia()
        finalFailure(snapshot.technicalDetail)
    }

    fun stop() {
        backgroundChannel = null
        backgrounded = false
        stopCurrentPlayback()
    }

    fun pauseForBackground() {
        if (backgrounded) return
        backgrounded = true
        val resumableChannel =
            snapshot.channel?.takeIf {
                snapshot.state in setOf(PlaybackState.OPENING, PlaybackState.BUFFERING, PlaybackState.PLAYING, PlaybackState.RECONNECTING)
            }
        stopCurrentPlayback()
        backgroundChannel = resumableChannel
    }

    fun resumeAfterBackground() {
        if (!backgrounded) return
        backgrounded = false
        val channel = backgroundChannel ?: return
        backgroundChannel = null
        attempts = 0
        start(channel)
    }

    private fun stopCurrentPlayback() {
        generation++
        cancelTimers()
        stopMedia()
        attempts = 0
        update(WebOsPlaybackSnapshot())
    }

    private fun start(channel: Channel) {
        cancelTimers()
        generation++
        update(
            WebOsPlaybackSnapshot(
                state = PlaybackState.OPENING,
                channel = channel,
                detail = "${displayName(channel)} betöltése…",
                reconnectAttempts = policy.reconnectAttempts,
            ),
        )
        stopMedia()
        try {
            startMedia(channel, generation)
        } catch (error: Throwable) {
            failed(generation, error.message)
        }
    }

    private fun finalFailure(reason: String?) {
        update(
            snapshot.copy(
                state = PlaybackState.ERROR,
                detail = "Lejátszási hiba",
                reconnectAttempt = null,
                reconnectAttempts = policy.reconnectAttempts,
                technicalDetail = reason ?: snapshot.technicalDetail,
            ),
        )
    }

    private fun accepts(token: Long): Boolean = token == generation && snapshot.state != PlaybackState.IDLE && snapshot.state != PlaybackState.ERROR

    private fun cancelTimers() {
        timerVersion++
        retry?.cancel()
        retry = null
        buffering?.cancel()
        buffering = null
        stateBeforeBuffering = null
    }

    private fun update(value: WebOsPlaybackSnapshot) {
        snapshot = value
        onSnapshot(value)
    }
}

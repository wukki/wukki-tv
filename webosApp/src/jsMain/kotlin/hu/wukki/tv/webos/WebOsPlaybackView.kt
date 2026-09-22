package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.PlaybackState
import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLVideoElement

internal class WebOsPlaybackView(
    private val appShell: WebOsAppShell,
    private val showStatus: (String) -> Unit,
    private val restoreChannelFocus: () -> Unit,
    private val recordSuccessfulPlayback: (String) -> Unit,
) {
    private val video = playbackElement<HTMLVideoElement>("player")
    private val liveEmpty = playbackElement<HTMLElement>("live-empty")
    private val nowPlaying = playbackElement<HTMLElement>("now-playing")
    private val channelNumber = playbackElement<HTMLElement>("live-channel-number")
    private val channelLogo = playbackElement<HTMLImageElement>("live-channel-logo")
    private val channelLogoFallback = playbackElement<HTMLElement>("live-channel-logo-fallback")
    private val previewLabel = playbackElement<HTMLElement>("live-preview-label")
    private val channelNumberInput = playbackElement<HTMLElement>("channel-number-input")
    private val stateOverlay = playbackElement<HTMLElement>("playback-state-overlay")
    private val stateTitle = playbackElement<HTMLElement>("playback-state-title")
    private val stateDetail = playbackElement<HTMLElement>("playback-state-detail")
    private val cancelReconnect = playbackElement<HTMLButtonElement>("cancel-reconnect")
    private val recoveryDialog = playbackElement<HTMLElement>("playback-recovery")
    private val technicalDetail = playbackElement<HTMLElement>("playback-technical-detail")
    private val retryPlayback = playbackElement<HTMLButtonElement>("retry-playback")
    private val openChannels = playbackElement<HTMLButtonElement>("open-channels-after-error")
    private val toggleDetails = playbackElement<HTMLButtonElement>("toggle-playback-details")
    private val stopButton = playbackElement<HTMLButtonElement>("stop")

    private val session =
        WebOsPlaybackSession(
            scheduler =
                WebOsPlaybackScheduler { delay, action ->
                    val timer = window.setTimeout(action, delay)
                    WebOsPlaybackCancellation { window.clearTimeout(timer) }
                },
            startMedia = ::startMedia,
            stopMedia = ::stopMedia,
            onSnapshot = ::renderPlaybackState,
            onPlaying = recordSuccessfulPlayback,
        )

    val hlsSupport = video.canPlayType("application/vnd.apple.mpegurl").toString().ifBlank { "nincs" }
    val playingChannelId: String? get() = session.snapshot.channel?.id
    val navigationVisible: Boolean get() = document.body?.classList?.contains("live-navigation-hidden") != true
    val recoveryVisible: Boolean get() = !recoveryDialog.hidden
    private var playingChannel: Channel? = null
    private var hudTimer: Int? = null
    private var navigationTimer: Int? = null
    private var recoveryActionIndex = 0

    fun configure() {
        stopButton.onclick = {
            stop()
            null
        }
        retryPlayback.onclick = {
            technicalDetail.hidden = true
            session.retry()
            null
        }
        openChannels.onclick = {
            stop()
            null
        }
        toggleDetails.onclick = {
            technicalDetail.hidden = !technicalDetail.hidden
            null
        }
        cancelReconnect.onclick = {
            session.cancelReconnect()
            null
        }
        video.onclick = {
            showHud()
            null
        }
        video.onmousemove = {
            showHud()
            null
        }
        channelLogo.addEventListener("error", {
            channelLogo.hidden = true
            channelLogoFallback.hidden = false
        })
    }

    fun start(
        channel: Channel,
        settings: WebOsSettings,
    ) {
        playingChannel = channel
        renderInformationPanel(channel, preview = false)
        document.body?.classList?.add("playback-active")
        liveEmpty.hidden = true
        appShell.activate(WebOsSection.LIVE)
        document.activeElement?.asDynamic()?.blur()
        showHud()
        showNavigation()
        session.play(
            channel,
            WebOsPlaybackPolicy(
                autoReconnect = settings.autoReconnect,
                reconnectAttempts = settings.reconnectAttempts,
            ),
        )
    }

    fun stop() {
        session.stop()
        leave()
        showStatus("Lejátszás leállítva.")
    }

    fun showHud() {
        if (!isActive() || appShell.activeSection != WebOsSection.LIVE || recoveryVisible) return
        document.body?.classList?.add("hud-visible")
        cancelHudTimer()
        hudTimer =
            window.setTimeout(
                {
                    hideHud()
                    hudTimer = null
                },
                liveLayerTimeoutMillis(LiveLayer.INFORMATION_PANEL) ?: 5_000,
            )
    }

    fun hideHud() {
        cancelHudTimer()
        document.body?.classList?.remove("hud-visible")
        if ((document.activeElement as? HTMLElement)?.classList?.contains("playback-action") == true) {
            (document.activeElement as? HTMLElement)?.blur()
            appShell.view(WebOsSection.LIVE).focus()
        }
    }

    fun showNavigation() {
        if (!isActive() || appShell.activeSection != WebOsSection.LIVE) return
        document.body?.classList?.remove("live-navigation-hidden")
        cancelNavigationTimer()
        navigationTimer =
            window.setTimeout(
                {
                    hideNavigation()
                    navigationTimer = null
                },
                liveLayerTimeoutMillis(LiveLayer.NAVIGATION) ?: 5_000,
            )
    }

    fun leaveLiveNavigation() {
        cancelNavigationTimer()
        document.body?.classList?.remove("live-navigation-hidden")
    }

    fun isActive(): Boolean = session.snapshot.state != PlaybackState.IDLE

    fun showPreview(channel: Channel?) {
        if (!isActive()) return
        if (channel == null) {
            playingChannel?.let { renderInformationPanel(it, preview = false) }
            hideHud()
        } else {
            renderInformationPanel(channel, preview = channel.id != playingChannelId)
            showHud()
        }
    }

    fun showChannelNumberInput(number: String?) {
        channelNumberInput.textContent = number.orEmpty()
        channelNumberInput.hidden = number.isNullOrEmpty()
    }

    fun handleRecoveryDialog(event: GuideProgrammeDialogEvent) {
        if (!recoveryVisible) return
        val actions = listOf(retryPlayback, openChannels, toggleDetails)
        when (event) {
            GuideProgrammeDialogEvent.LEFT -> recoveryActionIndex = (recoveryActionIndex - 1).coerceAtLeast(0)
            GuideProgrammeDialogEvent.RIGHT -> recoveryActionIndex = (recoveryActionIndex + 1).coerceAtMost(actions.lastIndex)
            GuideProgrammeDialogEvent.CONFIRM -> actions[recoveryActionIndex].click()
            GuideProgrammeDialogEvent.BACK -> openChannels.click()
        }
        if (recoveryVisible) actions[recoveryActionIndex].focus()
    }

    private fun startMedia(
        channel: Channel,
        generation: Long,
    ) {
        val source = channel.streamUrl
        video.onplaying = {
            if (isPlayableCurrentSource(source)) {
                session.bufferingEnded(generation)
                session.playing(generation)
                showStatus("Lejátszás: ${video.videoWidth}×${video.videoHeight} · ready=${video.readyState}")
                showHud()
            }
            null
        }
        video.onwaiting = {
            if (isCurrentSource(source)) session.bufferingStarted(generation)
            null
        }
        video.oncanplay = {
            if (isCurrentSource(source)) session.bufferingEnded(generation)
            null
        }
        video.onended = {
            if (isCurrentSource(source)) session.failed(generation, "A stream véget ért.")
            null
        }
        video.onerror = { _, _, _, _, _ ->
            if (isCurrentSource(source)) {
                session.failed(
                    generation,
                    "${mediaErrorName(video.error?.code)} (ready=${video.readyState}, network=${video.networkState})",
                )
            }
            null
        }
        video.src = source
        video.load()
        video.play().catch { error ->
            session.failed(generation, error.asDynamic().message ?: error.toString())
        }
    }

    private fun stopMedia() {
        video.onplaying = null
        video.onwaiting = null
        video.oncanplay = null
        video.onended = null
        video.onerror = null
        video.pause()
        video.removeAttribute("src")
        video.load()
    }

    private fun isCurrentSource(source: String): Boolean = session.snapshot.channel?.streamUrl == source && video.getAttribute("src") == source

    private fun isPlayableCurrentSource(source: String): Boolean = isCurrentSource(source) && !video.paused && video.readyState >= 3

    private fun renderPlaybackState(snapshot: WebOsPlaybackSnapshot) {
        when (snapshot.state) {
            PlaybackState.IDLE -> {
                stateOverlay.hidden = true
                recoveryDialog.hidden = true
            }

            PlaybackState.OPENING -> {
                showTransientState("Betöltés", snapshot.detail, reconnecting = false)
            }

            PlaybackState.BUFFERING -> {
                showTransientState("Pufferelés", null, reconnecting = false)
            }

            PlaybackState.RECONNECTING -> {
                showTransientState("Újracsatlakozás", snapshot.detail, reconnecting = true)
            }

            PlaybackState.PLAYING -> {
                stateOverlay.hidden = true
                recoveryDialog.hidden = true
            }

            PlaybackState.ERROR -> {
                showRecovery(snapshot)
            }
        }
    }

    private fun showTransientState(
        title: String,
        detail: String?,
        reconnecting: Boolean,
    ) {
        recoveryDialog.hidden = true
        stateOverlay.hidden = false
        stateTitle.textContent = title
        stateDetail.textContent = detail.orEmpty()
        stateDetail.hidden = detail.isNullOrBlank()
        cancelReconnect.hidden = !reconnecting
    }

    private fun showRecovery(snapshot: WebOsPlaybackSnapshot) {
        hideHud()
        stateOverlay.hidden = true
        recoveryDialog.hidden = false
        technicalDetail.textContent = snapshot.technicalDetail ?: "Nem érkezett technikai hibakód."
        technicalDetail.hidden = true
        recoveryActionIndex = 0
        retryPlayback.focus()
    }

    private fun leave() {
        cancelHudTimer()
        cancelNavigationTimer()
        document.body?.classList?.remove("playback-active", "hud-visible", "live-navigation-hidden")
        playingChannel = null
        showChannelNumberInput(null)
        stateOverlay.hidden = true
        recoveryDialog.hidden = true
        liveEmpty.hidden = false
        appShell.activate(WebOsSection.CHANNELS)
        restoreChannelFocus()
    }

    private fun renderInformationPanel(
        channel: Channel,
        preview: Boolean,
    ) {
        channelNumber.textContent = channel.tvgChno?.toString() ?: "–"
        nowPlaying.textContent = "Nincs műsoradat"
        previewLabel.hidden = !preview
        channelLogoFallback.textContent = displayName(channel)
        val logo = channel.logo?.takeIf(String::isNotBlank)
        channelLogo.hidden = logo == null
        channelLogoFallback.hidden = logo != null
        if (logo != null) channelLogo.src = logo
    }

    private fun hideNavigation() {
        if (!isActive() || appShell.activeSection != WebOsSection.LIVE || recoveryVisible) return
        document.body?.classList?.add("live-navigation-hidden")
        val active = document.activeElement as? HTMLElement
        if (active?.classList?.contains("nav-item") == true) {
            active.blur()
            appShell.view(WebOsSection.LIVE).focus()
        }
    }

    private fun cancelHudTimer() {
        hudTimer?.let(window::clearTimeout)
        hudTimer = null
    }

    private fun cancelNavigationTimer() {
        navigationTimer?.let(window::clearTimeout)
        navigationTimer = null
    }
}

private inline fun <reified T : HTMLElement> playbackElement(id: String): T = requireNotNull(document.getElementById(id)) { "Missing #$id" } as T

private fun mediaErrorName(code: Short?): String =
    when (code?.toInt()) {
        1 -> "megszakítva"
        2 -> "hálózati hiba"
        3 -> "dekódolási hiba"
        4 -> "nem támogatott médiaforrás"
        else -> "ismeretlen hibakód: $code"
    }

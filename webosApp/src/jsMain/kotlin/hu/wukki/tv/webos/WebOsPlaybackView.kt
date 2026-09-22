package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.KeyboardEvent

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
    val stopButton = playbackElement<HTMLButtonElement>("stop")

    val hlsSupport = video.canPlayType("application/vnd.apple.mpegurl").toString().ifBlank { "nincs" }
    var playingChannelId: String? = null
        private set
    private var playingChannel: Channel? = null
    private var playingIndex = 0
    private var hudTimer: Int? = null
    private var navigationTimer: Int? = null
    private var lastChannelSwitchAt = Double.NEGATIVE_INFINITY

    fun configure() {
        stopButton.onclick = {
            stop()
            null
        }
        video.onplaying = {
            playingChannelId?.let(recordSuccessfulPlayback)
            showStatus("Lejátszás: ${video.videoWidth}×${video.videoHeight} · ready=${video.readyState}")
            showHud()
            null
        }
        video.onwaiting = {
            showStatus("Pufferelés…")
            showHud()
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
        video.addEventListener(
            "error",
            {
                leave()
                showStatus(
                    "Lejátszási hiba: ${mediaErrorName(video.error?.code)} " +
                        "(ready=${video.readyState}, network=${video.networkState})",
                )
            },
        )
    }

    fun start(
        channel: Channel,
        sourceIndex: Int? = null,
    ) {
        if (sourceIndex != null) playingIndex = sourceIndex
        playingChannelId = channel.id
        val name = displayName(channel)
        playingChannel = channel
        renderInformationPanel(channel, preview = false)
        showStatus("Lejátszás indítása: $name · HLS: $hlsSupport")
        document.body?.classList?.add("playback-active")
        liveEmpty.setAttribute("hidden", "")
        appShell.activate(WebOsSection.LIVE)
        document.activeElement?.asDynamic()?.blur()
        showHud()
        showNavigation()
        when (playbackSourceAction(video.getAttribute("src"), channel.streamUrl, video.paused)) {
            PlaybackSourceAction.KEEP_PLAYING -> {
                return
            }

            PlaybackSourceAction.RESUME -> {
                playVideo()
            }

            PlaybackSourceAction.REPLACE -> {
                video.src = channel.streamUrl
                video.load()
                playVideo()
            }
        }
    }

    fun stop() {
        video.pause()
        video.removeAttribute("src")
        video.load()
        leave()
        showStatus("Lejátszás leállítva.")
    }

    fun switchChannel(
        channels: List<Channel>,
        event: KeyboardEvent,
        step: Int,
    ) {
        if (channels.isEmpty()) return
        val now = window.performance.now()
        if (!shouldHandleChannelSwitch(event.repeat, now, lastChannelSwitchAt)) return
        lastChannelSwitchAt = now
        val target = nextChannelIndex(playingIndex, channels.size, step)
        start(channels[target], target)
    }

    fun showHud() {
        if (!isActive() || appShell.activeSection != WebOsSection.LIVE) return
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

    val navigationVisible: Boolean
        get() = document.body?.classList?.contains("live-navigation-hidden") != true

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

    fun isActive(): Boolean = document.body?.classList?.contains("playback-active") == true

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

    private fun playVideo() {
        video.play().catch { error ->
            leave()
            showStatus("A lejátszás indítása sikertelen: ${error.asDynamic().message ?: error.toString()}")
        }
    }

    private fun leave() {
        cancelHudTimer()
        cancelNavigationTimer()
        document.body?.classList?.remove("playback-active", "hud-visible", "live-navigation-hidden")
        playingChannelId = null
        playingChannel = null
        showChannelNumberInput(null)
        liveEmpty.removeAttribute("hidden")
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
        if (!isActive() || appShell.activeSection != WebOsSection.LIVE) return
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

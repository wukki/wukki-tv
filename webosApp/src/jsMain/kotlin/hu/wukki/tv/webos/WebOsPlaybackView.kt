package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.KeyboardEvent

private const val HUD_HIDE_DELAY_MS = 5_000

internal class WebOsPlaybackView(
    private val appShell: WebOsAppShell,
    private val showStatus: (String) -> Unit,
    private val restoreChannelFocus: () -> Unit,
    private val recordSuccessfulPlayback: (String) -> Unit,
) {
    private val video = playbackElement<HTMLVideoElement>("player")
    private val liveEmpty = playbackElement<HTMLElement>("live-empty")
    private val nowPlaying = playbackElement<HTMLElement>("now-playing")
    val stopButton = playbackElement<HTMLButtonElement>("stop")

    val hlsSupport = video.canPlayType("application/vnd.apple.mpegurl").toString().ifBlank { "nincs" }
    private var playingChannelId: String? = null
    private var playingIndex = 0
    private var hudTimer: Int? = null
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
        nowPlaying.textContent = name
        showStatus("Lejátszás indítása: $name · HLS: $hlsSupport")
        document.body?.classList?.add("playback-active")
        liveEmpty.setAttribute("hidden", "")
        appShell.activate(WebOsSection.LIVE)
        document.activeElement?.asDynamic()?.blur()
        showHud()
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
                    document.body?.classList?.remove("hud-visible")
                    if (document.activeElement === stopButton) stopButton.blur()
                    hudTimer = null
                },
                HUD_HIDE_DELAY_MS,
            )
    }

    fun hideHud() {
        cancelHudTimer()
        document.body?.classList?.remove("hud-visible")
        if (document.activeElement === stopButton) stopButton.blur()
    }

    fun isActive(): Boolean = document.body?.classList?.contains("playback-active") == true

    private fun playVideo() {
        video.play().catch { error ->
            leave()
            showStatus("A lejátszás indítása sikertelen: ${error.asDynamic().message ?: error.toString()}")
        }
    }

    private fun leave() {
        cancelHudTimer()
        document.body?.classList?.remove("playback-active", "hud-visible")
        playingChannelId = null
        liveEmpty.removeAttribute("hidden")
        appShell.activate(WebOsSection.CHANNELS)
        restoreChannelFocus()
    }

    private fun cancelHudTimer() {
        hudTimer?.let(window::clearTimeout)
        hudTimer = null
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

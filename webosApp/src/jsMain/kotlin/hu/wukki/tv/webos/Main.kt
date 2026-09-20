package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.OTHER_CATEGORY_ID
import hu.wukki.tv.PlaylistParser
import hu.wukki.tv.UNKNOWN_CHANNEL_NAME_ID
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.fetch.Response
import kotlin.js.Promise

private const val BACK_KEY = 461
private const val OFFICIAL_PLAYLIST_URL = "https://raw.githubusercontent.com/wukki/wukki-tv/refs/heads/main/wukki-tv.m3u"
private const val DEFAULT_DIAGNOSTIC_STREAM_URL = "http://88.212.15.19/live/m2_hun/index.m3u8"
private const val WEBOS_PLAYLIST_ID = "webos-playlist"
private const val MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
private const val PLAYLIST_TIMEOUT_MS = 15_000
private const val HUD_HIDE_DELAY_MS = 5_000
private const val REPEATED_CHANNEL_SWITCH_INTERVAL_MS = 350

fun main() {
    WebOsApp().start()
}

private class WebOsApp {
    val sourceInput = element<HTMLInputElement>("source-url")
    val diagnosticInput = element<HTMLInputElement>("diagnostic-url")
    val loadPlaylist = element<HTMLButtonElement>("load-playlist")
    val playDirect = element<HTMLButtonElement>("play-direct")
    val stop = element<HTMLButtonElement>("stop")
    val video = element<HTMLVideoElement>("player")
    val channelList = element<HTMLElement>("channel-list")
    val channelCount = element<HTMLElement>("channel-count")
    val status = element<HTMLElement>("status")
    val nowPlaying = element<HTMLElement>("now-playing")
    val platform = element<HTMLElement>("platform")
    val channelButtons = mutableListOf<HTMLButtonElement>()
    var channels = emptyList<Channel>()
    private var playingIndex = 0
    private var playlistLoading = false
    private var selectedChannelId: String? = null
    private var hudTimer: Int? = null
    private var lastChannelSwitchAt = Double.NEGATIVE_INFINITY

    private val hlsSupport = video.canPlayType("application/vnd.apple.mpegurl").toString().ifBlank { "nincs" }

    fun start() {
        platform.textContent = "${window.navigator.userAgent} · HLS: $hlsSupport · Kotlin/JS core betöltve"
        sourceInput.value = OFFICIAL_PLAYLIST_URL
        diagnosticInput.value = requestedStream() ?: DEFAULT_DIAGNOSTIC_STREAM_URL
        configureControls()
        configurePlayerEvents()
        configureKeyboard()
        renderChannels()
        fetchPlaylist()
    }

    private fun requestedStream(): String? =
        window.location.search
            .removePrefix("?")
            .split('&')
            .firstOrNull { it.startsWith("stream=") }
            ?.substringAfter('=')
            ?.let(::decodeURIComponent)

    private fun show(message: String) {
        status.textContent = message
    }

    private fun displayName(channel: Channel): String = channel.name.takeUnless { it == UNKNOWN_CHANNEL_NAME_ID } ?: "Ismeretlen csatorna"

    private fun displayGroup(channel: Channel): String = channel.group.takeUnless { it == OTHER_CATEGORY_ID } ?: "Egyéb"

    private fun startPlayback(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        playingIndex = index
        selectedChannelId = channel.id
        updateSelectedChannel()
        val name = displayName(channel)
        nowPlaying.textContent = name
        show("Lejátszás indítása: $name · HLS: $hlsSupport")
        document.body?.classList?.add("playback-active")
        document.activeElement?.asDynamic()?.blur()
        showPlaybackHud()
        video.src = channel.streamUrl
        video.load()
        video.play().catch { error ->
            leavePlaybackView()
            show("A lejátszás indítása sikertelen: ${error.asDynamic().message ?: error.toString()}")
        }
    }

    private fun stopPlayback() {
        video.pause()
        video.removeAttribute("src")
        video.load()
        leavePlaybackView()
        show("Lejátszás leállítva.")
    }

    private fun leavePlaybackView() {
        cancelHudTimer()
        document.body?.classList?.remove("playback-active", "hud-visible")
        restoreChannelFocus()
    }

    private fun showPlaybackHud() {
        if (document.body?.classList?.contains("playback-active") != true) return
        document.body?.classList?.add("hud-visible")
        cancelHudTimer()
        hudTimer =
            window.setTimeout(
                {
                    document.body?.classList?.remove("hud-visible")
                    if (document.activeElement === stop) stop.blur()
                    hudTimer = null
                },
                HUD_HIDE_DELAY_MS,
            )
    }

    private fun hidePlaybackHud() {
        cancelHudTimer()
        document.body?.classList?.remove("hud-visible")
        if (document.activeElement === stop) stop.blur()
    }

    private fun cancelHudTimer() {
        hudTimer?.let(window::clearTimeout)
        hudTimer = null
    }

    private fun renderChannels() {
        channelList.innerHTML = ""
        channelButtons.clear()
        channelCount.textContent = "${channels.size} csatorna"
        if (channels.none { it.id == selectedChannelId }) selectedChannelId = channels.firstOrNull()?.id
        channels.forEachIndexed { index, channel ->
            val button = document.createElement("button") as HTMLButtonElement
            button.type = "button"
            button.className = "channel"
            button.setAttribute("data-channel-id", channel.id)

            val number = document.createElement("span") as HTMLElement
            number.className = "channel-number"
            number.textContent = (channel.tvgChno ?: index + 1).toString()
            val text = document.createElement("span") as HTMLElement
            text.className = "channel-text"
            val name = document.createElement("strong") as HTMLElement
            name.textContent = displayName(channel)
            val group = document.createElement("small") as HTMLElement
            group.textContent = displayGroup(channel)
            text.appendChild(name)
            text.appendChild(group)
            button.appendChild(number)
            button.appendChild(text)
            button.onfocus = {
                selectedChannelId = channel.id
                updateSelectedChannel()
                scrollIntoView(button)
                null
            }
            button.onmouseover = {
                button.focus()
                null
            }
            button.onclick = {
                selectedChannelId = channel.id
                startPlayback(index)
                null
            }
            channelList.appendChild(button)
            channelButtons += button
        }
        updateSelectedChannel()
    }

    private fun updateSelectedChannel() {
        channelButtons.forEach { button ->
            if (button.getAttribute("data-channel-id") == selectedChannelId) {
                button.setAttribute("aria-current", "true")
            } else {
                button.removeAttribute("aria-current")
            }
        }
    }

    private fun restoreChannelFocus() {
        val selected = channelButtons.firstOrNull { it.getAttribute("data-channel-id") == selectedChannelId }
        focusAndReveal(selected ?: channelButtons.getOrNull(playingIndex) ?: loadPlaylist)
    }

    private fun useDirectStream() {
        val url = diagnosticInput.value.trim()
        if (url.isEmpty()) {
            show("Adj meg egy stream URL-t.")
            diagnosticInput.focus()
            return
        }
        channels = listOf(probeChannel(url))
        renderChannels()
        startPlayback(0)
    }

    private fun fetchPlaylist() {
        if (playlistLoading) return
        val url = OFFICIAL_PLAYLIST_URL
        playlistLoading = true
        loadPlaylist.disabled = true
        loadPlaylist.textContent = "Betöltés…"
        show("Hivatalos csatornalista betöltése…")
        fetchPlaylistText(url)
            .then { text ->
                val parsed =
                    PlaylistParser.parse(text, WEBOS_PLAYLIST_ID, url)
                if (parsed.isEmpty()) {
                    throw IllegalArgumentException("A letöltött fájl nem tartalmaz lejátszható csatornát.")
                } else {
                    channels = parsed
                    renderChannels()
                    show("${channels.size} csatorna betöltve.")
                    channelButtons.firstOrNull()?.focus()
                }
                finishPlaylistLoad()
            }.catch { error ->
                finishPlaylistLoad(retry = true)
                show("A playlist nem tölthető be: ${error.asDynamic().message ?: error.toString()}")
            }
    }

    private fun finishPlaylistLoad(retry: Boolean = false) {
        playlistLoading = false
        loadPlaylist.disabled = false
        loadPlaylist.textContent = if (retry) "Újrapróbálás" else "Playlist frissítése"
    }

    private fun switchChannel(step: Int) {
        if (channels.isEmpty()) return
        startPlayback(nextChannelIndex(playingIndex, channels.size, step))
    }

    private fun configureControls() {
        loadPlaylist.onclick = {
            fetchPlaylist()
            null
        }
        playDirect.onclick = {
            useDirectStream()
            null
        }
        stop.onclick = {
            stopPlayback()
            null
        }
    }

    private fun configurePlayerEvents() {
        video.onplaying = {
            show("Lejátszás: ${video.videoWidth}×${video.videoHeight} · ready=${video.readyState}")
            showPlaybackHud()
            null
        }
        video.onwaiting = {
            show("Pufferelés…")
            showPlaybackHud()
            null
        }
        video.onclick = {
            showPlaybackHud()
            null
        }
        video.onmousemove = {
            showPlaybackHud()
            null
        }
        video.addEventListener(
            "error",
            {
                leavePlaybackView()
                show(
                    "Lejátszási hiba: ${mediaErrorName(video.error?.code)} " +
                        "(ready=${video.readyState}, network=${video.networkState})",
                )
            },
        )
    }

    private fun focusableElements(): List<HTMLElement> = (listOf(loadPlaylist, diagnosticInput, playDirect) + channelButtons).filter(::isFocusable)

    private fun moveFocus(step: Int) {
        val focusable = focusableElements()
        if (focusable.isEmpty()) return
        val current = focusable.indexOfFirst { it === document.activeElement }.coerceAtLeast(0)
        focusAndReveal(focusable[(current + step + focusable.size) % focusable.size])
    }

    private fun configureKeyboard() {
        document.onkeydown = { rawEvent: Event ->
            handleKey(rawEvent as KeyboardEvent)
            null
        }
    }

    private fun handleKey(event: KeyboardEvent) {
        val playbackActive = document.body?.classList?.contains("playback-active") == true
        when (event.keyCode) {
            37, 38 -> {
                event.preventDefault()
                if (playbackActive) handleChannelSwitch(event, -1) else moveFocus(-1)
            }

            39, 40 -> {
                event.preventDefault()
                if (playbackActive) handleChannelSwitch(event, 1) else moveFocus(1)
            }

            13 -> {
                if (playbackActive) {
                    event.preventDefault()
                    showPlaybackHud()
                } else if (document.activeElement === diagnosticInput) {
                    event.preventDefault()
                    useDirectStream()
                }
            }

            BACK_KEY -> {
                event.preventDefault()
                when {
                    playbackActive && document.body?.classList?.contains("hud-visible") == true -> hidePlaybackHud()
                    playbackActive -> stopPlayback()
                    else -> platformBack()
                }
            }
        }
    }

    private fun handleChannelSwitch(
        event: KeyboardEvent,
        step: Int,
    ) {
        val now = window.performance.now()
        if (!shouldHandleChannelSwitch(event.repeat, now, lastChannelSwitchAt)) return
        lastChannelSwitchAt = now
        switchChannel(step)
        showPlaybackHud()
    }
}

internal fun nextChannelIndex(
    current: Int,
    channelCount: Int,
    step: Int,
): Int {
    require(channelCount > 0)
    return ((current + step) % channelCount + channelCount) % channelCount
}

internal fun shouldHandleChannelSwitch(
    repeated: Boolean,
    nowMs: Double,
    lastSwitchMs: Double,
): Boolean = !repeated || nowMs - lastSwitchMs >= REPEATED_CHANNEL_SWITCH_INTERVAL_MS

private fun isFocusable(element: HTMLElement): Boolean {
    if (element.asDynamic().disabled == true || element.getAttribute("aria-hidden") == "true") return false
    val style = window.getComputedStyle(element)
    return style.display != "none" && style.visibility != "hidden" && element.asDynamic().getClientRects().length > 0
}

private fun focusAndReveal(element: HTMLElement) {
    if (!isFocusable(element)) return
    element.focus()
    scrollIntoView(element)
}

private fun scrollIntoView(element: HTMLElement) {
    element.asDynamic().scrollIntoView(js("({block: 'nearest'})"))
}

private fun fetchPlaylistText(url: String): Promise<String> =
    Promise { resolve, reject ->
        val controller = newAbortController()
        val options = js("({})")
        if (controller != null) options.signal = controller.signal
        val timeout =
            window.setTimeout(
                {
                    controller?.abort()
                    reject(Throwable("A letöltés túllépte a ${PLAYLIST_TIMEOUT_MS / 1000} másodperces időkorlátot."))
                },
                PLAYLIST_TIMEOUT_MS,
            )
        window
            .fetch(url, options)
            .then { response -> validateResponse(response) }
            .then { text ->
                window.clearTimeout(timeout)
                resolve(validatePlaylistBody(text))
            }.catch { error ->
                window.clearTimeout(timeout)
                reject(error)
            }
    }

private fun validateResponse(response: Response): Promise<String> {
    validatePlaylistResponse(response.status.toInt(), response.statusText, response.headers.get("Content-Length")?.toIntOrNull())
    return response.text()
}

internal fun validatePlaylistResponse(
    status: Int,
    statusText: String,
    declaredSize: Int?,
) {
    if (status !in 200..299) throw IllegalStateException("HTTP $status $statusText".trim())
    if (declaredSize != null && declaredSize > MAX_PLAYLIST_BYTES) {
        throw IllegalArgumentException("A playlist túl nagy: $declaredSize bájt, maximum $MAX_PLAYLIST_BYTES bájt lehet.")
    }
}

internal fun validatePlaylistBody(text: String): String {
    val size = text.encodeToByteArray().size
    require(size <= MAX_PLAYLIST_BYTES) {
        "A playlist túl nagy: $size bájt, maximum $MAX_PLAYLIST_BYTES bájt lehet."
    }
    return text
}

private fun newAbortController(): dynamic = js("typeof AbortController === 'undefined' ? null : new AbortController()")

private fun probeChannel(url: String): Channel =
    Channel(
        id = "webos-playback-probe",
        playlistId = "webos-probe",
        name = "M2 tesztstream",
        streamUrl = url,
        tvgId = null,
        tvgName = null,
        group = "Teszt",
        logo = null,
    )

private fun platformBack() {
    val webOS = window.asDynamic().webOS
    if (webOS != null && webOS.platformBack != null) {
        webOS.platformBack()
    } else {
        window.history.back()
    }
}

private inline fun <reified T : HTMLElement> element(id: String): T = requireNotNull(document.getElementById(id)) { "Missing #$id" } as T

private fun decodeURIComponent(value: String): String = js("decodeURIComponent(value)") as String

private fun mediaErrorName(code: Short?): String =
    when (code?.toInt()) {
        1 -> "megszakítva"
        2 -> "hálózati hiba"
        3 -> "dekódolási hiba"
        4 -> "nem támogatott médiaforrás"
        else -> "ismeretlen hibakód: $code"
    }

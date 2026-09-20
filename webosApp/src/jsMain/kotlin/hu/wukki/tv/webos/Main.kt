package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

private const val BACK_KEY = 461
private const val DEFAULT_STREAM_URL = "http://88.212.15.19/live/m2_hun/index.m3u8"

fun main() {
    val input = element<HTMLInputElement>("stream-url")
    val video = element<HTMLVideoElement>("player")
    val play = element<HTMLButtonElement>("play")
    val stop = element<HTMLButtonElement>("stop")
    val status = element<HTMLElement>("status")
    val platform = element<HTMLElement>("platform")

    platform.textContent = "${window.navigator.userAgent} · Kotlin/JS core betöltve"
    val requestedStream =
        window.location.search
            .removePrefix("?")
            .split('&')
            .firstOrNull { it.startsWith("stream=") }
            ?.substringAfter('=')
            ?.let(::decodeURIComponent)
    input.value = requestedStream ?: DEFAULT_STREAM_URL

    fun show(message: String) {
        status.textContent = message
    }

    fun startPlayback() {
        val url = input.value.trim()
        if (url.isEmpty()) {
            show("Adj meg egy stream URL-t.")
            input.focus()
            return
        }
        val channel = probeChannel(url)
        show("Megnyitás: ${channel.name}")
        video.src = channel.streamUrl
        video.load()
        video.play()
    }

    fun stopPlayback() {
        video.pause()
        video.removeAttribute("src")
        video.load()
        show("Lejátszás leállítva.")
    }

    play.onclick = {
        startPlayback()
        null
    }
    stop.onclick = {
        stopPlayback()
        null
    }
    video.onplaying = {
        show("Lejátszás folyamatban.")
        null
    }
    video.oncanplay = {
        if (!video.paused) show("Lejátszás folyamatban.")
        null
    }
    video.onwaiting = {
        show("Pufferelés…")
        null
    }
    video.addEventListener("error", { show("Lejátszási hiba: ${video.error?.code ?: "ismeretlen"}") })

    val focusable = listOf(input, play, stop)

    fun moveFocus(step: Int) {
        val current = focusable.indexOfFirst { it === document.activeElement }.coerceAtLeast(0)
        focusable[(current + step + focusable.size) % focusable.size].focus()
    }

    document.onkeydown = { rawEvent: Event ->
        val event = rawEvent as KeyboardEvent
        when (event.keyCode) {
            37, 38 -> {
                event.preventDefault()
                moveFocus(-1)
            }

            39, 40 -> {
                event.preventDefault()
                moveFocus(1)
            }

            13 -> {
                if (document.activeElement === input) {
                    event.preventDefault()
                    startPlayback()
                }
            }

            BACK_KEY -> {
                event.preventDefault()
                platformBack()
            }
        }
        null
    }

    play.focus()
}

private fun probeChannel(url: String): Channel =
    Channel(
        id = "webos-playback-probe",
        playlistId = "webos-probe",
        name = "webOS tesztstream",
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

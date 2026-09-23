package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.PlaybackState
import hu.wukki.tv.ProgrammePair
import hu.wukki.tv.programmeProgress
import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLVideoElement
import kotlin.js.Date

internal class WebOsPlaybackView(
    private val appShell: WebOsAppShell,
    private val localizer: WebOsLocalizer,
    private val showStatus: (String) -> Unit,
    private val restoreChannelFocus: () -> Unit,
    private val recordSuccessfulPlayback: (String) -> Unit,
) {
    private val video = playbackElement<HTMLVideoElement>("player")
    private val liveEmpty = playbackElement<HTMLElement>("live-empty")
    private val nowPlaying = playbackElement<HTMLElement>("now-playing")
    private val programmeTime = playbackElement<HTMLElement>("live-programme-time")
    private val programmeProgressBar = playbackElement<HTMLElement>("live-programme-progress")
    private val programmeProgressValue = playbackElement<HTMLElement>("live-programme-progress-value")
    private val nextProgramme = playbackElement<HTMLElement>("live-next-programme")
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
    private var playingProgrammes = ProgrammePair(null, null)
    private var hudTimer: Int? = null
    private var navigationTimer: Int? = null
    private var recoveryActionIndex = 0
    private var persistentSettings = WebOsSettings()
    private var temporaryAspectRatio: String? = null
    val effectiveAspectRatio: String get() = temporaryAspectRatio ?: persistentSettings.aspectRatio

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
        programmes: ProgrammePair = ProgrammePair(null, null),
        now: Long = Date.now().toLong(),
    ) {
        temporaryAspectRatio = null
        updateSettings(settings)
        playingChannel = channel
        playingProgrammes = programmes
        renderInformationPanel(channel, programmes, now, preview = false)
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

    fun updateSettings(settings: WebOsSettings) {
        persistentSettings = settings
        video.volume = settings.volume.coerceIn(0, 100) / 100.0
        session.updatePolicy(WebOsPlaybackPolicy(settings.autoReconnect, settings.reconnectAttempts))
        applyAspectRatio()
    }

    fun setTemporaryAspectRatio(value: String) {
        temporaryAspectRatio = value
        applyAspectRatio()
    }

    fun stop() {
        session.stop()
        leave()
        showStatus(if (localizer.language == "ENGLISH") "Playback stopped." else "Lejátszás leállítva.")
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

    fun showPreview(
        channel: Channel?,
        programmes: ProgrammePair = ProgrammePair(null, null),
        now: Long = Date.now().toLong(),
    ) {
        if (!isActive()) return
        if (channel == null) {
            playingChannel?.let { renderInformationPanel(it, playingProgrammes, now, preview = false) }
            hideHud()
        } else {
            renderInformationPanel(channel, programmes, now, preview = channel.id != playingChannelId)
            showHud()
        }
    }

    fun updateProgramme(
        channel: Channel,
        programmes: ProgrammePair,
        now: Long,
    ) {
        if (channel.id != playingChannelId) return
        playingProgrammes = programmes
        renderInformationPanel(channel, programmes, now, preview = false)
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
                val detail = snapshot.channel?.let { localizer.text("playback.channel.opening", displayName(it)) }
                showTransientState(localizer.text("playback.opening"), detail, reconnecting = false)
            }

            PlaybackState.BUFFERING -> {
                showTransientState(localizer.text("playback.buffering"), null, reconnecting = false)
            }

            PlaybackState.RECONNECTING -> {
                val detail =
                    snapshot.channel?.let {
                        localizer.text("playback.reconnect.attempt", displayName(it), snapshot.reconnectAttempt ?: 1, snapshot.reconnectAttempts)
                    }
                showTransientState(localizer.text("playback.reconnecting"), detail, reconnecting = true)
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
        technicalDetail.textContent = snapshot.technicalDetail ?: if (localizer.language == "ENGLISH") "No technical error code was provided." else "Nem érkezett technikai hibakód."
        technicalDetail.hidden = true
        recoveryActionIndex = 0
        retryPlayback.focus()
    }

    private fun leave() {
        cancelHudTimer()
        cancelNavigationTimer()
        document.body?.classList?.remove("playback-active", "hud-visible", "live-navigation-hidden")
        playingChannel = null
        temporaryAspectRatio = null
        showChannelNumberInput(null)
        stateOverlay.hidden = true
        recoveryDialog.hidden = true
        liveEmpty.hidden = false
        appShell.activate(WebOsSection.CHANNELS)
        restoreChannelFocus()
    }

    private fun applyAspectRatio() {
        document.body?.classList?.remove("aspect-auto", "aspect-ratio-16-9", "aspect-ratio-4-3", "aspect-ratio-21-9", "aspect-fill-crop")
        val className =
            when (effectiveAspectRatio) {
                "RATIO_16_9" -> "aspect-ratio-16-9"
                "RATIO_4_3" -> "aspect-ratio-4-3"
                "RATIO_21_9" -> "aspect-ratio-21-9"
                "FILL_CROP" -> "aspect-fill-crop"
                else -> "aspect-auto"
            }
        document.body?.classList?.add(className)
    }

    private fun renderInformationPanel(
        channel: Channel,
        programmes: ProgrammePair,
        now: Long,
        preview: Boolean,
    ) {
        channelNumber.textContent = channel.tvgChno?.toString() ?: "–"
        val current = programmes.current
        nowPlaying.textContent = current?.title?.ifBlank { localizer.text("epg.untitled") } ?: localizer.text("epg.none")
        programmeTime.textContent = current?.let { "${formatProgrammeTime(it.start)} – ${formatProgrammeTime(it.end)}" }.orEmpty()
        programmeTime.hidden = current == null
        val progress = programmeProgress(current, now)
        programmeProgressBar.hidden = progress == null
        programmeProgressBar.setAttribute("aria-valuenow", ((progress ?: 0.0) * 100).toInt().toString())
        programmeProgressValue.style.width = "${(progress ?: 0.0) * 100}%"
        nextProgramme.textContent = programmes.next?.let { "${localizer.text("epg.next")}: ${it.title.ifBlank { localizer.text("epg.untitled") }} · ${formatProgrammeTime(it.start)}" }.orEmpty()
        nextProgramme.hidden = programmes.next == null
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

private fun formatProgrammeTime(timestamp: Long): String {
    val date = Date(timestamp.toDouble())
    return "${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}"
}

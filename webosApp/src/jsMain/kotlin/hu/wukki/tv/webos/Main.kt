package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.OTHER_CATEGORY_ID
import hu.wukki.tv.PlaylistParser
import hu.wukki.tv.UNKNOWN_CHANNEL_NAME_ID
import hu.wukki.tv.normalizedChannelHistory
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.fetch.Response
import kotlin.js.Date
import kotlin.js.Promise

private const val BACK_KEY = 461
private const val OFFICIAL_PLAYLIST_URL = "https://raw.githubusercontent.com/wukki/wukki-tv/refs/heads/main/wukki-tv.m3u"
private const val DEFAULT_DIAGNOSTIC_STREAM_URL = "http://88.212.15.19/live/m2_hun/index.m3u8"
private const val WEBOS_PLAYLIST_ID = "webos-playlist"
private const val MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
private const val PLAYLIST_TIMEOUT_MS = 15_000
private const val REPEATED_CHANNEL_SWITCH_INTERVAL_MS = 350

fun main() {
    WebOsApp().start()
}

private class WebOsApp {
    val sourceInput = element<HTMLInputElement>("source-url")
    val diagnosticInput = element<HTMLInputElement>("diagnostic-url")
    val loadPlaylist = element<HTMLButtonElement>("load-playlist")
    val playDirect = element<HTMLButtonElement>("play-direct")
    val channelSearch = element<HTMLInputElement>("channel-search")
    val categoryFilter = element<HTMLButtonElement>("category-filter")
    val clearFilters = element<HTMLButtonElement>("clear-filters")
    val channelList = element<HTMLElement>("channel-list")
    val channelEmpty = element<HTMLElement>("channel-empty")
    val channelCount = element<HTMLElement>("channel-count")
    val status = element<HTMLElement>("status")
    val platform = element<HTMLElement>("platform")
    val channelButtons = mutableListOf<HTMLButtonElement>()
    private val appShell = WebOsAppShell(::onSectionActivated)
    private val playback = WebOsPlaybackView(appShell, ::show, ::restoreChannelFocus, ::recordSuccessfulPlayback)
    var channels = emptyList<Channel>()
    private var filteredChannels = emptyList<Channel>()
    private var categories = listOf<String?>(null)
    private var selectedCategory: String? = null
    private var renderedWindow = ChannelRenderWindow(0, 0)
    private var cachedChannels = emptyList<Channel>()
    private var playlistLoading = false
    private var selectedChannelId: String? = null
    private var lastSuccessfulChannelId: String? = null
    private var recentChannelIds = emptyList<String>()
    private var settings = WebOsSettings()
    private var playlistCachedAt = 0L
    private var storageProblem: String? = null

    private val stateStore =
        WebOsStateStore(
            read = { window.localStorage.getItem(WEBOS_STATE_STORAGE_KEY) },
            write = { value -> window.localStorage.setItem(WEBOS_STATE_STORAGE_KEY, value) },
        )

    fun start() {
        platform.textContent = "${window.navigator.userAgent} · HLS: ${playback.hlsSupport} · Kotlin/JS core betöltve"
        sourceInput.value = OFFICIAL_PLAYLIST_URL
        diagnosticInput.value = requestedStream() ?: DEFAULT_DIAGNOSTIC_STREAM_URL
        configureControls()
        playback.configure()
        configureKeyboard()
        appShell.configure()
        restoreCachedState()
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
        status.textContent = listOfNotNull(message, storageProblem).joinToString(" · ")
    }

    private fun startPlayback(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        selectedChannelId = channel.id
        updateSelectedChannel()
        playback.start(channel, index)
    }

    private fun onSectionActivated(section: WebOsSection) {
        if (section == WebOsSection.LIVE) {
            if (playback.isActive()) playback.showHud()
        } else {
            playback.hideHud()
        }
        if (section == WebOsSection.CHANNELS) renderChannelWindow()
    }

    private fun renderChannels() {
        val categoryOptions = sortedChannelCategories(channels)
        categories = listOf(null) + categoryOptions
        if (selectedCategory !in categoryOptions) selectedCategory = null
        filteredChannels = filterAndSortChannels(channels, channelSearch.value, selectedCategory)
        categoryFilter.textContent = "Kategória: ${selectedCategory?.let(::displayGroup) ?: "Összes"}"
        clearFilters.disabled = channelSearch.value.isBlank() && selectedCategory == null
        channelCount.textContent = "${filteredChannels.size} / ${channels.size} csatorna"
        channelEmpty.hidden = filteredChannels.isNotEmpty()
        channelEmpty.textContent = "Nincs találat. Töröld a keresést vagy válassz másik kategóriát."
        channelList.scrollTop = 0.0
        renderedWindow = ChannelRenderWindow(-1, -1)
        renderChannelWindow()
        updateSelectedChannel()
    }

    private fun renderChannelWindow(focusIndex: Int? = null) {
        val window = calculateChannelRenderWindow(filteredChannels.size, channelList.scrollTop, channelList.clientHeight)
        if (window == renderedWindow) {
            focusIndex?.let(::focusRenderedChannel)
            return
        }

        val previouslyFocusedIndex = focusedChannelIndex()
        channelList.innerHTML = ""
        channelButtons.clear()
        renderedWindow = window
        channelList.appendChild(channelSpacer(window.start * VIRTUAL_CHANNEL_ROW_HEIGHT))
        filteredChannels.subList(window.start, window.endExclusive).forEachIndexed { offset, channel ->
            val filteredIndex = window.start + offset
            val sourceIndex = channels.indexOfFirst { it.id == channel.id }
            val button = document.createElement("button") as HTMLButtonElement
            button.type = "button"
            button.className = "channel"
            button.setAttribute("data-channel-id", channel.id)
            button.setAttribute("data-filtered-index", filteredIndex.toString())

            val number = document.createElement("span") as HTMLElement
            number.className = "channel-number"
            number.textContent = (channel.tvgChno ?: sourceIndex + 1).toString()
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
                null
            }
            button.onmouseover = {
                button.focus()
                null
            }
            button.onclick = {
                selectedChannelId = channel.id
                startPlayback(sourceIndex)
                null
            }
            channelList.appendChild(button)
            channelButtons += button
        }
        channelList.appendChild(channelSpacer((filteredChannels.size - window.endExclusive) * VIRTUAL_CHANNEL_ROW_HEIGHT))
        updateSelectedChannel()
        (focusIndex ?: previouslyFocusedIndex)?.let(::focusRenderedChannel)
    }

    private fun channelSpacer(height: Int): HTMLElement =
        (document.createElement("div") as HTMLElement).apply {
            className = "channel-spacer"
            style.height = "${height}px"
            setAttribute("aria-hidden", "true")
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
        val selectedIndex = filteredChannels.indexOfFirst { it.id == selectedChannelId }
        if (filteredChannels.isNotEmpty()) {
            focusChannelAt(selectedIndex.takeIf { it >= 0 } ?: 0)
        } else {
            focusAndReveal(channelSearch)
        }
    }

    private fun focusChannelAt(index: Int) {
        if (filteredChannels.isEmpty()) {
            focusAndReveal(channelSearch)
            return
        }
        val target = index.coerceIn(filteredChannels.indices)
        val rowTop = target * VIRTUAL_CHANNEL_ROW_HEIGHT
        val rowBottom = rowTop + VIRTUAL_CHANNEL_ROW_HEIGHT
        val viewportBottom = channelList.scrollTop + channelList.clientHeight
        when {
            rowTop < channelList.scrollTop -> channelList.scrollTop = rowTop.toDouble()
            rowBottom > viewportBottom -> channelList.scrollTop = (rowBottom - channelList.clientHeight).coerceAtLeast(0).toDouble()
        }
        renderChannelWindow(target)
    }

    private fun focusRenderedChannel(index: Int) {
        val button = channelButtons.firstOrNull { it.getAttribute("data-filtered-index")?.toIntOrNull() == index } ?: return
        button.focus()
    }

    private fun focusedChannelIndex(): Int? = (document.activeElement as? HTMLElement)?.getAttribute("data-filtered-index")?.toIntOrNull()

    private fun useDirectStream() {
        val url = diagnosticInput.value.trim()
        if (url.isEmpty()) {
            show("Adj meg egy stream URL-t.")
            diagnosticInput.focus()
            return
        }
        playback.start(probeChannel(url))
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
                    cachedChannels = parsed
                    channels = parsed
                    playlistCachedAt = Date.now().toLong()
                    lastSuccessfulChannelId = lastSuccessfulChannelId?.takeIf { id -> parsed.any { it.id == id } }
                    recentChannelIds = normalizedChannelHistory(recentChannelIds, parsed)
                    renderChannels()
                    persistState()
                    show("${channels.size} csatorna betöltve.")
                    if (!playback.isActive() && appShell.activeSection == WebOsSection.CHANNELS) restoreChannelFocus()
                }
                finishPlaylistLoad()
            }.catch { error ->
                finishPlaylistLoad(retry = true)
                val detail = error.asDynamic().message ?: error.toString()
                if (cachedChannels.isEmpty()) {
                    show("A playlist nem tölthető be: $detail")
                } else {
                    show("A hálózati frissítés sikertelen; a mentett lista böngészhető, de a lejátszáshoz hálózat kell: $detail")
                }
            }
    }

    private fun finishPlaylistLoad(retry: Boolean = false) {
        playlistLoading = false
        loadPlaylist.disabled = false
        loadPlaylist.textContent = if (retry) "Újrapróbálás" else "Playlist frissítése"
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
        channelSearch.oninput = {
            renderChannels()
            null
        }
        categoryFilter.onclick = {
            val current = categories.indexOf(selectedCategory).coerceAtLeast(0)
            selectedCategory = categories[(current + 1) % categories.size]
            renderChannels()
            categoryFilter.focus()
            null
        }
        clearFilters.onclick = {
            channelSearch.value = ""
            selectedCategory = null
            renderChannels()
            channelSearch.focus()
            null
        }
        channelList.onscroll = {
            renderChannelWindow()
            null
        }
    }

    private fun moveFocus(step: Int) {
        val channelIndex = focusedChannelIndex()
        if (channelIndex != null) {
            val target = channelIndex + step
            when {
                target in filteredChannels.indices -> focusChannelAt(target)
                target < 0 -> focusAndReveal(filterControls().lastOrNull() ?: loadPlaylist)
                else -> focusAndReveal(filterControls().firstOrNull() ?: loadPlaylist)
            }
            return
        }

        val controls = filterControls()
        if (controls.isEmpty()) return
        val current = controls.indexOfFirst { it === document.activeElement }.coerceAtLeast(0)
        val target = current + step
        when {
            target >= controls.size && filteredChannels.isNotEmpty() -> focusChannelAt(0)
            target < 0 && filteredChannels.isNotEmpty() -> focusChannelAt(filteredChannels.lastIndex)
            else -> focusAndReveal(controls[(target + controls.size) % controls.size])
        }
    }

    private fun filterControls(): List<HTMLElement> =
        when (appShell.activeSection) {
            WebOsSection.CHANNELS -> listOf(channelSearch, categoryFilter, clearFilters)
            WebOsSection.SETTINGS -> listOf(loadPlaylist, diagnosticInput, playDirect)
            WebOsSection.LIVE -> listOf(playback.stopButton)
            WebOsSection.GUIDE -> emptyList()
        }.filter(::isFocusable)

    private fun configureKeyboard() {
        document.onkeydown = { rawEvent: Event ->
            handleKey(rawEvent as KeyboardEvent)
            null
        }
    }

    private fun handleKey(event: KeyboardEvent) {
        if (appShell.handleNavigationKey(event, ::focusSectionContent)) return
        when (event.keyCode) {
            38 -> handleDirectionalKey(event, -1, allowInTextInput = true)
            40 -> handleDirectionalKey(event, 1, allowInTextInput = true)
            37 -> handleDirectionalKey(event, -1, allowInTextInput = false)
            39 -> handleDirectionalKey(event, 1, allowInTextInput = false)
            13 -> handleEnterKey(event)
            BACK_KEY -> handleBackKey(event)
        }
    }

    private fun focusSectionContent() {
        when (appShell.activeSection) {
            WebOsSection.CHANNELS -> {
                focusAndReveal(filterControls().firstOrNull() ?: channelList)
            }

            WebOsSection.SETTINGS -> {
                val firstCategory = document.querySelector("#view-settings .settings-categories button") as? HTMLElement
                focusAndReveal(firstCategory ?: filterControls().firstOrNull() ?: appShell.view(WebOsSection.SETTINGS))
            }

            WebOsSection.LIVE -> {
                focusAndReveal(if (playback.isActive()) playback.stopButton else appShell.view(WebOsSection.LIVE))
            }

            WebOsSection.GUIDE -> {
                focusAndReveal(appShell.view(WebOsSection.GUIDE))
            }
        }
    }

    private fun handleDirectionalKey(
        event: KeyboardEvent,
        step: Int,
        allowInTextInput: Boolean,
    ) {
        val playbackActive = playback.isActive() && appShell.activeSection == WebOsSection.LIVE
        val textInputFocused = document.activeElement === diagnosticInput || document.activeElement === channelSearch
        if (!allowInTextInput && textInputFocused && !playbackActive) return
        event.preventDefault()
        if (playbackActive) playback.switchChannel(channels, event, step) else moveFocus(step)
    }

    private fun handleEnterKey(event: KeyboardEvent) {
        when {
            playback.isActive() && appShell.activeSection == WebOsSection.LIVE -> {
                event.preventDefault()
                playback.showHud()
            }

            document.activeElement === diagnosticInput -> {
                event.preventDefault()
                useDirectStream()
            }
        }
    }

    private fun handleBackKey(event: KeyboardEvent) {
        event.preventDefault()
        when {
            appShell.activeSection == WebOsSection.LIVE && playback.isActive() && document.body?.classList?.contains("hud-visible") == true -> playback.hideHud()
            appShell.activeSection == WebOsSection.LIVE && playback.isActive() -> playback.stop()
            appShell.activeSection != WebOsSection.LIVE -> appShell.activate(WebOsSection.LIVE)
            else -> platformBack()
        }
    }

    private fun restoreCachedState() {
        val loaded = stateStore.load()
        storageProblem = loaded.error?.let { "Tárolási hiba: $it" }
        val state = loaded.state
        if (state == null) {
            renderChannels()
            show("Nincs mentett csatornalista; hálózati betöltés indul.")
            return
        }

        cachedChannels = state.channels
        channels = state.channels
        settings = state.settings
        lastSuccessfulChannelId = state.lastChannelId?.takeIf { id -> channels.any { it.id == id } }
        recentChannelIds = normalizedChannelHistory(state.recentChannelIds, channels)
        playlistCachedAt = state.playlistUpdatedAt
        selectedChannelId = lastSuccessfulChannelId ?: channels.firstOrNull()?.id
        renderChannels()
        restoreChannelFocus()
        show("${channels.size} mentett csatorna betöltve; hálózati frissítés indul.")
    }

    private fun recordSuccessfulPlayback(channelId: String) {
        val channel = channels.firstOrNull { it.id == channelId } ?: return
        if (cachedChannels.none { it.id == channel.id }) return
        if (lastSuccessfulChannelId == channel.id && recentChannelIds.firstOrNull() == channel.id) return
        lastSuccessfulChannelId = channel.id
        recentChannelIds = normalizedChannelHistory(listOf(channel.id) + recentChannelIds, cachedChannels)
        persistState()
    }

    private fun persistState() {
        if (cachedChannels.isEmpty()) return
        val state =
            WebOsStoredState(
                playlistUrl = OFFICIAL_PLAYLIST_URL,
                playlistUpdatedAt = playlistCachedAt,
                channels = cachedChannels,
                lastChannelId = lastSuccessfulChannelId,
                recentChannelIds = recentChannelIds,
                settings = settings,
            )
        storageProblem = stateStore.save(state)?.let { "Tárolási hiba: $it" }
    }
}

internal fun displayName(channel: Channel): String = channel.name.takeUnless { it == UNKNOWN_CHANNEL_NAME_ID } ?: "Ismeretlen csatorna"

private fun displayGroup(channel: Channel): String = displayGroup(channel.group)

private fun displayGroup(group: String): String = group.takeUnless { it == OTHER_CATEGORY_ID } ?: "Egyéb"

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

package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.EpgMatcher
import hu.wukki.tv.EpgProgrammeIndex
import hu.wukki.tv.OTHER_CATEGORY_ID
import hu.wukki.tv.PlaylistParser
import hu.wukki.tv.Programme
import hu.wukki.tv.ProgrammePair
import hu.wukki.tv.UNKNOWN_CHANNEL_NAME_ID
import hu.wukki.tv.normalizedChannelHistory
import hu.wukki.tv.programmeProgress
import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent
import hu.wukki.tv.ui.navigation.RemoteKey
import hu.wukki.tv.ui.navigation.SettingsOptionId
import hu.wukki.tv.ui.settings.SettingsSection
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.fetch.Response
import kotlin.js.Date
import kotlin.js.Promise

private const val OFFICIAL_PLAYLIST_URL = "https://raw.githubusercontent.com/wukki/wukki-tv/refs/heads/main/wukki-tv.m3u"
private const val DEFAULT_DIAGNOSTIC_STREAM_URL = "http://88.212.15.19/live/m2_hun/index.m3u8"
private const val WEBOS_PLAYLIST_ID = "webos-playlist"
private const val MAX_PLAYLIST_BYTES = 2 * 1024 * 1024
private const val MAX_EPG_BYTES = 32 * 1024 * 1024
private const val PLAYLIST_TIMEOUT_MS = 15_000
private const val EPG_TIMEOUT_MS = 30_000
private const val REPEATED_CHANNEL_SWITCH_INTERVAL_MS = 350

fun main() {
    WebOsApp().start()
}

@Suppress("LargeClass", "TooManyFunctions")
private class WebOsApp {
    val sourceInput = element<HTMLInputElement>("source-url")
    val diagnosticInput = element<HTMLInputElement>("diagnostic-url")
    val loadPlaylist = element<HTMLButtonElement>("load-playlist")
    val playDirect = element<HTMLButtonElement>("play-direct")
    val channelSearch = element<HTMLInputElement>("channel-search")
    val channelTabs = element<HTMLElement>("channel-tabs")
    val channelFilterBar = element<HTMLElement>("channel-filter-bar")
    val channelSearchBar = element<HTMLElement>("channel-search-bar")
    val openChannelSearch = element<HTMLButtonElement>("open-channel-search")
    val clearChannelSearch = element<HTMLButtonElement>("clear-channel-search")
    val closeChannelSearch = element<HTMLButtonElement>("close-channel-search")
    val channelList = element<HTMLElement>("channel-list")
    val channelEmpty = element<HTMLElement>("channel-empty")
    val channelEmptyTitle = element<HTMLElement>("channel-empty-title")
    val channelEmptyDescription = element<HTMLElement>("channel-empty-description")
    val channelEmptyAction = element<HTMLButtonElement>("channel-empty-action")
    val channelCount = element<HTMLElement>("channel-count")
    val status = element<HTMLElement>("status")
    val platform = element<HTMLElement>("platform")
    val channelButtons = mutableListOf<HTMLButtonElement>()
    private val favoriteButtons = mutableListOf<HTMLButtonElement>()
    private val filterButtons = mutableListOf<HTMLButtonElement>()
    private val previewLogo = element<HTMLImageElement>("channel-preview-logo")
    private val previewLogoFallback = element<HTMLElement>("channel-preview-logo-fallback")
    private val previewMarker = element<HTMLElement>("channel-preview-marker")
    private val previewName = element<HTMLElement>("channel-preview-name")
    private val previewMeta = element<HTMLElement>("channel-preview-meta")
    private val previewProgrammes = element<HTMLElement>("channel-preview-programmes")
    private val previewCurrentProgramme = element<HTMLElement>("channel-preview-current")
    private val previewNextProgramme = element<HTMLElement>("channel-preview-next")
    private val previewProgrammeTime = element<HTMLElement>("channel-preview-time")
    private val previewProgrammeProgress = element<HTMLElement>("channel-preview-progress")
    private val previewProgrammeProgressValue = element<HTMLElement>("channel-preview-progress-value")
    private val previewProgrammeDescription = element<HTMLElement>("channel-preview-description")
    private val previewProgrammeImage = element<HTMLImageElement>("channel-preview-programme-image")
    private val openPreviewChannel = element<HTMLButtonElement>("open-preview-channel")
    private val favoritePreviewChannel = element<HTMLButtonElement>("favorite-preview-channel")
    private val previousChannel = element<HTMLButtonElement>("previous-channel")
    private val channelDown = element<HTMLButtonElement>("channel-down")
    private val channelUp = element<HTMLButtonElement>("channel-up")
    private val quickSettings = element<HTMLButtonElement>("quick-settings")
    private val quickSettingsDialog = element<HTMLElement>("quick-settings-dialog")
    private val closeQuickSettings = element<HTMLButtonElement>("close-quick-settings")
    private val quickAspectOptions = element<HTMLElement>("quick-aspect-options")
    private val legalDialog = element<HTMLElement>("legal-dialog")
    private val appShell = WebOsAppShell(::onSectionActivated, ::onNavigationFocused)
    private var settings = WebOsSettings()
    private val localizer = WebOsLocalizer(settings.language)
    private val playback = WebOsPlaybackView(appShell, localizer, ::show, ::restoreChannelFocus, ::recordSuccessfulPlayback)
    var channels = emptyList<Channel>()
    private var filteredChannels = emptyList<Channel>()
    private var categories = emptyList<String>()
    private var selectedFilter = WebOsChannelFilter.ALL
    private var selectedCategory: String? = null
    private var renderedWindow = ChannelRenderWindow(0, 0)
    private var cachedChannels = emptyList<Channel>()
    private var playlistLoading = false
    private var playlistLoadGeneration = 0L
    private var selectedChannelId: String? = null
    private var lastSuccessfulChannelId: String? = null
    private var recentChannelIds = emptyList<String>()
    private var playlistCachedAt = 0L
    private var storageProblem: String? = null
    private var favoriteFocusRequested = false
    private var playlistLoadFailed = false
    private var programmes = emptyList<Programme>()
    private var programmeIndex = EpgProgrammeIndex(emptyList())
    private var epgLoadGeneration = 0L
    private var epgLoading = false
    private var programmeRefreshTimer: Int? = null
    private var playlistRefreshTimer: Int? = null
    private var epgRefreshTimer: Int? = null
    private var currentEpgUrl: String? = null
    private var autoplayStarted = false
    private var uiActive = true
    private var playlistRefreshInterrupted = false
    private var epgRefreshInterrupted = false
    private var stateWritesBlocked = false
    private val epgParser = WebOsEpgParser(schedule = { action -> window.setTimeout(action, 0) })
    private val settingsView =
        WebOsSettingsView(
            localizer = localizer,
            settings = { settings },
            update = ::updateSettings,
            refreshPlaylist = ::fetchPlaylist,
            refreshEpg = { currentEpgUrl?.let(::fetchEpg) ?: show(localizer.text("settings.no.sources")) },
            channelCount = { channels.size },
            playlistUpdatedAt = { playlistCachedAt },
            platformLabel = { "webOS · ${playback.hlsSupport}" },
        )
    private val guideView =
        WebOsGuideView(
            localizer = localizer,
            channels = { channels },
            programmesFor = { channel -> programmeIndex.programmes(channel) },
            selectedChannelId = { playback.playingChannelId ?: selectedChannelId },
            showLogos = { settings.showLogos },
            showProgrammeImages = { settings.showProgrammeImages },
            openChannel = ::openGuideChannel,
        )

    private val navigationHost =
        object : WebOsNavigationHost {
            override val activeSection: WebOsSection get() = appShell.activeSection
            override val visibleChannelIds: List<String> get() = filteredChannels.map(Channel::id)
            override val selectedChannelIdForNavigation: String? get() = playback.playingChannelId ?: selectedChannelId
            override val channelSearchHasText: Boolean get() = channelSearch.value.isNotBlank()
            override val channelSearchFocused: Boolean get() = document.activeElement === channelSearch
            override val channelSearchOpen: Boolean get() = !channelSearchBar.hidden
            override val channelFilterCount: Int get() = filterButtons.size
            override val liveOverlayVisible: Boolean get() = document.body?.classList?.contains("hud-visible") == true
            override val liveNavigationVisible: Boolean get() = playback.navigationVisible
            override val dialogVisible: Boolean get() = guideView.dialogVisible || !quickSettingsDialog.hidden || !legalDialog.hidden || playback.recoveryVisible
            override val settingsDetailOpen: Boolean get() = settingsView.detailOpen
            override val activateSection: (WebOsSection) -> Unit = appShell::activate
            override val focusNavigation: (WebOsSection) -> Unit = appShell::focusNavigation
            override val focusSectionContent: (WebOsSection) -> Unit = { section ->
                when (section) {
                    WebOsSection.CHANNELS -> restoreChannelFocus()
                    WebOsSection.SETTINGS -> focusSettingsCategory(0)
                    WebOsSection.LIVE -> focusAndReveal(appShell.view(section))
                    WebOsSection.GUIDE -> guideView.focusCurrent()
                }
            }
            override val focusChannelFilter: (Int) -> Unit = { index ->
                filterButtons.getOrNull(index.coerceIn(0, (filterButtons.size - 1).coerceAtLeast(0)))?.let(::focusAndReveal)
            }
            override val focusChannelSearch: () -> Unit = {
                openSearch()
                focusAndReveal(channelSearch)
            }
            override val focusChannel: (Int, Boolean) -> Unit = { index, favorite ->
                favoriteFocusRequested = favorite
                focusChannelAt(index)
            }
            override val focusSettings: (Int) -> Unit = ::focusSettingsCategory
            override val openSettingsSection: (SettingsSection) -> Unit = settingsView::open
            override val closeSettingsSection: () -> Unit = settingsView::close
            override val focusSettingsOption: (Int) -> Unit = settingsView::focusOption
            override val adjustSetting: (SettingsOptionId, Int) -> Unit = settingsView::adjust
            override val activateSetting: (SettingsOptionId) -> Unit = settingsView::activate
            override val activateChannelFilter: (Int) -> Unit = { index ->
                filterButtons.getOrNull(index)?.click()
            }
            override val activateChannelEmpty: () -> Unit = { channelEmptyAction.click() }
            override val clearChannelSearch: () -> Unit = ::closeSearch
            override val openChannel: (Int) -> Unit = ::openFilteredChannel
            override val toggleFavorite: (Int) -> Unit = ::toggleFavorite
            override val previewChannel: (String?) -> Unit = { id ->
                val channel = channels.firstOrNull { it.id == id }
                playback.showPreview(channel?.let(::playbackChannel), channel?.let(::currentProgrammes) ?: ProgrammePair(null, null))
            }
            override val switchChannel: (Int) -> Unit = ::switchChannel
            override val openPreviousChannel: () -> Unit = ::openPreviousChannel
            override val selectChannelNumber: (String) -> Unit = ::selectChannelNumber
            override val showLiveOverlay: () -> Unit = playback::showHud
            override val hideLiveOverlay: () -> Unit = {
                playback.hideHud()
                playback.showPreview(null)
            }
            override val showLiveNavigation: () -> Unit = playback::showNavigation
            override val showChannelNumberInput: (String?) -> Unit = playback::showChannelNumberInput
            override val showQuickSettings: () -> Unit = ::showQuickSettings
            override val closeDialog: () -> Unit = ::closeDialog
            override val handleDialogEvent: (GuideProgrammeDialogEvent) -> Unit = ::handleDialogEvent
            override val handleGuideKey: (RemoteKey) -> Unit = guideView::handleRemoteKey
            override val confirmGuide: () -> Unit = guideView::confirm
            override val showStatus: (String) -> Unit = ::show
            override val exitApplication: () -> Unit = ::platformBack
        }
    private val remoteController = WebOsRemoteController(navigationHost)

    private val stateStore =
        WebOsStateStore(
            read = { window.localStorage.getItem(WEBOS_STATE_STORAGE_KEY) },
            write = { value -> window.localStorage.setItem(WEBOS_STATE_STORAGE_KEY, value) },
        )
    private val epgCacheStore =
        WebOsEpgCacheStore(
            read = { window.localStorage.getItem(WEBOS_EPG_STORAGE_KEY) },
            write = { value -> window.localStorage.setItem(WEBOS_EPG_STORAGE_KEY, value) },
            remove = { window.localStorage.removeItem(WEBOS_EPG_STORAGE_KEY) },
        )

    fun start() {
        platform.textContent = "${window.navigator.userAgent} · HLS: ${playback.hlsSupport} · Kotlin/JS core"
        sourceInput.value = OFFICIAL_PLAYLIST_URL
        diagnosticInput.value = requestedStream() ?: DEFAULT_DIAGNOSTIC_STREAM_URL
        configureControls()
        playback.configure()
        settingsView.onCategoryFocused = remoteController::onSettingsFocused
        settingsView.onCategoryActivated = remoteController::onSettingsCategoryActivated
        settingsView.onOptionFocused = remoteController::onSettingsOptionFocused
        settingsView.configure()
        guideView.configure()
        configureKeyboard()
        appShell.configure()
        configureLifecycle()
        restoreCachedState()
        applySettings()
        settingsView.refreshCopy()
        restoreEpgCache()
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
        playback.start(playbackChannel(channel), settings, currentProgrammes(channel))
    }

    private fun openGuideChannel(channelId: String) {
        val index = channels.indexOfFirst { it.id == channelId }
        if (index >= 0) startPlayback(index)
    }

    private fun playbackChannel(channel: Channel): Channel = if (settings.showLogos) channel else channel.copy(logo = null)

    private fun onSectionActivated(section: WebOsSection) {
        remoteController.onSectionActivated(section)
        if (section == WebOsSection.LIVE) {
            if (playback.isActive()) {
                playback.showHud()
                playback.showNavigation()
            }
        } else {
            playback.hideHud()
            playback.leaveLiveNavigation()
        }
        when (section) {
            WebOsSection.CHANNELS -> {
                renderChannelWindow()
            }

            WebOsSection.GUIDE -> {
                guideView.activate()
                remoteController.onGuideFocused()
            }

            else -> {}
        }
    }

    private fun onNavigationFocused(section: WebOsSection) {
        remoteController.onNavigationFocused(section)
        if (appShell.activeSection == WebOsSection.LIVE) playback.showNavigation()
    }

    private fun renderChannels() {
        val categoryOptions = sortedChannelCategories(channels)
        categories = categoryOptions
        if (selectedCategory !in categoryOptions) {
            selectedCategory = null
            if (selectedFilter == WebOsChannelFilter.CATEGORY) selectedFilter = WebOsChannelFilter.ALL
        }
        val previousSelectedId = selectedChannelId
        filteredChannels = filterAndSortChannels(channels, channelSearch.value, selectedFilter, selectedCategory, recentChannelIds)
        selectedChannelId =
            previousSelectedId?.takeIf { id -> filteredChannels.any { it.id == id } }
                ?: filteredChannels.firstOrNull()?.id
        channelCount.textContent = "${filteredChannels.size} / ${localizer.text("settings.channels.count", channels.size)}"
        renderFilterTabs()
        renderEmptyState()
        applyChannelDisplaySettings()
        renderedWindow = ChannelRenderWindow(-1, -1)
        keepSelectedChannelVisible()
        renderChannelWindow()
        updateSelectedChannel()
    }

    private fun renderFilterTabs() {
        channelTabs.innerHTML = ""
        filterButtons.clear()
        addFilterTab(localizer.text("channels.all"), selectedFilter == WebOsChannelFilter.ALL) { selectFilter(WebOsChannelFilter.ALL) }
        addFilterTab(localizer.text("channels.favorites"), selectedFilter == WebOsChannelFilter.FAVORITES) { selectFilter(WebOsChannelFilter.FAVORITES) }
        addFilterTab(localizer.text("channels.recent"), selectedFilter == WebOsChannelFilter.RECENT) { selectFilter(WebOsChannelFilter.RECENT) }
        addFilterTab(localizer.text("channels.previous"), false, ::openPreviousChannel)
        categories.forEach { category ->
            addFilterTab(displayGroup(category), selectedFilter == WebOsChannelFilter.CATEGORY && selectedCategory == category) {
                selectedCategory = category
                selectFilter(WebOsChannelFilter.CATEGORY)
            }
        }
    }

    private fun addFilterTab(
        label: String,
        selected: Boolean,
        action: () -> Unit,
    ) {
        val index = filterButtons.size
        val button = document.createElement("button") as HTMLButtonElement
        button.type = "button"
        button.textContent = label
        button.setAttribute("role", "tab")
        button.setAttribute("aria-selected", selected.toString())
        button.onclick = {
            action()
            null
        }
        button.onfocus = {
            remoteController.onChannelFilterFocused(index)
            null
        }
        channelTabs.appendChild(button)
        filterButtons += button
    }

    private fun selectFilter(filter: WebOsChannelFilter) {
        selectedFilter = filter
        if (filter != WebOsChannelFilter.CATEGORY) selectedCategory = null
        renderChannels()
        filterButtons.firstOrNull { it.getAttribute("aria-selected") == "true" }?.focus()
    }

    private fun renderEmptyState() {
        val emptyState = channelEmptyState(channels.isNotEmpty(), filteredChannels.size, channelSearch.value, selectedFilter, playlistLoadFailed)
        channelEmpty.hidden = emptyState == null
        if (emptyState == null) return
        val copy = emptyStateCopy(emptyState, channelSearch.value, selectedCategory, localizer)
        channelEmptyTitle.textContent = copy.first
        channelEmptyDescription.textContent = copy.second
        channelEmptyAction.textContent = copy.third
        channelEmptyAction.onclick = {
            when (emptyState.action()) {
                WebOsChannelEmptyAction.REFRESH -> {
                    fetchPlaylist()
                }

                WebOsChannelEmptyAction.CLEAR_SEARCH -> {
                    channelSearch.value = ""
                    renderChannels()
                    channelSearch.focus()
                }

                WebOsChannelEmptyAction.SHOW_ALL -> {
                    selectFilter(WebOsChannelFilter.ALL)
                }
            }
            null
        }
    }

    private fun applyChannelDisplaySettings() {
        val rowHeight = scaledChannelRowHeight()
        channelList.style.setProperty("--channel-row-height", "${rowHeight}px")
        document.body?.classList?.remove("channel-mode-compact", "channel-mode-normal", "channel-mode-detailed")
        document.body?.classList?.add("channel-mode-${settings.channelListMode.lowercase()}")
        previewProgrammes.hidden = !settings.showChannelProgramme && !settings.showMiniGuide
    }

    private fun keepSelectedChannelVisible() {
        val index = filteredChannels.indexOfFirst { it.id == selectedChannelId }
        if (index < 0) {
            channelList.scrollTop = 0.0
            return
        }
        val rowHeight = scaledChannelRowHeight()
        val maximumScroll = (filteredChannels.size * rowHeight - channelList.clientHeight).coerceAtLeast(0)
        channelList.scrollTop = (index * rowHeight).coerceAtMost(maximumScroll).toDouble()
    }

    private fun renderChannelWindow(focusIndex: Int? = null) {
        val rowHeight = scaledChannelRowHeight()
        val window = calculateChannelRenderWindow(filteredChannels.size, channelList.scrollTop, channelList.clientHeight, rowHeight)
        if (window == renderedWindow) {
            focusIndex?.let(::focusRenderedChannel)
            return
        }

        val previouslyFocusedIndex = focusedChannelIndex()
        channelList.innerHTML = ""
        channelButtons.clear()
        favoriteButtons.clear()
        renderedWindow = window
        channelList.appendChild(channelSpacer(window.start * rowHeight))
        filteredChannels.subList(window.start, window.endExclusive).forEachIndexed { offset, channel ->
            val filteredIndex = window.start + offset
            val sourceIndex = channels.indexOfFirst { it.id == channel.id }
            val row = document.createElement("div") as HTMLElement
            row.className = "channel-row"
            if (channel.id == selectedChannelId) row.classList.add("is-previewed")
            if (channel.id == playback.playingChannelId) row.classList.add("is-playing")
            val button = document.createElement("button") as HTMLButtonElement
            button.type = "button"
            button.className = "channel"
            button.setAttribute("data-channel-id", channel.id)
            button.setAttribute("data-filtered-index", filteredIndex.toString())

            val number = document.createElement("span") as HTMLElement
            number.className = "channel-number"
            number.textContent = (channel.tvgChno ?: sourceIndex + 1).toString()
            if (settings.showLogos && !channel.logo.isNullOrBlank()) {
                val logo = document.createElement("img") as HTMLImageElement
                logo.className = "channel-logo"
                logo.alt = ""
                logo.src = channel.logo.orEmpty()
                logo.addEventListener("error", {
                    logo.hidden = true
                })
                button.appendChild(logo)
            }
            val text = document.createElement("span") as HTMLElement
            text.className = "channel-text"
            val name = document.createElement("strong") as HTMLElement
            name.textContent = displayName(channel)
            val group = document.createElement("small") as HTMLElement
            group.textContent = displayGroup(channel)
            text.appendChild(name)
            text.appendChild(group)
            if (settings.showChannelProgramme) {
                val programme = document.createElement("span") as HTMLElement
                programme.className = "channel-programme"
                programme.textContent = currentProgrammes(channel).current?.title?.ifBlank { localizer.text("epg.untitled") } ?: localizer.text("epg.none")
                text.appendChild(programme)
            }
            button.appendChild(number)
            button.appendChild(text)
            button.onfocus = {
                selectedChannelId = channel.id
                remoteController.onChannelFocused(filteredIndex, favorite = false)
                updateSelectedChannel()
                null
            }
            button.onmouseover = {
                button.focus()
                null
            }
            button.onclick = {
                selectedChannelId = channel.id
                updateSelectedChannel()
                show(
                    localized(
                        "Előnézet: ${displayName(channel)}. A lejátszáshoz válaszd a Megnyitás gombot.",
                        "Preview: ${displayName(channel)}. Select Open to start playback.",
                    ),
                )
                null
            }
            val favorite = document.createElement("button") as HTMLButtonElement
            favorite.type = "button"
            favorite.className = "channel-favorite"
            favorite.textContent = if (channel.favorite) "♥" else "♡"
            favorite.setAttribute("aria-label", "${displayName(channel)} kedvenc")
            favorite.setAttribute("aria-pressed", channel.favorite.toString())
            favorite.setAttribute("data-filtered-index", filteredIndex.toString())
            favorite.onfocus = {
                selectedChannelId = channel.id
                remoteController.onChannelFocused(filteredIndex, favorite = true)
                updateSelectedChannel()
                null
            }
            favorite.onclick = {
                toggleFavorite(filteredIndex)
                null
            }
            row.appendChild(button)
            row.appendChild(favorite)
            channelList.appendChild(row)
            channelButtons += button
            favoriteButtons += favorite
        }
        channelList.appendChild(channelSpacer((filteredChannels.size - window.endExclusive) * rowHeight))
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
                button.parentElement?.classList?.add("is-previewed")
            } else {
                button.removeAttribute("aria-current")
                button.parentElement?.classList?.remove("is-previewed")
            }
        }
        renderChannelPreview()
    }

    private fun renderChannelPreview() {
        val channel = channels.firstOrNull { it.id == selectedChannelId }
        openPreviewChannel.disabled = channel == null
        favoritePreviewChannel.disabled = channel == null
        if (channel == null) {
            previewName.textContent = localizer.text("channels.select")
            previewMeta.textContent = localizer.text("epg.none.description")
            previewMarker.textContent = localizer.text("channels.preview")
            previewLogo.hidden = true
            previewLogoFallback.hidden = false
            favoritePreviewChannel.textContent = "♡"
            renderPreviewProgrammes(ProgrammePair(null, null), Date.now().toLong())
            return
        }
        previewName.textContent = displayName(channel)
        val sourceIndex = channels.indexOfFirst { it.id == channel.id }
        previewMeta.textContent = "${channel.tvgChno ?: sourceIndex + 1}. · ${displayGroup(channel)}"
        previewMarker.textContent = if (channel.id == playback.playingChannelId) localizer.text("channels.playing") else localizer.text("channels.preview")
        favoritePreviewChannel.textContent = if (channel.favorite) "♥" else "♡"
        favoritePreviewChannel.setAttribute("aria-pressed", channel.favorite.toString())
        val logo = channel.logo?.takeIf { settings.showLogos && it.isNotBlank() }
        previewLogo.hidden = logo == null
        previewLogoFallback.hidden = logo != null
        if (logo != null) previewLogo.src = logo
        renderPreviewProgrammes(currentProgrammes(channel), Date.now().toLong())
    }

    private fun restoreChannelFocus() {
        val selectedIndex = filteredChannels.indexOfFirst { it.id == selectedChannelId }
        if (filteredChannels.isNotEmpty()) {
            focusChannelAt(selectedIndex.takeIf { it >= 0 } ?: 0)
        } else {
            focusAndReveal(channelEmptyAction)
        }
    }

    private fun focusChannelAt(index: Int) {
        if (filteredChannels.isEmpty()) {
            focusAndReveal(channelEmptyAction)
            return
        }
        val target = index.coerceIn(filteredChannels.indices)
        val rowHeight = scaledChannelRowHeight()
        val rowTop = target * rowHeight
        val rowBottom = rowTop + rowHeight
        val viewportBottom = channelList.scrollTop + channelList.clientHeight
        when {
            rowTop < channelList.scrollTop -> channelList.scrollTop = rowTop.toDouble()
            rowBottom > viewportBottom -> channelList.scrollTop = (rowBottom - channelList.clientHeight).coerceAtLeast(0).toDouble()
        }
        renderChannelWindow(target)
    }

    private fun focusRenderedChannel(index: Int) {
        val candidates = if (favoriteFocusRequested) favoriteButtons else channelButtons
        val button = candidates.firstOrNull { it.getAttribute("data-filtered-index")?.toIntOrNull() == index } ?: return
        favoriteFocusRequested = false
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
        playback.start(probeChannel(url), settings)
    }

    @Suppress("CognitiveComplexMethod")
    private fun fetchPlaylist() {
        if (playlistLoading || !uiActive) return
        val initialLoad = cachedChannels.isEmpty()
        val generation = ++playlistLoadGeneration
        val url = OFFICIAL_PLAYLIST_URL
        playlistLoading = true
        playlistLoadFailed = false
        loadPlaylist.disabled = true
        loadPlaylist.textContent = localizer.text("status.playlist.loading")
        show(localizer.text("status.playlist.loading"))
        fetchPlaylistText(url)
            .then { text ->
                if (generation != playlistLoadGeneration || !uiActive) return@then
                val searchFocused = document.activeElement === channelSearch
                val focusedChannelId = focusedChannelIdForRefresh()
                val parsed = mergeFavoriteState(PlaylistParser.parse(text, WEBOS_PLAYLIST_ID, url), cachedChannels)
                val discoveredEpgUrl = PlaylistParser.epgUrl(text, url)
                currentEpgUrl = discoveredEpgUrl
                settingsView.setEpgSource(discoveredEpgUrl)
                if (parsed.isEmpty()) {
                    throw IllegalArgumentException("A letöltött fájl nem tartalmaz lejátszható csatornát.")
                } else {
                    channels = matchEpg(parsed)
                    cachedChannels = channels
                    playlistCachedAt = Date.now().toLong()
                    lastSuccessfulChannelId = lastSuccessfulChannelId?.takeIf { id -> parsed.any { it.id == id } }
                    recentChannelIds = normalizedChannelHistory(recentChannelIds, parsed)
                    renderChannels()
                    guideView.updateData()
                    persistState()
                    schedulePlaylistRefresh()
                    show(localizer.text("status.playlist.loaded", channels.size, "Wukki"))
                    maybeAutoplay()
                    if (!playback.isActive()) {
                        restoreFocusAfterRefresh(searchFocused, focusedChannelId)
                        if (initialLoad && !searchFocused && focusedChannelId == null && appShell.activeSection == WebOsSection.CHANNELS) {
                            restoreChannelFocus()
                        }
                    }
                    if (discoveredEpgUrl != null) fetchEpg(discoveredEpgUrl)
                }
                finishPlaylistLoad()
            }.catch { error ->
                if (generation != playlistLoadGeneration || !uiActive) return@catch
                playlistLoadFailed = true
                finishPlaylistLoad(retry = true)
                val detail = error.asDynamic().message ?: error.toString()
                if (cachedChannels.isEmpty()) {
                    renderChannels()
                    show(localizer.text("error.playlist.load", detail))
                } else {
                    show(
                        localized(
                            "A hálózati frissítés sikertelen; a mentett lista böngészhető, de a lejátszáshoz hálózat kell: $detail",
                            "Network refresh failed; the saved list remains available, but playback requires a connection: $detail",
                        ),
                    )
                }
            }
    }

    private fun finishPlaylistLoad(retry: Boolean = false) {
        playlistLoading = false
        loadPlaylist.disabled = false
        loadPlaylist.textContent = if (retry) localizer.text("action.retry") else localizer.text("settings.refresh")
        schedulePlaylistRefresh(fromNow = retry)
    }

    private fun fetchEpg(url: String) {
        if (!uiActive) return
        val generation = ++epgLoadGeneration
        epgLoading = true
        epgParser.cancel()
        show(localizer.text("status.epg.loading", "Wukki"))
        fetchBoundedText(url, MAX_EPG_BYTES, EPG_TIMEOUT_MS, "Az EPG")
            .then { xml ->
                if (generation != epgLoadGeneration) return@then
                epgParser.parse(
                    xml,
                    onComplete = { parsed ->
                        if (generation != epgLoadGeneration) return@parse
                        epgLoading = false
                        if (parsed.isEmpty()) {
                            scheduleEpgRefresh(fromNow = true)
                            show(
                                localized(
                                    "Az EPG nem tartalmaz érvényes műsorokat; a korábbi adatok maradnak használatban.",
                                    "The EPG contains no valid programmes; the previous data remains in use.",
                                ),
                            )
                            return@parse
                        }
                        applyEpg(parsed)
                        val cacheError = epgCacheStore.save(WebOsEpgCache(url, Date.now().toLong(), parsed))
                        scheduleEpgRefresh()
                        show(localizer.text("status.epg.loaded", parsed.size, "Wukki") + cacheError?.let { " $it" }.orEmpty())
                    },
                    onFailure = { error ->
                        if (generation == epgLoadGeneration) {
                            epgLoading = false
                            scheduleEpgRefresh(fromNow = true)
                            show(
                                localized(
                                    "Az EPG nem dolgozható fel; a videó tovább működik: ${error.message ?: error}",
                                    "EPG processing failed; video playback continues: ${error.message ?: error}",
                                ),
                            )
                        }
                    },
                )
            }.catch { error ->
                if (generation == epgLoadGeneration) {
                    epgLoading = false
                    scheduleEpgRefresh(fromNow = true)
                    val detail = error.asDynamic().message ?: error.toString()
                    show(localized("Az EPG nem tölthető be; a videó tovább működik: $detail", "EPG download failed; video playback continues: $detail"))
                }
            }
    }

    private fun applyEpg(parsed: List<Programme>) {
        programmes = parsed
        programmeIndex = EpgProgrammeIndex(parsed)
        channels = matchEpg(channels)
        cachedChannels = matchEpg(cachedChannels)
        filteredChannels = filteredChannels.map { filtered -> channels.firstOrNull { it.id == filtered.id } ?: filtered }
        val focusedIndex = focusedChannelIndex()
        renderedWindow = ChannelRenderWindow(-1, -1)
        renderChannelWindow(focusedIndex)
        guideView.updateData()
        refreshProgrammeViews()
        persistState()
    }

    private fun matchEpg(source: List<Channel>): List<Channel> = if (programmes.isEmpty()) source else EpgMatcher.match(source, programmes)

    private fun currentProgrammes(channel: Channel): ProgrammePair = programmeIndex.nowAndNext(channel, Date.now().toLong())

    private fun refreshProgrammeViews() {
        val now = Date.now().toLong()
        renderChannelPreview()
        channels.firstOrNull { it.id == playback.playingChannelId }?.let { channel ->
            playback.updateProgramme(playbackChannel(channel), programmeIndex.nowAndNext(channel, now), now)
        }
        guideView.refreshClock()
        scheduleProgrammeRefresh(now)
    }

    private fun scheduleProgrammeRefresh(now: Long) {
        programmeRefreshTimer?.let(window::clearTimeout)
        programmeRefreshTimer = null
        if (!uiActive || programmes.isEmpty()) return
        val boundary = programmeIndex.nextBoundary(channels, now) ?: return
        val delay = minOf((boundary - now + 50L).coerceAtLeast(1_000L), 60_000L).toInt()
        programmeRefreshTimer =
            window.setTimeout(
                {
                    val focus = focusedChannelIndex()
                    renderedWindow = ChannelRenderWindow(-1, -1)
                    renderChannelWindow(focus)
                    refreshProgrammeViews()
                },
                delay,
            )
    }

    private fun renderPreviewProgrammes(
        pair: ProgrammePair,
        now: Long,
    ) {
        val current = pair.current
        previewCurrentProgramme.textContent = current?.title?.ifBlank { localizer.text("epg.untitled") } ?: localizer.text("epg.none")
        previewNextProgramme.textContent = pair.next?.let { "${formatEpgTime(it.start)} · ${it.title.ifBlank { localizer.text("epg.untitled") }}" } ?: localizer.text("epg.none")
        previewProgrammeTime.textContent = current?.let { "${formatEpgTime(it.start)} – ${formatEpgTime(it.end)}" }.orEmpty()
        previewProgrammeTime.hidden = current == null
        previewProgrammeDescription.textContent = current?.description.orEmpty()
        previewProgrammeDescription.hidden = current?.description.isNullOrBlank()
        val progress = programmeProgress(current, now)
        previewProgrammeProgress.hidden = progress == null
        previewProgrammeProgress.setAttribute("aria-valuenow", ((progress ?: 0.0) * 100).toInt().toString())
        previewProgrammeProgressValue.style.width = "${(progress ?: 0.0) * 100}%"
        val image = current?.imageUrl?.takeIf { settings.showProgrammeImages }
        previewProgrammeImage.hidden = image == null
        if (image != null) previewProgrammeImage.src = image
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
        channelSearch.onfocus = {
            remoteController.onSearchFocused()
            null
        }
        openChannelSearch.onclick = {
            openSearch()
            channelSearch.focus()
            null
        }
        openChannelSearch.onfocus = {
            remoteController.onSearchFocused()
            null
        }
        clearChannelSearch.onclick = {
            channelSearch.value = ""
            renderChannels()
            channelSearch.focus()
            null
        }
        closeChannelSearch.onclick = {
            closeSearch()
            null
        }
        channelList.onscroll = {
            renderChannelWindow()
            null
        }
        previousChannel.onclick = {
            openPreviousChannel()
            null
        }
        channelDown.onclick = {
            switchChannel(-1)
            null
        }
        channelUp.onclick = {
            switchChannel(1)
            null
        }
        quickSettings.onclick = {
            showQuickSettings()
            null
        }
        closeQuickSettings.onclick = {
            closeDialog()
            null
        }
        openPreviewChannel.onclick = {
            val index = channels.indexOfFirst { it.id == selectedChannelId }
            if (index >= 0) startPlayback(index)
            null
        }
        favoritePreviewChannel.onclick = {
            val index = filteredChannels.indexOfFirst { it.id == selectedChannelId }
            if (index >= 0) toggleFavorite(index)
            null
        }
        previewLogo.addEventListener("error", {
            previewLogo.hidden = true
            previewLogoFallback.hidden = false
        })
        previewProgrammeImage.addEventListener("error", {
            previewProgrammeImage.hidden = true
        })
    }

    private fun openSearch() {
        channelFilterBar.hidden = true
        channelSearchBar.hidden = false
    }

    private fun closeSearch() {
        channelSearch.value = ""
        channelSearchBar.hidden = true
        channelFilterBar.hidden = false
        renderChannels()
        filterButtons.firstOrNull { it.getAttribute("aria-selected") == "true" }?.focus()
    }

    private fun configureKeyboard() {
        document.onkeydown = { rawEvent: Event ->
            val event = rawEvent as KeyboardEvent
            val editingDiagnostic = document.activeElement === diagnosticInput
            if (!editingDiagnostic || event.keyCode == WEBOS_BACK_KEY || event.keyCode == 27) remoteController.handle(event)
            null
        }
    }

    private fun configureLifecycle() {
        document.addEventListener("visibilitychange", {
            if (document.asDynamic().hidden == true) pauseForBackground() else resumeAfterBackground()
        })
        window.addEventListener("pagehide", { pauseForBackground() })
        window.addEventListener("pageshow", { resumeAfterBackground() })
        if (document.asDynamic().hidden == true) pauseForBackground()
    }

    private fun pauseForBackground() {
        if (!uiActive) return
        uiActive = false
        playlistRefreshInterrupted = playlistRefreshInterrupted || playlistLoading
        epgRefreshInterrupted = epgRefreshInterrupted || epgLoading
        playlistLoadGeneration++
        epgLoadGeneration++
        epgParser.cancel()
        playlistLoading = false
        epgLoading = false
        loadPlaylist.disabled = false
        loadPlaylist.textContent = localizer.text("settings.refresh")
        clearUiTimers()
        remoteController.pauseForBackground()
        playback.pauseForBackground()
        persistState()
    }

    private fun resumeAfterBackground() {
        if (uiActive) return
        uiActive = true
        playback.resumeAfterBackground()
        if (programmes.isNotEmpty()) refreshProgrammeViews()
        schedulePlaylistRefresh()
        scheduleEpgRefresh()
        val resumePlaylist = playlistRefreshInterrupted || cachedChannels.isEmpty()
        val resumeEpg = epgRefreshInterrupted
        playlistRefreshInterrupted = false
        epgRefreshInterrupted = false
        when {
            resumePlaylist -> fetchPlaylist()
            resumeEpg -> currentEpgUrl?.let(::fetchEpg)
        }
        maybeAutoplay()
    }

    private fun clearUiTimers() {
        programmeRefreshTimer?.let(window::clearTimeout)
        playlistRefreshTimer?.let(window::clearTimeout)
        epgRefreshTimer?.let(window::clearTimeout)
        programmeRefreshTimer = null
        playlistRefreshTimer = null
        epgRefreshTimer = null
    }

    private fun focusedChannelIdForRefresh(): String? {
        val element = document.activeElement as? HTMLElement ?: return null
        val index = element.getAttribute("data-filtered-index")?.toIntOrNull() ?: return null
        return filteredChannels.getOrNull(index)?.id
    }

    private fun restoreFocusAfterRefresh(
        searchFocused: Boolean,
        focusedChannelId: String?,
    ) {
        if (appShell.activeSection != WebOsSection.CHANNELS) return
        if (searchFocused) {
            channelSearch.focus()
            return
        }
        val index = filteredChannels.indexOfFirst { it.id == focusedChannelId }
        if (index >= 0) focusChannelAt(index)
    }

    private fun focusSettingsCategory(categoryIndex: Int) {
        val buttons = document.querySelectorAll("#view-settings .settings-categories button")
        val button = buttons.item(categoryIndex.coerceIn(0, buttons.length - 1)) as? HTMLElement
        focusAndReveal(button ?: appShell.view(WebOsSection.SETTINGS))
    }

    private fun openFilteredChannel(index: Int) {
        val channel = filteredChannels.getOrNull(index) ?: return
        val sourceIndex = channels.indexOfFirst { it.id == channel.id }
        if (sourceIndex >= 0) startPlayback(sourceIndex)
    }

    private fun toggleFavorite(index: Int) {
        val channel = filteredChannels.getOrNull(index) ?: return
        val favorite = !channel.favorite
        channels = channels.map { if (it.id == channel.id) it.copy(favorite = favorite) else it }
        cachedChannels = cachedChannels.map { if (it.id == channel.id) it.copy(favorite = favorite) else it }
        selectedChannelId = channel.id
        renderChannels()
        persistState()
        show(
            if (settings.language == "ENGLISH") {
                "${displayName(channel)} ${if (favorite) "added to" else "removed from"} favorites."
            } else {
                "${displayName(channel)} ${if (favorite) "a kedvencekhez adva" else "eltávolítva a kedvencek közül"}."
            },
        )
    }

    private fun switchChannel(delta: Int) {
        if (channels.isEmpty()) return
        val current = channels.indexOfFirst { it.id == playback.playingChannelId }.coerceAtLeast(0)
        startPlayback(nextChannelIndex(current, channels.size, delta))
    }

    private fun openPreviousChannel() {
        val previousId = recentChannelIds.firstOrNull { it != playback.playingChannelId }
        val index = channels.indexOfFirst { it.id == previousId }
        if (index >= 0) startPlayback(index) else show(localized("Még nincs előző sikeresen lejátszott csatorna.", "No previously played channel is available yet."))
    }

    private fun selectChannelNumber(number: String) {
        val requested = number.toIntOrNull() ?: return
        val index = channels.indexOfFirst { it.tvgChno == requested }.takeIf { it >= 0 } ?: (requested - 1).takeIf { it in channels.indices }
        if (index != null) startPlayback(index) else show(localized("Nincs $number számú csatorna.", "Channel number $number is unavailable."))
    }

    private fun showQuickSettings() {
        playback.hideHud()
        renderQuickAspectOptions()
        quickSettingsDialog.hidden = false
        (quickAspectOptions.querySelector("button[aria-pressed=\"true\"]") as? HTMLElement)?.focus() ?: closeQuickSettings.focus()
    }

    private fun closeDialog() {
        if (guideView.dialogVisible) {
            guideView.handleDialogEvent(GuideProgrammeDialogEvent.BACK)
            return
        }
        if (playback.recoveryVisible) {
            playback.handleRecoveryDialog(GuideProgrammeDialogEvent.BACK)
            return
        }
        if (!legalDialog.hidden) {
            legalDialog.hidden = true
            return
        }
        quickSettingsDialog.hidden = true
        if (playback.isActive()) {
            playback.showHud()
            appShell.view(WebOsSection.LIVE).focus()
        }
    }

    private fun handleDialogEvent(event: GuideProgrammeDialogEvent) {
        if (guideView.dialogVisible) {
            guideView.handleDialogEvent(event)
        } else if (playback.recoveryVisible) {
            playback.handleRecoveryDialog(event)
        } else if (!legalDialog.hidden) {
            if (event == GuideProgrammeDialogEvent.BACK || event == GuideProgrammeDialogEvent.CONFIRM) {
                (document.getElementById("close-legal") as? HTMLButtonElement)?.click()
            }
        } else {
            val buttons = quickAspectOptions.querySelectorAll("button")
            val focused = (0 until buttons.length).indexOfFirst { buttons.item(it) === document.activeElement }.coerceAtLeast(0)
            when (event) {
                GuideProgrammeDialogEvent.LEFT -> (buttons.item((focused - 1).coerceAtLeast(0)) as? HTMLElement)?.focus()
                GuideProgrammeDialogEvent.RIGHT -> (buttons.item((focused + 1).coerceAtMost((buttons.length - 1).coerceAtLeast(0))) as? HTMLElement)?.focus()
                GuideProgrammeDialogEvent.CONFIRM -> (buttons.item(focused) as? HTMLButtonElement)?.click()
                GuideProgrammeDialogEvent.BACK -> closeDialog()
            }
        }
    }

    private fun restoreCachedState() {
        val loaded = stateStore.load()
        storageProblem = loaded.error?.let { "Tárolási hiba: $it" }
        stateWritesBlocked = loaded.error != null
        val state = loaded.state
        if (state == null) {
            renderChannels()
            show(localized("Nincs mentett csatornalista; hálózati betöltés indul.", "No saved channel list; starting network download."))
            return
        }

        cachedChannels = state.channels
        channels = state.channels
        settings = state.settings
        localizer.select(settings.language)
        lastSuccessfulChannelId = state.lastChannelId?.takeIf { id -> channels.any { it.id == id } }
        recentChannelIds = normalizedChannelHistory(state.recentChannelIds, channels)
        playlistCachedAt = state.playlistUpdatedAt
        selectedChannelId = lastSuccessfulChannelId ?: channels.firstOrNull()?.id
        renderChannels()
        restoreChannelFocus()
        show(localized("${channels.size} mentett csatorna betöltve; hálózati frissítés indul.", "${channels.size} saved channels loaded; starting network refresh."))
        if (loaded.migratedFromVersion != null) persistState()
        maybeAutoplay()
    }

    private fun restoreEpgCache() {
        val cache = epgCacheStore.load() ?: return
        currentEpgUrl = cache.sourceUrl
        settingsView.setEpgSource(cache.sourceUrl)
        applyEpg(cache.programmes)
        scheduleEpgRefresh()
        show(localized("${cache.programmes.size} mentett műsor betöltve; hálózati frissítés indul.", "${cache.programmes.size} saved programmes loaded; starting network refresh."))
    }

    private fun recordSuccessfulPlayback(channelId: String) {
        val channel = channels.firstOrNull { it.id == channelId } ?: return
        if (cachedChannels.none { it.id == channel.id }) return
        if (lastSuccessfulChannelId == channel.id && recentChannelIds.firstOrNull() == channel.id) return
        lastSuccessfulChannelId = channel.id
        recentChannelIds = normalizedChannelHistory(listOf(channel.id) + recentChannelIds, cachedChannels)
        if (selectedFilter == WebOsChannelFilter.RECENT) renderChannels()
        persistState()
    }

    private fun persistState() {
        if (cachedChannels.isEmpty() || stateWritesBlocked) return
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

    private fun updateSettings(value: WebOsSettings) {
        val autoplayEnabled = !settings.autoPlayOnLaunch && value.autoPlayOnLaunch
        settings = value
        applySettings()
        renderChannels()
        persistState()
        schedulePlaylistRefresh()
        scheduleEpgRefresh()
        guideView.refreshCopy()
        if (autoplayEnabled) maybeAutoplay()
    }

    private fun applySettings() {
        (document.documentElement as? HTMLElement)?.style?.setProperty("--ui-scale", settings.uiScale.toString())
        playback.updateSettings(settings)
        localizer.select(settings.language)
        applyChannelDisplaySettings()
    }

    private fun scaledChannelRowHeight(): Int = (channelRowHeight(settings.channelListMode) * settings.uiScale).toInt().coerceAtLeast(48)

    private fun maybeAutoplay() {
        if (autoplayStarted || !settings.autoPlayOnLaunch || !uiActive) return
        val targetId = lastSuccessfulChannelId ?: selectedChannelId ?: channels.firstOrNull()?.id
        val index = channels.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            autoplayStarted = true
            startPlayback(index)
        }
    }

    private fun schedulePlaylistRefresh(fromNow: Boolean = false) {
        playlistRefreshTimer?.let(window::clearTimeout)
        playlistRefreshTimer = null
        if (!uiActive) return
        val updatedAt = if (fromNow) Date.now().toLong() else playlistCachedAt
        playlistRefreshTimer = scheduleRefresh(settings.playlistRefreshHours, updatedAt, ::fetchPlaylist)
    }

    private fun scheduleEpgRefresh(fromNow: Boolean = false) {
        epgRefreshTimer?.let(window::clearTimeout)
        epgRefreshTimer = null
        if (!uiActive) return
        val updatedAt = if (fromNow) Date.now().toLong() else epgCacheStore.load()?.updatedAt ?: 0L
        epgRefreshTimer = scheduleRefresh(settings.epgRefreshHours, updatedAt) { currentEpgUrl?.let(::fetchEpg) }
    }

    private fun scheduleRefresh(
        hours: Int,
        updatedAt: Long,
        action: () -> Unit,
    ): Int? {
        if (!uiActive) return null
        val delay = refreshDelayMillis(hours, updatedAt, Date.now().toLong()) ?: return null
        return window.setTimeout(action, delay)
    }

    private fun renderQuickAspectOptions() {
        quickAspectOptions.innerHTML = ""
        listOf("AUTO", "RATIO_16_9", "RATIO_4_3", "RATIO_21_9", "FILL_CROP").forEach { ratio ->
            val button = document.createElement("button") as HTMLButtonElement
            button.type = "button"
            button.textContent =
                when (ratio) {
                    "RATIO_16_9" -> "16:9"
                    "RATIO_4_3" -> "4:3"
                    "RATIO_21_9" -> "21:9"
                    "FILL_CROP" -> if (settings.language == "ENGLISH") "Fill" else "Kitöltés"
                    else -> "Auto"
                }
            button.setAttribute("aria-pressed", (playback.effectiveAspectRatio == ratio).toString())
            button.onclick = {
                playback.setTemporaryAspectRatio(ratio)
                renderQuickAspectOptions()
                (quickAspectOptions.querySelector("button[aria-pressed=\"true\"]") as? HTMLElement)?.focus()
                null
            }
            quickAspectOptions.appendChild(button)
        }
    }

    private fun localized(
        hungarian: String,
        english: String,
    ): String = if (settings.language == "ENGLISH") english else hungarian
}

internal fun displayName(channel: Channel): String = channel.name.takeUnless { it == UNKNOWN_CHANNEL_NAME_ID } ?: "Ismeretlen csatorna"

private fun emptyStateCopy(
    state: WebOsChannelEmptyState,
    query: String,
    category: String?,
    localizer: WebOsLocalizer,
): Triple<String, String, String> =
    when (state) {
        WebOsChannelEmptyState.NO_DATA -> {
            Triple(localizer.text("channels.empty.no.data.title"), localizer.text("channels.empty.no.data.description"), localizer.text("settings.refresh"))
        }

        WebOsChannelEmptyState.LOAD_FAILED -> {
            Triple(localizer.text("channels.empty.load.failed.title"), localizer.text("channels.empty.load.failed.description"), localizer.text("action.retry"))
        }

        WebOsChannelEmptyState.NO_SEARCH_RESULTS -> {
            Triple(localizer.text("channels.empty.search.title"), localizer.text("channels.empty.search.description", query), localizer.text("channels.search.clear"))
        }

        WebOsChannelEmptyState.NO_FAVORITES -> {
            Triple(localizer.text("channels.empty.favorites.title"), localizer.text("channels.empty.favorites.description"), localizer.text("channels.show.all"))
        }

        WebOsChannelEmptyState.NO_RECENT -> {
            Triple(localizer.text("channels.empty.recent.title"), localizer.text("channels.empty.recent.description"), localizer.text("channels.show.all"))
        }

        WebOsChannelEmptyState.NO_CATEGORY_RESULTS -> {
            Triple(
                localizer.text("channels.empty.category.title"),
                localizer.text("channels.empty.category.description", category?.let(::displayGroup).orEmpty()),
                localizer.text("channels.show.all"),
            )
        }
    }

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

internal fun refreshDelayMillis(
    hours: Int,
    updatedAt: Long,
    now: Long,
): Int? {
    if (hours <= 0) return null
    val interval = hours * 60L * 60L * 1_000L
    return (updatedAt + interval - now).coerceIn(1_000L, interval).toInt()
}

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

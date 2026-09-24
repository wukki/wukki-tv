package hu.wukki.tv.webos

import hu.wukki.tv.ui.navigation.AboutSettingsOption
import hu.wukki.tv.ui.navigation.DisplaySettingsOption
import hu.wukki.tv.ui.navigation.EpgSettingsOption
import hu.wukki.tv.ui.navigation.LanguageSettingsOption
import hu.wukki.tv.ui.navigation.ParentalSettingsOption
import hu.wukki.tv.ui.navigation.PlaybackSettingsOption
import hu.wukki.tv.ui.navigation.PlaylistSettingsOption
import hu.wukki.tv.ui.navigation.SettingsOptionId
import hu.wukki.tv.ui.settings.SettingsSection
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement

internal class WebOsSettingsView(
    private val localizer: WebOsLocalizer,
    private val settings: () -> WebOsSettings,
    private val update: (WebOsSettings) -> Unit,
    private val refreshPlaylist: () -> Unit,
    private val refreshEpg: () -> Unit,
    private val channelCount: () -> Int,
    private val playlistUpdatedAt: () -> Long,
    private val platformLabel: () -> String,
) {
    private val detail = settingsElement<HTMLElement>("settings-detail")
    private val platform = settingsElement<HTMLElement>("platform")
    private val sourceInput = settingsElement<HTMLElement>("source-url")
    private val loadPlaylist = settingsElement<HTMLButtonElement>("load-playlist")
    private val diagnosticInput = settingsElement<HTMLElement>("diagnostic-url")
    private val playDirect = settingsElement<HTMLButtonElement>("play-direct")
    private val categories = mutableListOf<HTMLButtonElement>()
    private val optionRows = mutableListOf<HTMLElement>()
    private var section: SettingsSection? = null
    private var epgUrl: String? = null
    var onCategoryFocused: (Int) -> Unit = {}
    var onCategoryActivated: (Int) -> Unit = {}
    var onOptionFocused: (Int) -> Unit = {}

    val detailOpen: Boolean get() = section != null

    fun configure() {
        val nodes = document.querySelectorAll("#view-settings .settings-categories button")
        for (index in 0 until nodes.length) {
            val button = nodes.item(index) as? HTMLButtonElement ?: continue
            button.onfocus = {
                onCategoryFocused(index)
                null
            }
            button.onclick = {
                onCategoryActivated(index)
                null
            }
            categories += button
        }
        renderHome()
    }

    fun setEpgSource(url: String?) {
        epgUrl = url
        if (section == SettingsSection.EPG) renderSection(section ?: return)
    }

    fun refreshCopy() {
        localizer.applyStaticCopy()
        section?.let(::renderSection) ?: renderHome()
    }

    fun open(target: SettingsSection) {
        section = target
        categories.forEachIndexed { index, button -> button.classList.toggle("is-active", index == target.ordinal) }
        renderSection(target)
    }

    fun close() {
        section = null
        categories.forEach { it.classList.remove("is-active") }
        renderHome()
    }

    fun focusCategory(index: Int) {
        categories.getOrNull(index.coerceIn(0, (categories.size - 1).coerceAtLeast(0)))?.let(::focusSettingsElement)
    }

    fun focusOption(index: Int) {
        optionRows.getOrNull(index.coerceIn(0, (optionRows.size - 1).coerceAtLeast(0)))?.let(::focusSettingsElement)
    }

    fun adjust(
        option: SettingsOptionId,
        delta: Int,
    ) {
        val focusedIndex = optionRows.indexOf(document.activeElement).takeIf { it >= 0 }
        val current = settings()
        val next =
            when (option) {
                PlaybackSettingsOption.AUTOPLAY -> current.copy(autoPlayOnLaunch = !current.autoPlayOnLaunch)

                PlaybackSettingsOption.VOLUME -> current.copy(volume = (current.volume + delta * 5).coerceIn(0, 100))

                PlaybackSettingsOption.BUFFER -> current

                // HTML5/webOS exposes no controllable buffer profile.
                PlaybackSettingsOption.ASPECT_RATIO -> current.copy(aspectRatio = cycle(listOf("AUTO", "RATIO_16_9", "RATIO_4_3", "RATIO_21_9", "FILL_CROP"), current.aspectRatio, delta))

                PlaybackSettingsOption.RECONNECT -> current.copy(autoReconnect = !current.autoReconnect)

                PlaybackSettingsOption.RETRIES -> current.copy(reconnectAttempts = (current.reconnectAttempts + delta).coerceIn(1, 10))

                DisplaySettingsOption.UI_SCALE -> current.copy(uiScale = cycle(listOf(.9, 1.0, 1.15), current.uiScale, delta))

                DisplaySettingsOption.CHANNEL_LIST -> current.copy(channelListMode = cycle(listOf("COMPACT", "NORMAL", "DETAILED"), current.channelListMode, delta))

                DisplaySettingsOption.PROGRAMME -> current.copy(showChannelProgramme = !current.showChannelProgramme)

                DisplaySettingsOption.MINI_GUIDE -> current.copy(showMiniGuide = !current.showMiniGuide)

                DisplaySettingsOption.LOGOS -> current.copy(showLogos = !current.showLogos)

                DisplaySettingsOption.PROGRAMME_IMAGES -> current.copy(showProgrammeImages = !current.showProgrammeImages)

                EpgSettingsOption.SCHEDULE -> current.copy(epgRefreshHours = cycle(listOf(0, 6, 12, 24), current.epgRefreshHours, delta))

                PlaylistSettingsOption.SCHEDULE -> current.copy(playlistRefreshHours = cycle(listOf(0, 6, 24), current.playlistRefreshHours, delta))

                LanguageSettingsOption.LANGUAGE -> current.copy(language = if (current.language == "HUNGARIAN") "ENGLISH" else "HUNGARIAN")

                else -> current
            }
        if (next != current) update(next)
        section?.let(::renderSection)
        focusedIndex?.let(::focusOption)
    }

    fun activate(option: SettingsOptionId) {
        when (option) {
            EpgSettingsOption.REFRESH -> refreshEpg()
            PlaylistSettingsOption.REFRESH -> refreshPlaylist()
            AboutSettingsOption.PRIVACY -> openLegal("settings.about.privacy", "privacy")
            AboutSettingsOption.LICENSES -> openLegal("settings.about.licenses.title", "vlc_notice")
            is AboutSettingsOption, is ParentalSettingsOption -> Unit
            else -> adjust(option, 1)
        }
    }

    private fun renderHome() {
        optionRows.clear()
        detail.innerHTML = ""
        val home = document.createElement("div") as HTMLElement
        home.id = "settings-home"
        home.className = "settings-home"
        val heading = document.createElement("h2") as HTMLElement
        heading.textContent = localizer.text("settings.home")
        val hint = document.createElement("p") as HTMLElement
        hint.textContent = if (localizer.language == "ENGLISH") "Choose a category from the list." else "A bal oldali listából válassz egy kategóriát."
        home.appendChild(heading)
        home.appendChild(hint)
        detail.appendChild(home)
    }

    private fun renderSection(target: SettingsSection) {
        detail.innerHTML = ""
        optionRows.clear()
        val title = document.createElement("h2") as HTMLElement
        title.textContent = localizer.text(sectionKey(target))
        detail.appendChild(title)
        when (target) {
            SettingsSection.PLAYBACK -> renderPlayback()
            SettingsSection.EPG -> renderEpg()
            SettingsSection.DISPLAY -> renderDisplay()
            SettingsSection.PARENTAL -> row(ParentalSettingsOption.INFORMATION, "settings.parental.coming", "settings.parental.description", "")
            SettingsSection.PLAYLISTS -> renderPlaylists()
            SettingsSection.LANGUAGE -> row(LanguageSettingsOption.LANGUAGE, "settings.language.title", "settings.language.notice", languageLabel(settings().language))
            SettingsSection.ABOUT -> renderAbout()
        }
    }

    private fun renderPlayback() {
        val value = settings()
        row(PlaybackSettingsOption.AUTOPLAY, "settings.playback.autoplay", "settings.playback.autoplay.description", toggleLabel(value.autoPlayOnLaunch))
        row(PlaybackSettingsOption.VOLUME, "settings.playback.volume", "settings.playback.volume.description", "${value.volume}%")
        row(PlaybackSettingsOption.BUFFER, "settings.playback.buffer", "settings.playback.buffer.description", unsupported(), disabled = true)
        row(PlaybackSettingsOption.ASPECT_RATIO, "settings.playback.aspect", "settings.playback.aspect.description", aspectLabel(value.aspectRatio))
        row(PlaybackSettingsOption.RECONNECT, "settings.playback.reconnect", "settings.playback.reconnect.description", toggleLabel(value.autoReconnect))
        row(PlaybackSettingsOption.RETRIES, "settings.playback.attempts", "settings.playback.attempts.description", value.reconnectAttempts.toString())
    }

    private fun renderEpg() {
        row(EpgSettingsOption.SCHEDULE, "settings.epg.refresh", "settings.epg.refresh.description", refreshLabel(settings().epgRefreshHours))
        row(EpgSettingsOption.REFRESH, "settings.epg.source", "settings.epg.url", epgUrl ?: localizer.text("settings.no.sources"), action = localizer.text("settings.refresh"))
    }

    private fun renderDisplay() {
        val value = settings()
        val scale =
            when (value.uiScale) {
                .9 -> "settings.display.small"
                1.15 -> "settings.display.large"
                else -> "settings.display.normal"
            }
        row(DisplaySettingsOption.UI_SCALE, "settings.display.scale", "settings.display.scale.description", localizer.text(scale))
        row(DisplaySettingsOption.CHANNEL_LIST, "settings.display.channel.list", "settings.display.channel.list.description", localizer.text("settings.display.channel.list.${value.channelListMode.lowercase()}"))
        row(DisplaySettingsOption.PROGRAMME, "settings.display.programme", "settings.display.programme.description", toggleLabel(value.showChannelProgramme))
        row(DisplaySettingsOption.MINI_GUIDE, "settings.display.mini.guide", "settings.display.mini.guide.description", toggleLabel(value.showMiniGuide))
        row(DisplaySettingsOption.LOGOS, "settings.display.logos", "settings.display.logos.description", toggleLabel(value.showLogos))
        row(DisplaySettingsOption.PROGRAMME_IMAGES, "settings.display.programme.images", "settings.display.programme.images.description", toggleLabel(value.showProgrammeImages))
    }

    private fun renderPlaylists() {
        row(PlaylistSettingsOption.SCHEDULE, "settings.playlist.refresh", "settings.playlist.refresh.description", refreshLabel(settings().playlistRefreshHours))
        val updated = playlistUpdatedAt().takeIf { it > 0 }?.let { DateLabel.format(it) } ?: localizer.text("settings.not.updated")
        row(PlaylistSettingsOption.REFRESH, "settings.playlist.source", "settings.playlist.url", "$channelCountValue · $updated", action = localizer.text("settings.refresh"))
    }

    private fun renderAbout() {
        row(AboutSettingsOption.APPLICATION, "settings.about", null, "Wukki TV")
        row(AboutSettingsOption.VERSION, "settings.about.version", null, WebOsBuildInfo.DISPLAY_VERSION)
        row(AboutSettingsOption.BUILD, "settings.about.build", null, WebOsBuildInfo.BUILD_ID)
        row(AboutSettingsOption.ENGINE, "settings.about.engine", null, "HTML5 video / webOS")
        row(AboutSettingsOption.PLATFORM, "settings.about.platform", null, platformLabel())
        row(AboutSettingsOption.OS, "settings.about.os", null, window.navigator.userAgent)
        row(AboutSettingsOption.DEVICE_ID, "settings.about.device.id", null, if (localizer.language == "ENGLISH") "Local webOS installation" else "Helyi webOS-telepítés")
        row(AboutSettingsOption.STORAGE, "settings.about.storage", null, if (localizer.language == "ENGLISH") "Browser local storage" else "Böngésző helyi tárhelye")
        row(AboutSettingsOption.PRIVACY, "settings.about.privacy", null, localizer.text("action.open"))
        row(AboutSettingsOption.LICENSES, "settings.about.licenses.title", null, localizer.text("action.open"))
        renderDiagnostics()
    }

    private fun renderDiagnostics() {
        val group = document.createElement("div") as HTMLElement
        group.className = "settings-about-diagnostics"
        val heading = document.createElement("h3") as HTMLElement
        heading.textContent = if (localizer.language == "ENGLISH") "Diagnostics" else "Diagnosztika"
        platform.className = "platform-info"
        loadPlaylist.textContent = localizer.text("settings.refresh")
        playDirect.textContent = if (localizer.language == "ENGLISH") "Play diagnostic stream" else "Diagnosztikai stream lejátszása"
        group.appendChild(heading)
        group.appendChild(platform)
        group.appendChild(sourceInput)
        group.appendChild(loadPlaylist)
        group.appendChild(diagnosticInput)
        group.appendChild(playDirect)
        detail.appendChild(group)
    }

    private fun row(
        option: SettingsOptionId,
        titleKey: String,
        descriptionKey: String?,
        value: String,
        action: String? = null,
        disabled: Boolean = false,
    ) {
        val index = optionRows.size
        val row = document.createElement("button") as HTMLButtonElement
        row.type = "button"
        row.className = "settings-option"
        if (disabled) row.setAttribute("aria-disabled", "true")
        row.setAttribute("data-option-index", index.toString())
        val copy = document.createElement("span") as HTMLElement
        copy.className = "settings-option-copy"
        val title = document.createElement("strong") as HTMLElement
        title.textContent = localizer.text(titleKey)
        copy.appendChild(title)
        descriptionKey?.let {
            val description = document.createElement("small") as HTMLElement
            description.textContent = localizer.text(it)
            copy.appendChild(description)
        }
        val selected = document.createElement("span") as HTMLElement
        selected.className = "settings-option-value"
        selected.textContent = action ?: value
        if (action != null) selected.classList.add("is-action")
        row.appendChild(copy)
        row.appendChild(selected)
        row.onfocus = {
            onOptionFocused(index)
            null
        }
        row.onclick = {
            if (!disabled) activate(option)
            null
        }
        detail.appendChild(row)
        optionRows += row
    }

    private fun openLegal(
        titleKey: String,
        stem: String,
    ) {
        val restoreIndex = optionRows.indexOf(document.activeElement).takeIf { it >= 0 }
        val suffix = if (localizer.language == "ENGLISH") "en" else "hu"
        val text = loadText("legal/${stem}_$suffix.txt") ?: localizer.text("settings.about.document.unavailable")
        settingsElement<HTMLElement>("legal-title").textContent = localizer.text(titleKey)
        settingsElement<HTMLElement>("legal-content").textContent = text
        val dialog = settingsElement<HTMLElement>("legal-dialog")
        dialog.hidden = false
        settingsElement<HTMLButtonElement>("close-legal").apply {
            onclick = {
                dialog.hidden = true
                restoreIndex?.let(::focusOption)
                null
            }
            focus()
        }
    }

    private fun loadText(path: String): String? = embeddedWebOsResource(path)

    private fun toggleLabel(enabled: Boolean) =
        if (localizer.language == "ENGLISH") {
            if (enabled) "On" else "Off"
        } else {
            if (enabled) "Be" else "Ki"
        }

    private fun unsupported() = if (localizer.language == "ENGLISH") "Controlled by the TV" else "A TV kezeli"

    private fun languageLabel(value: String) = localizer.text(if (value == "ENGLISH") "language.english" else "language.hungarian")

    private fun aspectLabel(value: String) =
        when (value) {
            "RATIO_16_9" -> "16:9"
            "RATIO_4_3" -> "4:3"
            "RATIO_21_9" -> "21:9"
            "FILL_CROP" -> if (localizer.language == "ENGLISH") "Fill / crop" else "Kitöltés / vágás"
            else -> if (localizer.language == "ENGLISH") "Automatic" else "Automatikus"
        }

    private fun refreshLabel(hours: Int) =
        when (hours) {
            6 -> localizer.text("refresh.six.hours")
            12 -> localizer.text("refresh.twelve.hours")
            24 -> localizer.text("refresh.twentyfour.hours")
            else -> localizer.text("refresh.manual")
        }

    private fun sectionKey(value: SettingsSection) = "settings.${value.name.lowercase()}"

    private val channelCountValue: String get() = localizer.text("settings.channels.count", channelCount())
}

private object DateLabel {
    fun format(millis: Long): String = js("new Date(millis).toLocaleString()") as String
}

@Suppress("UNCHECKED_CAST")
private fun <T : HTMLElement> settingsElement(id: String): T = document.getElementById(id) as? T ?: error("Missing #$id")

private fun focusSettingsElement(element: HTMLElement) {
    if (element.asDynamic().disabled != true) {
        element.focus()
        element.asDynamic().scrollIntoView(js("({block: 'nearest'})"))
    }
}

private fun <T> cycle(
    values: List<T>,
    current: T,
    delta: Int,
): T {
    val index = values.indexOf(current).coerceAtLeast(0)
    return values[((index + delta).coerceIn(0, values.lastIndex))]
}

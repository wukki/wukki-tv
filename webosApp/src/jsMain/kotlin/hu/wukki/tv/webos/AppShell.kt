package hu.wukki.tv.webos

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent

internal enum class WebOsSection(
    val route: String,
) {
    LIVE("live"),
    GUIDE("guide"),
    CHANNELS("channels"),
    SETTINGS("settings"),
}

internal val webOsSectionOrder =
    listOf(
        WebOsSection.LIVE,
        WebOsSection.GUIDE,
        WebOsSection.CHANNELS,
        WebOsSection.SETTINGS,
    )

internal fun webOsSection(route: String): WebOsSection? = webOsSectionOrder.firstOrNull { it.route == route }

internal class WebOsAppShell(
    private val onSectionActivated: (WebOsSection) -> Unit,
) {
    private val navigationButtons =
        mapOf(
            WebOsSection.LIVE to shellElement<HTMLButtonElement>("nav-live"),
            WebOsSection.GUIDE to shellElement<HTMLButtonElement>("nav-guide"),
            WebOsSection.CHANNELS to shellElement<HTMLButtonElement>("nav-channels"),
            WebOsSection.SETTINGS to shellElement<HTMLButtonElement>("nav-settings"),
        )
    private val sectionViews =
        mapOf(
            WebOsSection.LIVE to shellElement<HTMLElement>("view-live"),
            WebOsSection.GUIDE to shellElement<HTMLElement>("view-guide"),
            WebOsSection.CHANNELS to shellElement<HTMLElement>("view-channels"),
            WebOsSection.SETTINGS to shellElement<HTMLElement>("view-settings"),
        )

    var activeSection = WebOsSection.CHANNELS
        private set

    fun configure() {
        navigationButtons.forEach { (section, button) ->
            button.onclick = {
                activate(section)
                null
            }
        }
        activate(activeSection)
    }

    fun activate(section: WebOsSection) {
        activeSection = section
        document.body?.setAttribute("data-section", section.route)
        navigationButtons.forEach { (candidate, button) ->
            val active = candidate == section
            button.setAttribute("aria-selected", active.toString())
            if (active) button.classList.add("is-active") else button.classList.remove("is-active")
        }
        sectionViews.forEach { (candidate, view) ->
            if (candidate == section) view.removeAttribute("hidden") else view.setAttribute("hidden", "")
        }
        onSectionActivated(section)
    }

    fun handleNavigationKey(
        event: KeyboardEvent,
        onEnterContent: () -> Unit,
    ): Boolean {
        val index = webOsSectionOrder.indexOfFirst { navigationButtons[it] === document.activeElement }
        if (index < 0) return false
        when (event.keyCode) {
            37, 39 -> {
                event.preventDefault()
                val delta = if (event.keyCode == 37) -1 else 1
                val target = (index + delta).coerceIn(webOsSectionOrder.indices)
                navigationButtons.getValue(webOsSectionOrder[target]).focus()
            }

            40 -> {
                event.preventDefault()
                onEnterContent()
            }

            13 -> {
                event.preventDefault()
                navigationButtons.getValue(webOsSectionOrder[index]).click()
            }

            else -> {
                return false
            }
        }
        return true
    }

    fun view(section: WebOsSection): HTMLElement = sectionViews.getValue(section)
}

internal enum class PlaybackSourceAction {
    KEEP_PLAYING,
    RESUME,
    REPLACE,
}

/** Route changes never participate in this decision, so opening another view cannot reload media. */
internal fun playbackSourceAction(
    currentSource: String?,
    requestedSource: String,
    paused: Boolean,
): PlaybackSourceAction =
    when {
        currentSource != requestedSource -> PlaybackSourceAction.REPLACE
        paused -> PlaybackSourceAction.RESUME
        else -> PlaybackSourceAction.KEEP_PLAYING
    }

private inline fun <reified T : HTMLElement> shellElement(id: String): T = requireNotNull(document.getElementById(id)) { "Missing #$id" } as T

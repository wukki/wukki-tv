package hu.wukki.tv.webos

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

/** Loads the same property bundles that back the Compose clients. */
internal class WebOsLocalizer(
    initialLanguage: String,
) {
    private val bundles = mutableMapOf<String, Map<String, String>>()
    var language: String = initialLanguage
        private set

    init {
        bundles["HUNGARIAN"] = loadBundle("i18n/messages_hu.properties")
        bundles["ENGLISH"] = loadBundle("i18n/messages_en.properties")
    }

    fun select(value: String) {
        language = if (value == "ENGLISH") "ENGLISH" else "HUNGARIAN"
        document.documentElement?.setAttribute("lang", if (language == "ENGLISH") "en" else "hu")
        applyStaticCopy()
    }

    fun text(
        key: String,
        vararg arguments: Any?,
    ): String {
        val template = bundles[language]?.get(key) ?: bundles["ENGLISH"]?.get(key) ?: fallback[key] ?: key
        return arguments.foldIndexed(template) { index, value, argument -> value.replace("{$index}", argument?.toString().orEmpty()) }
    }

    fun applyStaticCopy() {
        set("nav-live", "nav.live", child = true)
        set("nav-guide", "nav.guide", child = true)
        set("nav-channels", "nav.channels", child = true)
        set("nav-settings", "nav.settings", child = true)
        (document.querySelector("#view-channels .view-heading h1") as? HTMLElement)?.textContent = text("channels.title")
        (document.querySelector("#view-settings .view-heading h1") as? HTMLElement)?.textContent = text("settings.title")
        (document.getElementById("guide-title") as? HTMLElement)?.textContent = text("epg.guide.title")
        set("live-empty", "live.empty")
        set("live-preview-label", "channels.preview")
        set("quick-settings", "playback.quick.title")
        set("playback-recovery-title", "playback.error")
        set("playback-recovery-help", "playback.recovery.help")
        set("retry-playback", "playback.recovery.retry")
        set("open-channels-after-error", "nav.channels")
        set("toggle-playback-details", "playback.recovery.details")
        set("clear-channel-search", "channels.search.clear")
        set("open-preview-channel", "action.open")
        set("channel-preview-now-label", "epg.guide.now")
        set("channel-preview-next-label", "epg.next")
        literal("previous-channel", "Előző", "Previous")
        literal("channel-down", "Csatorna −", "Channel −")
        literal("channel-up", "Csatorna +", "Channel +")
        literal("stop", "Leállítás", "Stop")
        literal(
            "quick-unsupported-note",
            "A hangsáv és a felirat kiválasztását ez a webOS lejátszó nem teszi elérhetővé.",
            "This webOS player does not expose audio-track or subtitle selection.",
        )
        (document.getElementById("channel-search") as? HTMLElement)?.setAttribute("placeholder", text("channels.search"))
        val keys = listOf("settings.playback", "settings.epg", "settings.display", "settings.parental", "settings.playlists", "settings.language", "settings.about")
        val labels = document.querySelectorAll(".settings-category-label")
        keys.forEachIndexed { index, key -> (labels.item(index) as? HTMLElement)?.textContent = text(key) }
        (document.getElementById("quick-settings-title") as? HTMLElement)?.textContent = text("playback.quick.title")
        (document.getElementById("quick-settings-temporary") as? HTMLElement)?.textContent = text("playback.quick.temporary")
        (document.getElementById("close-quick-settings") as? HTMLElement)?.textContent = text("action.close")
        (document.getElementById("close-legal") as? HTMLElement)?.textContent = text("action.close")
    }

    private fun set(
        id: String,
        key: String,
        child: Boolean = false,
    ) {
        val element = document.getElementById(id) as? HTMLElement ?: return
        val target = if (child) element.querySelector("span") as? HTMLElement else element
        target?.textContent = text(key)
    }

    private fun literal(
        id: String,
        hungarian: String,
        english: String,
    ) {
        (document.getElementById(id) as? HTMLElement)?.textContent = if (language == "ENGLISH") english else hungarian
    }

    private fun loadBundle(path: String): Map<String, String> = embeddedWebOsResource(path)?.let(::parseProperties).orEmpty()

    private fun parseProperties(source: String): Map<String, String> =
        source.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') && '=' in it }.associate { line ->
            val separator = line.indexOf('=')
            line.substring(0, separator).trim() to decodeProperties(line.substring(separator + 1))
        }

    private fun decodeProperties(value: String): String {
        var decoded = value.replace("\\n", "\n").replace("\\t", "\t")
        val unicode = Regex("\\\\u([0-9a-fA-F]{4})")
        decoded =
            unicode.replace(decoded) { match ->
                match.groupValues[1]
                    .toInt(16)
                    .toChar()
                    .toString()
            }
        return decoded.replace("\\:", ":").replace("\\=", "=").replace("\\\\", "\\")
    }

    private companion object {
        val fallback =
            mapOf(
                "nav.live" to "Élő adás",
                "nav.guide" to "Műsorújság",
                "nav.channels" to "Csatornák",
                "nav.settings" to "Beállítások",
                "action.close" to "Bezárás",
                "action.open" to "Megnyitás",
                "settings.home" to "Állítsd be az alkalmazást\na saját igényeid szerint.",
                "language.hungarian" to "Magyar",
                "language.english" to "English",
            )
    }
}

internal fun embeddedWebOsResource(path: String): String? =
    try {
        val resources = window.asDynamic().WUKKI_EMBEDDED_RESOURCES
        if (resources == null) null else resources[path] as? String
    } catch (_: Throwable) {
        null
    }

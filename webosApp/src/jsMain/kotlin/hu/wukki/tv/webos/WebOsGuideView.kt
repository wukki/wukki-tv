package hu.wukki.tv.webos

import hu.wukki.tv.Channel
import hu.wukki.tv.Programme
import hu.wukki.tv.UNKNOWN_CHANNEL_NAME_ID
import hu.wukki.tv.ui.guide.GuideProgrammeDialogAction
import hu.wukki.tv.ui.guide.GuideProgrammeDialogEffect
import hu.wukki.tv.ui.guide.GuideProgrammeDialogEvent
import hu.wukki.tv.ui.guide.GuideProgrammeDialogState
import hu.wukki.tv.ui.navigation.RemoteKey
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import kotlin.js.Date
import kotlin.math.max
import kotlin.math.min

@Suppress("LargeClass", "TooManyFunctions")
internal class WebOsGuideView(
    private val localizer: WebOsLocalizer,
    private val channels: () -> List<Channel>,
    private val programmesFor: (Channel) -> List<Programme>,
    private val selectedChannelId: () -> String?,
    private val showLogos: () -> Boolean,
    private val showProgrammeImages: () -> Boolean,
    private val openChannel: (String) -> Unit,
) {
    private val actions = guideElement<HTMLElement>("guide-actions")
    private val date = guideElement<HTMLElement>("guide-date")
    private val timelineHeader = guideElement<HTMLElement>("guide-timeline")
    private val rows = guideElement<HTMLElement>("guide-rows")
    private val rowsSpacer = guideElement<HTMLElement>("guide-rows-spacer")
    private val empty = guideElement<HTMLElement>("guide-empty")
    private val dialog = guideElement<HTMLElement>("guide-programme-dialog")
    private val dialogImage = guideElement<HTMLImageElement>("guide-dialog-image")
    private val dialogChannel = guideElement<HTMLElement>("guide-dialog-channel")
    private val dialogTitle = guideElement<HTMLElement>("guide-dialog-title")
    private val dialogTime = guideElement<HTMLElement>("guide-dialog-time")
    private val dialogDescription = guideElement<HTMLElement>("guide-dialog-description")
    private val dialogNext = guideElement<HTMLElement>("guide-dialog-next")
    private val dialogCancel = guideElement<HTMLButtonElement>("guide-dialog-cancel")
    private val dialogOpen = guideElement<HTMLButtonElement>("guide-dialog-open")
    private var navigation =
        WebOsGuideNavigationState(
            focusTime = Date.now().toLong(),
            windowStart = startOfLocalDay(Date.now().toLong()),
        )
    private var dialogState = GuideProgrammeDialogState()
    private var dialogChannelValue: Channel? = null
    private var dialogProgramme: Programme? = null
    private val layoutScale: Float get() =
        hu.wukki.tv.ui.layout.DisplayLayout.settingsScale(
            (document.querySelector(".guide-card") as? HTMLElement)?.clientWidth?.toFloat() ?: 1116f,
            (document.querySelector(".guide-card") as? HTMLElement)?.clientHeight?.toFloat() ?: 892f,
        )
    private val rowHeight: Int get() = (112f * layoutScale).toInt()
    private val windowMillis: Long get() = (timelineHeader.clientWidth.coerceAtLeast(1) / (6f * layoutScale) * 60_000).toLong()
    private var initialised = false
    private var timelineCache: WebOsGuideTimeline? = null
    private val programmeCache = mutableMapOf<String, List<Programme>>()

    val dialogVisible: Boolean get() = !dialog.hidden

    fun configure() {
        rows.onscroll = {
            renderRows(focus = false)
            null
        }
        dialogCancel.onclick = {
            closeDialog()
            null
        }
        dialogOpen.onclick = {
            openDialogChannel()
            null
        }
        render()
    }

    fun activate() {
        invalidateDataCache()
        initialise()
        render()
        focusCurrent()
    }

    fun updateData() {
        invalidateDataCache()
        initialise()
        render()
    }

    fun refreshClock() {
        if (document.body?.getAttribute("data-section") == "guide") {
            renderTimeline()
            renderRows(focus = false)
        }
    }

    fun refreshCopy() {
        render()
        if (dialogVisible) renderDialog()
    }

    fun focusCurrent() {
        when (navigation.zone) {
            WebOsGuideFocusZone.HEADER -> actionButton(navigation.headerIndex)?.focus()
            WebOsGuideFocusZone.CHANNELS -> focusedRowElement()?.querySelector(".guide-channel")?.asDynamic()?.focus()
            WebOsGuideFocusZone.PROGRAMMES -> focusedProgrammeElement()?.focus()
        }
    }

    @Suppress("CognitiveComplexMethod", "CyclomaticComplexMethod")
    fun handleRemoteKey(key: RemoteKey) {
        initialise()
        val visibleChannels = visibleChannels()
        val channelIndex = visibleChannels.indexOfFirst { it.id == navigation.focusedChannelId }.let { if (it < 0) 0 else it }
        when (navigation.zone) {
            WebOsGuideFocusZone.HEADER -> {
                navigation =
                    when (key) {
                        RemoteKey.LEFT -> navigation.copy(headerIndex = (navigation.headerIndex - 1).coerceAtLeast(0))
                        RemoteKey.RIGHT -> navigation.copy(headerIndex = (navigation.headerIndex + 1).coerceAtMost(WebOsGuideHeaderAction.entries.lastIndex))
                        RemoteKey.DOWN -> navigation.copy(zone = if (visibleChannels.isEmpty()) WebOsGuideFocusZone.HEADER else WebOsGuideFocusZone.CHANNELS)
                        else -> navigation
                    }
            }

            WebOsGuideFocusZone.CHANNELS -> {
                when (key) {
                    RemoteKey.LEFT -> {
                        navigation = navigation.copy(zone = WebOsGuideFocusZone.HEADER)
                    }

                    RemoteKey.RIGHT -> {
                        navigation = navigation.copy(zone = WebOsGuideFocusZone.PROGRAMMES)
                    }

                    RemoteKey.UP -> {
                        if (channelIndex == 0) navigation = navigation.copy(zone = WebOsGuideFocusZone.HEADER) else moveChannel(-1)
                    }

                    RemoteKey.DOWN -> {
                        moveChannel(1)
                    }

                    RemoteKey.CONFIRM -> {
                        navigation = navigation.copy(zone = WebOsGuideFocusZone.PROGRAMMES)
                    }
                }
            }

            WebOsGuideFocusZone.PROGRAMMES -> {
                when (key) {
                    RemoteKey.UP -> {
                        if (channelIndex == 0) navigation = navigation.copy(zone = WebOsGuideFocusZone.CHANNELS) else moveChannel(-1)
                    }

                    RemoteKey.DOWN -> {
                        moveChannel(1)
                    }

                    RemoteKey.LEFT -> {
                        moveProgramme(-1)
                    }

                    RemoteKey.RIGHT -> {
                        moveProgramme(1)
                    }

                    RemoteKey.CONFIRM -> {
                        return
                    }
                }
            }
        }
        ensureFocusedRowVisible()
        render()
        focusCurrent()
    }

    fun confirm() {
        initialise()
        when (navigation.zone) {
            WebOsGuideFocusZone.HEADER -> {
                activateHeader(WebOsGuideHeaderAction.entries[navigation.headerIndex])
            }

            WebOsGuideFocusZone.CHANNELS -> {
                navigation = navigation.copy(zone = WebOsGuideFocusZone.PROGRAMMES)
                render()
                focusCurrent()
            }

            WebOsGuideFocusZone.PROGRAMMES -> {
                focusedProgramme()?.let { (channel, programme) -> openDetails(channel, programme) }
            }
        }
    }

    fun handleDialogEvent(event: GuideProgrammeDialogEvent) {
        if (!dialogVisible) return
        val transition = dialogState.reduce(event)
        dialogState = transition.state
        when (transition.effect) {
            GuideProgrammeDialogEffect.DISMISS -> {
                closeDialog()
            }

            GuideProgrammeDialogEffect.OPEN_CHANNEL -> {
                openDialogChannel()
            }

            GuideProgrammeDialogEffect.NONE -> {
                renderDialogActions()
            }
        }
    }

    private fun initialise() {
        val visibleChannels = visibleChannels()
        val timeline = timeline()
        if (visibleChannels.isEmpty()) {
            navigation = navigation.copy(focusedChannelId = null, focusedProgrammeKey = null, zone = WebOsGuideFocusZone.HEADER)
            initialised = true
            return
        }
        val channel =
            visibleChannels.firstOrNull { it.id == navigation.focusedChannelId }
                ?: visibleChannels.firstOrNull { it.id == selectedChannelId() }
                ?: visibleChannels.first()
        val requestedTime = if (initialised) navigation.focusTime else Date.now().toLong().coerceIn(timeline.start, timeline.end - 1)
        val programme = guideProgrammeAt(channelProgrammes(channel), requestedTime)
        navigation =
            navigation.copy(
                focusedChannelId = channel.id,
                focusedProgrammeKey = programme?.webOsGuideKey(),
                focusTime = requestedTime,
                windowStart = if (initialised) guideWindowContaining(requestedTime, navigation.windowStart, timeline, windowMillis) else guideWindowStart(requestedTime - 30L * 60L * 1_000L, timeline, windowMillis),
            )
        initialised = true
    }

    private fun activateHeader(action: WebOsGuideHeaderAction) {
        val now = Date.now().toLong()
        val timeline = timeline()
        navigation = navigation.copy(headerIndex = action.ordinal, zone = WebOsGuideFocusZone.HEADER)
        when (action) {
            WebOsGuideHeaderAction.ALL,
            WebOsGuideHeaderAction.FAVORITES,
            -> {
                navigation = navigation.copy(favoritesOnly = action == WebOsGuideHeaderAction.FAVORITES)
                initialise()
            }

            WebOsGuideHeaderAction.NOW -> {
                navigation = navigation.copy(favoritesOnly = false)
                focusAt(now, selectedChannelId(), WebOsGuideFocusZone.PROGRAMMES)
            }

            WebOsGuideHeaderAction.TONIGHT -> {
                focusAt(localTime(now, hour = 20), navigation.focusedChannelId, WebOsGuideFocusZone.PROGRAMMES)
            }

            WebOsGuideHeaderAction.PREVIOUS_DAY -> {
                focusAt(addLocalDays(navigation.focusTime, -1), navigation.focusedChannelId, WebOsGuideFocusZone.HEADER)
            }

            WebOsGuideHeaderAction.NEXT_DAY -> {
                focusAt(addLocalDays(navigation.focusTime, 1), navigation.focusedChannelId, WebOsGuideFocusZone.HEADER)
            }
        }
        navigation = navigation.copy(focusTime = navigation.focusTime.coerceIn(timeline.start, timeline.end - 1))
        ensureFocusedRowVisible()
        render()
        focusCurrent()
    }

    private fun focusAt(
        requestedTime: Long,
        preferredChannelId: String?,
        zone: WebOsGuideFocusZone,
    ) {
        val timeline = timeline()
        val target = requestedTime.takeIf { it in timeline.start until timeline.end } ?: navigation.focusTime
        val visibleChannels = visibleChannels()
        val channel = visibleChannels.firstOrNull { it.id == preferredChannelId } ?: visibleChannels.firstOrNull() ?: return
        val programme = guideProgrammeAt(channelProgrammes(channel), target)
        navigation =
            navigation.copy(
                zone = zone,
                focusedChannelId = channel.id,
                focusedProgrammeKey = programme?.webOsGuideKey(),
                focusTime = target,
                windowStart = guideWindowStart(target - 30L * 60L * 1_000L, timeline, windowMillis),
            )
    }

    private fun moveChannel(delta: Int) {
        val visibleChannels = visibleChannels()
        if (visibleChannels.isEmpty()) return
        val current = visibleChannels.indexOfFirst { it.id == navigation.focusedChannelId }.let { if (it < 0) 0 else it }
        val target = (current + delta).coerceIn(0, visibleChannels.lastIndex)
        val channel = visibleChannels[target]
        val programme = guideProgrammeAt(channelProgrammes(channel), navigation.focusTime)
        navigation =
            navigation.copy(
                focusedChannelId = channel.id,
                focusedProgrammeKey = programme?.webOsGuideKey(),
                focusTime = programme?.middleTime() ?: navigation.focusTime,
            )
    }

    private fun moveProgramme(delta: Int) {
        val channel = visibleChannels().firstOrNull { it.id == navigation.focusedChannelId } ?: return
        val programmes = channelProgrammes(channel).filter { it.end > timeline().start && it.start < timeline().end }
        val target = guideAdjacentProgramme(programmes, navigation.focusedProgrammeKey, navigation.focusTime, delta)
        if (target == null) {
            if (delta < 0) navigation = navigation.copy(zone = WebOsGuideFocusZone.CHANNELS)
            return
        }
        navigation =
            navigation.copy(
                focusedProgrammeKey = target.webOsGuideKey(),
                focusTime = target.middleTime(),
                windowStart = guideWindowContaining(target.middleTime(), navigation.windowStart, timeline(), windowMillis),
            )
    }

    private fun render() {
        renderActions()
        renderTimeline()
        renderRows(focus = false)
    }

    private fun renderActions() {
        actions.innerHTML = ""
        WebOsGuideHeaderAction.entries.forEachIndexed { index, action ->
            val button = document.createElement("button") as HTMLButtonElement
            button.type = "button"
            button.textContent = localizer.text(action.labelKey)
            button.setAttribute("data-guide-action", index.toString())
            val selected = (action == WebOsGuideHeaderAction.ALL && !navigation.favoritesOnly) || (action == WebOsGuideHeaderAction.FAVORITES && navigation.favoritesOnly)
            button.setAttribute("aria-pressed", selected.toString())
            if (navigation.zone == WebOsGuideFocusZone.HEADER && navigation.headerIndex == index) button.classList.add("is-focused")
            button.onclick = {
                navigation = navigation.copy(headerIndex = index, zone = WebOsGuideFocusZone.HEADER)
                activateHeader(action)
                null
            }
            actions.appendChild(button)
        }
    }

    private fun renderTimeline() {
        val from = navigation.windowStart
        val to = min(from + windowMillis, timeline().end)
        val duration = (to - from).coerceAtLeast(1L)
        timelineHeader.innerHTML = ""
        date.textContent = formatGuideShortDate(navigation.focusTime, localizer.language)
        var tick = firstHalfHourAtOrAfter(from)
        while (tick < to) {
            val marker = document.createElement("div") as HTMLElement
            marker.className = "guide-tick"
            marker.style.left = "${((tick - from).toDouble() / duration * 100.0)}%"
            val time = document.createElement("strong") as HTMLElement
            time.textContent = formatEpgTime(tick)
            marker.appendChild(time)
            if (startOfLocalDay(tick) == tick) {
                val day = document.createElement("small") as HTMLElement
                day.textContent = formatGuideDate(tick, localizer.language)
                marker.appendChild(day)
            }
            timelineHeader.appendChild(marker)
            tick += 30L * 60L * 1_000L
        }
        appendNowLine(timelineHeader, from, to)
    }

    private fun renderRows(focus: Boolean) {
        val visibleChannels = visibleChannels()
        empty.hidden = visibleChannels.isNotEmpty()
        rows.hidden = visibleChannels.isEmpty()
        if (visibleChannels.isEmpty()) {
            empty.textContent = localizer.text(if (navigation.favoritesOnly) "epg.guide.emptyFavorites" else "channels.empty")
            rowsSpacer.innerHTML = ""
            return
        }
        rowsSpacer.style.height = "${visibleChannels.size * rowHeight}px"
        val range = guideVisibleRows(visibleChannels.size, rows.scrollTop.toInt(), rows.clientHeight.coerceAtLeast(rowHeight * 5), rowHeight = rowHeight)
        rowsSpacer.innerHTML = ""
        range.forEach { index -> rowsSpacer.appendChild(renderRow(visibleChannels[index], index)) }
        if (focus) focusCurrent()
    }

    private fun renderRow(
        channel: Channel,
        index: Int,
    ): HTMLElement {
        val row = document.createElement("div") as HTMLElement
        row.className = "guide-row"
        row.setAttribute("data-guide-channel-id", channel.id)
        row.style.top = "${index * rowHeight}px"
        val channelButton = document.createElement("button") as HTMLButtonElement
        channelButton.type = "button"
        channelButton.className = "guide-channel"
        if (navigation.zone == WebOsGuideFocusZone.CHANNELS && navigation.focusedChannelId == channel.id) channelButton.classList.add("is-focused")
        val number = document.createElement("span") as HTMLElement
        number.className = "guide-channel-number"
        number.textContent = channel.tvgChno?.toString() ?: "–"
        channelButton.appendChild(number)
        if (showLogos() && !channel.logo.isNullOrBlank()) {
            val logo = document.createElement("img") as HTMLImageElement
            logo.src = channel.logo.orEmpty()
            logo.alt = ""
            logo.addEventListener("error", {
                logo.hidden = true
            })
            channelButton.appendChild(logo)
        }
        val name = document.createElement("span") as HTMLElement
        name.className = "guide-channel-name"
        name.textContent = (channel.name.takeUnless { it == UNKNOWN_CHANNEL_NAME_ID } ?: localizer.text("channels.unknown")).take(1).uppercase()
        channelButton.appendChild(name)
        channelButton.onclick = {
            selectChannel(channel, WebOsGuideFocusZone.CHANNELS)
            render()
            focusCurrent()
            null
        }
        row.appendChild(channelButton)

        val strip = document.createElement("div") as HTMLElement
        strip.className = "guide-programmes"
        val from = navigation.windowStart
        val to = min(from + windowMillis, timeline().end)
        val duration = (to - from).coerceAtLeast(1L)
        val channelProgrammes = channelProgrammes(channel)
        val programmes = guideVisibleProgrammes(channelProgrammes, from, to)
        programmes.forEach { programme ->
            val clippedStart = max(programme.start, from)
            val clippedEnd = min(programme.end, to)
            val button = document.createElement("button") as HTMLButtonElement
            button.type = "button"
            button.className = "guide-programme"
            if (programme.start < from && clippedEnd - clippedStart < 40L * 60L * 1_000L) {
                button.classList.add("is-short-leading-fragment")
            }
            button.setAttribute("data-guide-programme-key", programme.webOsGuideKey())
            button.style.left = "${((clippedStart - from).toDouble() / duration * 100.0)}%"
            button.style.width = "${((clippedEnd - clippedStart).toDouble() / duration * 100.0).coerceAtLeast(1.5)}%"
            if (navigation.zone == WebOsGuideFocusZone.PROGRAMMES && navigation.focusedChannelId == channel.id && navigation.focusedProgrammeKey == programme.webOsGuideKey()) {
                button.classList.add("is-focused")
            }
            val title = document.createElement("strong") as HTMLElement
            title.textContent = programme.title.ifBlank { localizer.text("epg.untitled") }
            val time = document.createElement("small") as HTMLElement
            time.textContent = "${formatEpgTime(programme.start)} – ${formatEpgTime(programme.end)}"
            button.appendChild(title)
            button.appendChild(time)
            button.onclick = {
                selectProgramme(channel, programme)
                render()
                openDetails(channel, programme)
                null
            }
            strip.appendChild(button)
        }
        if (programmes.isEmpty()) {
            val missing = document.createElement("span") as HTMLElement
            missing.className = "guide-no-programme"
            missing.textContent = localizer.text("epg.none")
            strip.appendChild(missing)
        }
        appendNowLine(strip, from, to)
        row.appendChild(strip)
        return row
    }

    private fun appendNowLine(
        parent: HTMLElement,
        from: Long,
        to: Long,
    ) {
        val now = Date.now().toLong()
        if (now !in from until to) return
        val line = document.createElement("span") as HTMLElement
        line.className = "guide-now-line"
        line.style.left = "${((now - from).toDouble() / (to - from).toDouble() * 100.0)}%"
        if (parent == timelineHeader) {
            val label = document.createElement("strong") as HTMLElement
            label.className = "guide-now-label"
            label.textContent = formatEpgTime(now)
            line.appendChild(label)
        }
        parent.appendChild(line)
    }

    private fun selectChannel(
        channel: Channel,
        zone: WebOsGuideFocusZone,
    ) {
        val programme = guideProgrammeAt(channelProgrammes(channel), navigation.focusTime)
        navigation =
            navigation.copy(
                zone = zone,
                focusedChannelId = channel.id,
                focusedProgrammeKey = programme?.webOsGuideKey(),
                focusTime = programme?.middleTime() ?: navigation.focusTime,
            )
    }

    private fun selectProgramme(
        channel: Channel,
        programme: Programme,
    ) {
        navigation =
            navigation.copy(
                zone = WebOsGuideFocusZone.PROGRAMMES,
                focusedChannelId = channel.id,
                focusedProgrammeKey = programme.webOsGuideKey(),
                focusTime = programme.middleTime(),
                windowStart = guideWindowContaining(programme.middleTime(), navigation.windowStart, timeline(), windowMillis),
            )
    }

    private fun focusedProgramme(): Pair<Channel, Programme>? {
        val channel = visibleChannels().firstOrNull { it.id == navigation.focusedChannelId } ?: return null
        val programme = channelProgrammes(channel).firstOrNull { it.webOsGuideKey() == navigation.focusedProgrammeKey } ?: return null
        return channel to programme
    }

    private fun openDetails(
        channel: Channel,
        programme: Programme,
    ) {
        dialogChannelValue = channel
        dialogProgramme = programme
        dialogState = GuideProgrammeDialogState(canOpenChannel = true)
        dialog.hidden = false
        document.body?.classList?.add("guide-dialog-open")
        renderDialog()
    }

    private fun renderDialog() {
        val channel = dialogChannelValue ?: return
        val programme = dialogProgramme ?: return
        dialogChannel.textContent = channel.name.takeUnless { it == UNKNOWN_CHANNEL_NAME_ID } ?: localizer.text("channels.unknown")
        dialogTitle.textContent = programme.title.ifBlank { localizer.text("epg.untitled") }
        dialogTime.textContent = "${formatEpgTime(programme.start)} – ${formatEpgTime(programme.end)}"
        dialogDescription.textContent = programme.description?.takeIf(String::isNotBlank) ?: localizer.text("epg.no.description")
        val next = channelProgrammes(channel).filter { it.start >= programme.end }.minByOrNull(Programme::start)
        dialogNext.hidden = next == null
        dialogNext.textContent = next?.let { "${localizer.text("epg.next")}: ${it.title.ifBlank { localizer.text("epg.untitled") }} · ${formatEpgTime(it.start)}" }
        dialogImage.hidden = programme.imageUrl.isNullOrBlank() || !showProgrammeImages()
        if (!dialogImage.hidden) dialogImage.src = programme.imageUrl.orEmpty()
        dialogCancel.textContent = localizer.text("action.cancel")
        dialogOpen.textContent = localizer.text("action.open")
        renderDialogActions()
    }

    private fun renderDialogActions() {
        dialogCancel.classList.toggle("is-focused", dialogState.focusedAction == GuideProgrammeDialogAction.CANCEL)
        dialogOpen.classList.toggle("is-focused", dialogState.focusedAction == GuideProgrammeDialogAction.OPEN)
        if (dialogState.focusedAction == GuideProgrammeDialogAction.CANCEL) dialogCancel.focus() else dialogOpen.focus()
    }

    private fun closeDialog() {
        dialog.hidden = true
        document.body?.classList?.remove("guide-dialog-open")
        dialogChannelValue = null
        dialogProgramme = null
        render()
        focusCurrent()
    }

    private fun openDialogChannel() {
        val channelId = dialogChannelValue?.id ?: return
        closeDialog()
        openChannel(channelId)
    }

    private fun ensureFocusedRowVisible() {
        val index = visibleChannels().indexOfFirst { it.id == navigation.focusedChannelId }
        if (index < 0) return
        val top = index * rowHeight
        val bottom = top + rowHeight
        when {
            top < rows.scrollTop -> rows.scrollTop = top.toDouble()
            bottom > rows.scrollTop + rows.clientHeight -> rows.scrollTop = (bottom - rows.clientHeight).coerceAtLeast(0).toDouble()
        }
    }

    private fun focusedRowElement(): HTMLElement? = rowsSpacer.querySelector("[data-guide-channel-id=\"${navigation.focusedChannelId}\"]") as? HTMLElement

    private fun focusedProgrammeElement(): HTMLButtonElement? =
        rowsSpacer.querySelector(
            "[data-guide-programme-key=\"${navigation.focusedProgrammeKey}\"]",
        ) as? HTMLButtonElement

    private fun actionButton(index: Int): HTMLButtonElement? = actions.querySelector("[data-guide-action=\"$index\"]") as? HTMLButtonElement

    private fun visibleChannels(): List<Channel> = channels().filter { !navigation.favoritesOnly || it.favorite }

    private fun timeline(): WebOsGuideTimeline {
        timelineCache?.let { cached ->
            if (cached.start == startOfLocalDay(Date.now().toLong())) return cached
        }
        val now = Date.now().toLong()
        val start = startOfLocalDay(now)
        val latest = channels().asSequence().flatMap { channelProgrammes(it).asSequence() }.maxOfOrNull(Programme::end)
        val lastInstant = latest?.takeIf { it > start }?.minus(1L) ?: now
        return WebOsGuideTimeline(start, startOfNextLocalDay(lastInstant)).also { timelineCache = it }
    }

    private fun channelProgrammes(channel: Channel): List<Programme> = programmeCache.getOrPut(channel.id) { programmesFor(channel) }

    private fun invalidateDataCache() {
        timelineCache = null
        programmeCache.clear()
    }
}

private fun Programme.middleTime(): Long = start + (end - start) / 2L

private fun startOfLocalDay(timestamp: Long): Long {
    val date = Date(timestamp.toDouble())
    date.asDynamic().setHours(0, 0, 0, 0)
    return date.getTime().toLong()
}

private fun startOfNextLocalDay(timestamp: Long): Long {
    val date = Date(timestamp.toDouble())
    date.asDynamic().setHours(0, 0, 0, 0)
    date.asDynamic().setDate(date.getDate() + 1)
    return date.getTime().toLong()
}

private fun addLocalDays(
    timestamp: Long,
    days: Int,
): Long {
    val date = Date(timestamp.toDouble())
    date.asDynamic().setDate(date.getDate() + days)
    return date.getTime().toLong()
}

private fun localTime(
    timestamp: Long,
    hour: Int,
): Long {
    val date = Date(timestamp.toDouble())
    date.asDynamic().setHours(hour, 0, 0, 0)
    return date.getTime().toLong()
}

private fun firstHalfHourAtOrAfter(timestamp: Long): Long {
    val date = Date(timestamp.toDouble())
    val minutes = date.getMinutes()
    val roundedMinutes =
        if (minutes == 0 || minutes == 30) {
            minutes
        } else if (minutes < 30) {
            30
        } else {
            60
        }
    date.asDynamic().setMinutes(roundedMinutes, 0, 0)
    return date.getTime().toLong()
}

private fun formatGuideShortDate(
    timestamp: Long,
    language: String,
): String {
    val date = Date(timestamp.toDouble())
    val weekdays = if (language == "ENGLISH") listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") else listOf("V", "H", "K", "Sze", "Cs", "P", "Szo")
    val months = if (language == "ENGLISH") listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec") else listOf("jan.", "febr.", "márc.", "ápr.", "máj.", "jún.", "júl.", "aug.", "szept.", "okt.", "nov.", "dec.")
    return "${weekdays[date.getDay()]}, ${months[date.getMonth()]} ${date.getDate()}"
}

private fun formatGuideDate(
    timestamp: Long,
    language: String,
): String {
    val date = Date(timestamp.toDouble())
    val weekdaysHu = listOf("vasárnap", "hétfő", "kedd", "szerda", "csütörtök", "péntek", "szombat")
    val weekdaysEn = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val monthsHu = listOf("január", "február", "március", "április", "május", "június", "július", "augusztus", "szeptember", "október", "november", "december")
    val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return if (language == "ENGLISH") {
        "${weekdaysEn[date.getDay()]}, ${monthsEn[date.getMonth()]} ${date.getDate()}"
    } else {
        "${monthsHu[date.getMonth()]} ${date.getDate()}. · ${weekdaysHu[date.getDay()]}"
    }
}

private inline fun <reified T : HTMLElement> guideElement(id: String): T = requireNotNull(document.getElementById(id)) { "Missing #$id" } as T

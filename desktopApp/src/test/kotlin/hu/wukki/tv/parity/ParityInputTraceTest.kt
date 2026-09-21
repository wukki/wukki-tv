package hu.wukki.tv.parity

import hu.wukki.tv.ui.navigation.AppRemoteEffect
import hu.wukki.tv.ui.navigation.AppRemoteKey
import hu.wukki.tv.ui.navigation.AppRemoteState
import hu.wukki.tv.ui.navigation.ChannelNavigationEffect
import hu.wukki.tv.ui.navigation.ChannelNavigationState
import hu.wukki.tv.ui.navigation.ChannelRemoteFocus
import hu.wukki.tv.ui.navigation.DashboardSection
import hu.wukki.tv.ui.navigation.LiveChannelPreviewEvent
import hu.wukki.tv.ui.navigation.LiveChannelPreviewState
import hu.wukki.tv.ui.navigation.RemoteKey
import hu.wukki.tv.ui.navigation.SettingsNavigationState
import hu.wukki.tv.ui.navigation.TvFocusZone
import hu.wukki.tv.ui.navigation.reduce
import hu.wukki.tv.ui.settings.SettingsSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The JSON is also the contract for the future JS runner; expected results are not regenerated. */
class ParityInputTraceTest {
    @Test
    fun referenceInputTracesMatchSharedReducer() {
        val root = Json.parseToJsonElement(File(System.getProperty("parity.traces")).readText()).jsonObject
        for (case in root.getValue("cases").jsonArray) {
            val spec = case.jsonObject
            val initial = spec.getValue("initial").jsonObject
            var state =
                AppRemoteState(
                    section = DashboardSection.valueOf(initial.string("section")),
                    focus = TvFocusZone.valueOf(initial.string("focus")),
                    menuIndex = initial.int("menuIndex"),
                    channels = ChannelNavigationState(ChannelRemoteFocus.valueOf(initial.string("channelFocus")), initial.int("filterIndex"), initial.int("channelIndex")),
                    settings = SettingsNavigationState(initial.optional("settingsSection")?.let(SettingsSection::valueOf)),
                    channelIds = root.getValue("channelIds").jsonArray.map { it.jsonPrimitive.content },
                    selectedChannelId = root.string("selectedChannelId"),
                    searchHasText = root.bool("searchHasText"),
                    searchOpen = initial.bool("searchOpen"),
                    dialogVisible = initial.bool("dialogVisible"),
                    overlayVisible = initial.bool("overlayVisible"),
                    navigationVisible = initial.bool("navigationVisible"),
                    preview = LiveChannelPreviewState(initial.optional("previewId"), initial.int("previewSequence")),
                    number = initial.string("number"),
                    requireDoubleBack = root.bool("requireDoubleBack"),
                )
            for ((index, item) in spec.getValue("steps").jsonArray.withIndex()) {
                val step = item.jsonObject
                val event = step.getValue("event").jsonObject
                state = state.copy(nowMillis = event["nowMillis"]?.jsonPrimitive?.long ?: state.nowMillis)
                val result =
                    state.reduce(
                        AppRemoteKey(
                            remote = event.optional("remote")?.let(RemoteKey::valueOf),
                            digit = event.optional("digit"),
                            back = event.bool("back"),
                            escape = event.bool("escape"),
                            backspace = event.bool("backspace"),
                            channelDelta = event["channelDelta"]?.jsonPrimitive?.int,
                            preview = event.optional("preview")?.let(LiveChannelPreviewEvent::valueOf),
                            previousChannel = event.bool("previousChannel"),
                            quickSettings = event.bool("quickSettings"),
                        ),
                    )
                val label = "${spec.string("id")} step $index"
                assertEquals(step.getValue("expected"), projection(result.state), label)
                assertEquals(step.getValue("effects").jsonArray.map { it.jsonPrimitive.content }, result.effects.map(::effectName), label)
                assertEquals(step.bool("handled"), result.handled, label)
                state = result.state
            }
        }
    }

    private fun projection(state: AppRemoteState) =
        buildJsonObject {
            put("section", state.section.name)
            put("focus", state.focus.name)
            put("menuIndex", state.menuIndex)
            put("channelFocus", state.channels.focus.name)
            put("channelIndex", state.channels.channelIndex)
            put("filterIndex", state.channels.filterIndex)
            put("searchOpen", state.searchOpen)
            put("dialogVisible", state.dialogVisible)
            put("overlayVisible", state.overlayVisible)
            put("navigationVisible", state.navigationVisible)
            put("previewId", state.preview.channelId)
            put("previewSequence", state.preview.interactionSequence)
            put("number", state.number)
            put("settingsSection", state.settings.section?.name)
            put("exitAt", state.exitConfirmation.firstBackAtMillis)
        }

    private fun effectName(effect: AppRemoteEffect): String =
        when (effect) {
            is AppRemoteEffect.Preview -> {
                "Preview:${effect.result.effect}:${effect.result.channelIdToOpen.orEmpty()}"
            }

            is AppRemoteEffect.Back -> {
                "Back:${effect.effect}"
            }

            is AppRemoteEffect.ActivateSection -> {
                "ActivateSection:${effect.section}"
            }

            is AppRemoteEffect.SwitchChannel -> {
                "SwitchChannel:${effect.delta}"
            }

            is AppRemoteEffect.SelectNumber -> {
                "SelectNumber:${effect.number}"
            }

            is AppRemoteEffect.Dialog -> {
                "Dialog:${effect.event}"
            }

            is AppRemoteEffect.Channels -> {
                "Channels:" +
                    when (val action = effect.effect) {
                        is ChannelNavigationEffect.OpenChannel -> "OpenChannel:${action.index}"
                        is ChannelNavigationEffect.ToggleFavorite -> "ToggleFavorite:${action.index}"
                        else -> action::class.simpleName
                    }
            }

            else -> {
                effect::class.simpleName ?: error("Unnamed effect")
            }
        }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.optional(key: String) = get(key)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int

    private fun JsonObject.bool(key: String) = get(key)?.jsonPrimitive?.boolean ?: false
}

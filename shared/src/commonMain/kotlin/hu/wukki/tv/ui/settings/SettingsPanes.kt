package hu.wukki.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.AspectRatioMode
import hu.wukki.tv.BufferProfile
import hu.wukki.tv.ChannelListDisplayMode
import hu.wukki.tv.RefreshInterval
import hu.wukki.tv.WukkiBuildInfo
import hu.wukki.tv.formatByteSize
import hu.wukki.tv.ui.components.Localizer
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr

private enum class PlaybackOption { AUTOPLAY, VOLUME, BUFFER, ASPECT_RATIO, RECONNECT, RETRIES }

@Composable
internal fun PlaybackSettingsPane(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    remoteOptionIndex: Int,
    dropdownOpenRequest: Int,
    dropdownOptionIndex: Int,
    onOptionFocus: (Int) -> Unit,
    scale: Float
) {
    val settings = state.settings.playback
    val focusedOption = PlaybackOption.entries.getOrElse(remoteOptionIndex) { PlaybackOption.AUTOPLAY }
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsOptionRow(state.language, "settings.playback.autoplay", "settings.playback.autoplay.description", focusedOption == PlaybackOption.AUTOPLAY, onFocus = { onOptionFocus(0) }, scale = scale) {
            Switch(
                checked = settings.autoPlayOnLaunch != false,
                onCheckedChange = { enabled -> onOptionFocus(0); callbacks.updatePlayback { it.copy(autoPlayOnLaunch = enabled) } }
            )
        }
        SettingsOptionRow(state.language, "settings.playback.volume", "settings.playback.volume.description", focusedOption == PlaybackOption.VOLUME, onFocus = { onOptionFocus(1) }, scale = scale) {
            Row(
                modifier = Modifier.widthIn(min = 150.dp, max = 205.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${settings.volume}%", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Slider(
                    value = settings.volume.toFloat(),
                    onValueChange = { volume -> onOptionFocus(1); callbacks.setVolume(volume.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        SettingsOptionRow(state.language, "settings.playback.buffer", "settings.playback.buffer.description", focusedOption == PlaybackOption.BUFFER, onFocus = { onOptionFocus(2) }, scale = scale) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
                SettingsExposedDropdown(
                    value = settings.bufferProfile,
                    entries = BufferProfile.entries.toList(),
                    label = { it.label(state.language) },
                    onFocus = { onOptionFocus(2) },
                    openRequest = if (dropdownOptionIndex == 2) dropdownOpenRequest else 0
                ) { profile -> callbacks.setBufferProfile(profile) }
            }
        }
        SettingsOptionRow(state.language, "settings.playback.aspect", "settings.playback.aspect.description", focusedOption == PlaybackOption.ASPECT_RATIO, onFocus = { onOptionFocus(3) }, scale = scale) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
                SettingsExposedDropdown(
                    value = settings.aspectRatio ?: AspectRatioMode.AUTO,
                    entries = AspectRatioMode.entries.toList(),
                    label = { it.label(state.language) },
                    onFocus = { onOptionFocus(3) },
                    openRequest = if (dropdownOptionIndex == 3) dropdownOpenRequest else 0
                ) { ratio -> callbacks.updatePlayback { it.copy(aspectRatio = ratio) } }
            }
        }
        SettingsOptionRow(state.language, "settings.playback.reconnect", "settings.playback.reconnect.description", focusedOption == PlaybackOption.RECONNECT, onFocus = { onOptionFocus(4) }, scale = scale) {
            Switch(
                checked = settings.autoReconnect,
                onCheckedChange = { enabled -> onOptionFocus(4); callbacks.updatePlayback { it.copy(autoReconnect = enabled) } }
            )
        }
        SettingsOptionRow(state.language, "settings.playback.attempts", "settings.playback.attempts.description", focusedOption == PlaybackOption.RETRIES, onFocus = { onOptionFocus(5) }, scale = scale) {
            PlaybackStepper(
                value = settings.reconnectAttempts,
                onDecrease = { onOptionFocus(5); callbacks.adjustSetting(hu.wukki.tv.ui.navigation.PlaybackSettingsOption.RETRIES, -1) },
                onIncrease = { onOptionFocus(5); callbacks.adjustSetting(hu.wukki.tv.ui.navigation.PlaybackSettingsOption.RETRIES, 1) }
            )
        }
    }
}

@Composable
internal fun EpgSettingsPane(state: SettingsUiState, callbacks: SettingsCallbacks, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column {
        SettingsOptionRow(state.language, "settings.epg.refresh", "settings.epg.refresh.description", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
            RefreshSelector(
                state.language,
                state.settings.epgRefresh,
                intervals = RefreshInterval.entries.toList(),
                useHourlyLabels = true,
                onFocus = { onOptionFocus(0) },
                onSelect = callbacks.setEpgRefresh
            )
        }
        FixedSourceCard(
            language = state.language,
            titleKey = "settings.epg.source",
            sourceName = state.epgSource?.name ?: tr(state.language, "settings.no.sources"),
            location = state.epgSource?.location,
            updatedAt = state.epgSource?.updatedAt,
            selected = remoteOptionIndex == 1,
            onFocus = { onOptionFocus(1) },
            onRefresh = callbacks.refreshEpg,
            scale = scale
        )
    }
}

@Composable
internal fun DisplaySettingsPane(state: SettingsUiState, callbacks: SettingsCallbacks, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column {
        SettingsOptionRow(state.language, "settings.display.scale", "settings.display.scale.description", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
            val options = listOf(
                .9f to tr(state.language, "settings.display.small"),
                1f to tr(state.language, "settings.display.normal"),
                1.15f to tr(state.language, "settings.display.large")
            )
            SettingsSegmentedChoice(
                entries = options,
                selected = options.first { it.first == state.settings.display.uiScale },
                label = { it.second },
                onSelect = { option -> onOptionFocus(0); callbacks.updateDisplay { it.copy(uiScale = option.first) } }
            )
        }
        SettingsOptionRow(state.language, "settings.display.channel.list", "settings.display.channel.list.description", selected = remoteOptionIndex == 1, onFocus = { onOptionFocus(1) }, scale = scale) {
            SettingsSegmentedChoice(
                entries = ChannelListDisplayMode.entries,
                selected = state.settings.display.channelListMode ?: ChannelListDisplayMode.NORMAL,
                label = { it.label(state.language) },
                onSelect = { mode -> onOptionFocus(1); callbacks.updateDisplay { it.copy(channelListMode = mode) } }
            )
        }
        SettingsToggle(state.language, "settings.display.programme", "settings.display.programme.description", state.settings.display.showChannelProgramme, remoteOptionIndex == 2, { onOptionFocus(2) }, scale) { callbacks.updateDisplay { current -> current.copy(showChannelProgramme = it) } }
        SettingsToggle(state.language, "settings.display.mini.guide", "settings.display.mini.guide.description", state.settings.display.showMiniGuide, remoteOptionIndex == 3, { onOptionFocus(3) }, scale) { callbacks.updateDisplay { current -> current.copy(showMiniGuide = it) } }
        SettingsToggle(state.language, "settings.display.logos", "settings.display.logos.description", state.settings.display.showLogos, remoteOptionIndex == 4, { onOptionFocus(4) }, scale) { callbacks.updateDisplay { current -> current.copy(showLogos = it) } }
        SettingsToggle(state.language, "settings.display.programme.images", "settings.display.programme.images.description", state.settings.display.showProgrammeImages != false, remoteOptionIndex == 5, { onOptionFocus(5) }, scale) { callbacks.updateDisplay { current -> current.copy(showProgrammeImages = it) } }
    }
}

@Composable
internal fun PlaylistSettingsPane(state: SettingsUiState, callbacks: SettingsCallbacks, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column {
        SettingsOptionRow(state.language, "settings.playlist.refresh", "settings.playlist.refresh.description", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
            RefreshSelector(
                state.language,
                state.settings.playlistRefresh,
                intervals = listOf(RefreshInterval.MANUAL, RefreshInterval.SIX_HOURS, RefreshInterval.DAILY),
                onFocus = { onOptionFocus(0) },
                onSelect = callbacks.setPlaylistRefresh
            )
        }
        FixedSourceCard(
            language = state.language,
            titleKey = "settings.playlist.source",
            sourceName = state.playlistSource.name,
            location = state.playlistSource.location,
            updatedAt = state.playlistSource.updatedAt,
            footer = tr(state.language, "settings.channels.count", state.channelCount),
            selected = remoteOptionIndex == 1,
            onFocus = { onOptionFocus(1) },
            onRefresh = callbacks.refreshPlaylist,
            scale = scale
        )
    }
}

@Composable
private fun FixedSourceCard(
    language: AppLanguage,
    titleKey: String,
    sourceName: String,
    location: String?,
    updatedAt: Long?,
    selected: Boolean,
    onFocus: () -> Unit,
    onRefresh: () -> Unit,
    footer: String? = null,
    scale: Float
) {
    val details = footer ?: updatedAt?.let { "${tr(language, "settings.updated")}: ${formatTime(it)}" }
        ?: tr(language, "settings.not.updated")
    SettingsListRow(
        title = tr(language, titleKey),
        description = details,
        highlight = if (selected) SettingsRowHighlight.FOCUSED else SettingsRowHighlight.NONE,
        onClick = onFocus,
        scale = scale
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(sourceName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            location?.let { Text(it, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            TextButton(onClick = { onFocus(); onRefresh() }) { Text(tr(language, "settings.refresh")) }
        }
    }
}

@Composable
internal fun LanguageSettingsPane(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
    remoteOptionIndex: Int,
    dropdownOpenRequest: Int,
    dropdownOptionIndex: Int,
    onOptionFocus: (Int) -> Unit,
    scale: Float
) {
    Column {
        SettingsOptionRow(state.language, "settings.language.title", "settings.language.notice", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
                SettingsExposedDropdown(
                    value = state.language,
                    entries = AppLanguage.entries.toList(),
                    label = { language -> tr(state.language, if (language == AppLanguage.HUNGARIAN) "language.hungarian" else "language.english") },
                    onFocus = { onOptionFocus(0) },
                    openRequest = if (dropdownOptionIndex == 0) dropdownOpenRequest else 0,
                    onSelect = callbacks.setLanguage
                )
            }
        }
    }
}

@Composable
internal fun ParentalSettingsPane(
    language: AppLanguage,
    remoteOptionIndex: Int,
    onOptionFocus: (Int) -> Unit,
    scale: Float
) {
    SettingsOptionRow(
        language,
        "settings.parental.coming",
        "settings.parental.description",
        selected = remoteOptionIndex == 0,
        onFocus = { onOptionFocus(0) },
        scale = scale
    ) { }
}

@Composable
internal fun AboutSettingsPane(
    state: SettingsUiState,
    remoteOptionIndex: Int,
    openRequest: Int,
    onOptionFocus: (Int) -> Unit,
    scale: Float
) {
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    val language = state.language
    val deviceInfo = state.deviceInfo
    LaunchedEffect(openRequest) {
        if (openRequest > 0) {
            legalDocument = when (remoteOptionIndex) {
                8 -> LegalDocument.PRIVACY
                9 -> LegalDocument.LICENSES
                else -> null
            }
        }
    }
    Column {
        SettingsOptionRow(language, "settings.about", "settings.about.licenses", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) { Text("Wukki TV", fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(language, "settings.about.version", selected = remoteOptionIndex == 1, onFocus = { onOptionFocus(1) }, scale = scale) { Text(WukkiBuildInfo.VERSION, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(language, "settings.about.build", selected = remoteOptionIndex == 2, onFocus = { onOptionFocus(2) }, scale = scale) { Text(WukkiBuildInfo.BUILD, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(language, "settings.about.engine", selected = remoteOptionIndex == 3, onFocus = { onOptionFocus(3) }, scale = scale) { Text(state.playbackEngineLabel, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(language, "settings.about.platform", selected = remoteOptionIndex == 4, onFocus = { onOptionFocus(4) }, scale = scale) { Text(deviceInfo?.platform ?: tr(language, "settings.about.loading"), fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(language, "settings.about.os", selected = remoteOptionIndex == 5, onFocus = { onOptionFocus(5) }, scale = scale) { Text(deviceInfo?.osVersion ?: tr(language, "settings.about.loading"), fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(language, "settings.about.device.id", selected = remoteOptionIndex == 6, onFocus = { onOptionFocus(6) }, scale = scale) { Text(deviceInfo?.installationId ?: tr(language, "settings.about.loading"), fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(language, "settings.about.storage", selected = remoteOptionIndex == 7, onFocus = { onOptionFocus(7) }, scale = scale) {
            Text(
                deviceInfo?.let { info -> tr(language, "settings.about.storage.value", formatByteSize(info.appDataBytes), formatByteSize(info.availableStorageBytes)) }
                    ?: tr(language, "settings.about.loading"),
                fontWeight = FontWeight.SemiBold
            )
        }
        SettingsOptionRow(language, "settings.about.privacy", selected = remoteOptionIndex == 8, onFocus = { onOptionFocus(8) }, onSelect = { legalDocument = LegalDocument.PRIVACY }, scale = scale) {
            Text(tr(language, "action.open"), fontWeight = FontWeight.SemiBold)
        }
        SettingsOptionRow(language, "settings.about.licenses.title", selected = remoteOptionIndex == 9, onFocus = { onOptionFocus(9) }, onSelect = { legalDocument = LegalDocument.LICENSES }, scale = scale) {
            Text(tr(language, "action.open"), fontWeight = FontWeight.SemiBold)
        }
    }
    legalDocument?.let { document -> LegalDocumentDialog(language, document) { legalDocument = null } }
}

private enum class LegalDocument(val resourceStem: String, val titleKey: String) {
    PRIVACY("privacy", "settings.about.privacy"),
    LICENSES("vlc_notice", "settings.about.licenses.title")
}

@Composable
private fun LegalDocumentDialog(language: AppLanguage, document: LegalDocument, onDismiss: () -> Unit) {
    val text = remember(document, language) {
        val languageSuffix = if (language == AppLanguage.HUNGARIAN) "hu" else "en"
        Localizer.legalText("legal/${document.resourceStem}_$languageSuffix.txt")
            ?: tr(language, "settings.about.document.unavailable")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(language, document.titleKey), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = text,
                modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = { Button(onClick = onDismiss) { Text(tr(language, "action.close")) } }
    )
}

package hu.wukki.tv.ui.settings

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.components.WukkiBrushes
import hu.wukki.tv.ui.components.WukkiColors

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class SettingsSection { PLAYBACK, EPG, DISPLAY, PARENTAL, PLAYLISTS, LANGUAGE, ABOUT }
private val SettingsPanel = WukkiColors.surfaceOverlay
private val SettingsSurface = WukkiColors.surface
private val SettingsMuted = WukkiColors.textMuted
private val SettingsAccent = WukkiColors.primary
private const val SETTINGS_REFERENCE_WIDTH = 1116f
private const val SETTINGS_REFERENCE_HEIGHT = 892f
private enum class PlaybackOption { AUTOPLAY, VOLUME, BUFFER, ASPECT_RATIO, RECONNECT, RETRIES }

@Composable
fun SettingsScreen(
    model: WukkiModel,
    scope: CoroutineScope,
    selectedSection: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
    remoteCategoryIndex: Int = 0,
    remoteNavigationActive: Boolean = false,
    remoteOptionIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val scale = minOf(
            maxWidth.value / SETTINGS_REFERENCE_WIDTH,
            maxHeight.value / SETTINGS_REFERENCE_HEIGHT
        ).coerceIn(.70f, 1f)
        Column(Modifier.fillMaxSize()) {
            Text(
                tr(model.settings.language, "settings.title"),
                color = WukkiColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = (29f * scale).sp,
                modifier = Modifier.padding(start = 8.dp * scale, top = 8.dp * scale, bottom = 34.dp * scale)
            )
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(34.dp * scale)
            ) {
                SettingsNavigation(
                    model = model,
                    selected = selectedSection,
                    onSelect = onSectionChange,
                    remoteCategoryIndex = remoteCategoryIndex,
                    remoteNavigationActive = remoteNavigationActive,
                    scale = scale,
                    modifier = Modifier.width(430.dp * scale)
                )
                if (selectedSection == null) {
                    SettingsHome(model, scale, Modifier.weight(1f).fillMaxHeight())
                } else {
                    SettingsDetail(
                        model = model,
                        scope = scope,
                        selectedSection = selectedSection,
                        scale = scale,
                        remoteOptionIndex = remoteOptionIndex,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigation(
    model: WukkiModel,
    selected: SettingsSection?,
    onSelect: (SettingsSection?) -> Unit,
    remoteCategoryIndex: Int,
    remoteNavigationActive: Boolean,
    scale: Float,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(12.dp * scale)
    Column(
        modifier = modifier.clip(shape).background(SettingsSurface).border(1.dp, WukkiColors.border, shape)
    ) {
        SettingsSection.entries.forEachIndexed { index, item ->
            val active = item == selected
            val focused = remoteNavigationActive && selected == null && index == remoteCategoryIndex
            Row(
                modifier = Modifier.fillMaxWidth().height(81.dp * scale)
                    .background(if (active) WukkiColors.surfaceSelected else WukkiColors.transparent)
                    .border(if (focused) 2.dp else .5.dp, if (focused) SettingsAccent else WukkiColors.border.copy(alpha = .72f))
                    .clickable { onSelect(item) }.padding(horizontal = 24.dp * scale),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.title(model),
                    color = WukkiColors.textPrimary,
                    fontSize = (19f * scale).sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item == SettingsSection.LANGUAGE) {
                    Text(
                        tr(model.settings.language, "settings.language.current"),
                        color = WukkiColors.textPrimary,
                        fontSize = (16f * scale).sp,
                        modifier = Modifier.padding(end = 14.dp * scale)
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = WukkiColors.textSecondary, modifier = Modifier.size(22.dp * scale))
            }
        }
    }
}

@Composable
private fun SettingsHome(model: WukkiModel, scale: Float, modifier: Modifier) {
    Column(
        modifier = modifier.padding(bottom = 80.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SettingsGear(scale)
        Spacer(Modifier.height(52.dp * scale))
        Text(
            tr(model.settings.language, "settings.home"),
            color = WukkiColors.textSecondary,
            fontSize = (18f * scale).sp,
            lineHeight = (28f * scale).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsGear(scale: Float) {
    Icon(Icons.Outlined.Settings, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(184.dp * scale))
}

@Composable
private fun SettingsDetail(
    model: WukkiModel,
    scope: CoroutineScope,
    selectedSection: SettingsSection,
    scale: Float,
    remoteOptionIndex: Int,
    modifier: Modifier
) {
    SettingsCard(modifier) {
        when (selectedSection) {
            SettingsSection.EPG -> EpgSettings(model, scope)
            SettingsSection.PLAYLISTS -> PlaylistSettings(model, scope)
            else -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                when (selectedSection) {
                    SettingsSection.PLAYBACK -> PlaybackSettings(model, remoteOptionIndex)
                    SettingsSection.DISPLAY -> DisplaySettings(model, remoteOptionIndex)
                    SettingsSection.PARENTAL -> ParentalSettings(model)
                    SettingsSection.LANGUAGE -> LanguageSettings(model, remoteOptionIndex)
                    SettingsSection.ABOUT -> AboutSettings(model)
                    SettingsSection.EPG, SettingsSection.PLAYLISTS -> Unit
                }
            }
        }
    }
}

@Composable
private fun PlaybackSettings(model: WukkiModel, remoteOptionIndex: Int) {
    val settings = model.settings.playback
    val focusedOption = PlaybackOption.entries.getOrElse(remoteOptionIndex) { PlaybackOption.AUTOPLAY }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsOptionRow(model, "settings.playback.autoplay", "settings.playback.autoplay.description", focusedOption == PlaybackOption.AUTOPLAY) {
            Switch(
                checked = settings.autoPlayOnLaunch != false,
                onCheckedChange = { enabled -> model.updatePlayback { it.copy(autoPlayOnLaunch = enabled) } },
                colors = SwitchDefaults.colors(checkedThumbColor = WukkiColors.textPrimary, checkedTrackColor = SettingsAccent, uncheckedThumbColor = SettingsMuted, uncheckedTrackColor = WukkiColors.border)
            )
        }
        SettingsOptionRow(model, "settings.playback.volume", "settings.playback.volume.description", focusedOption == PlaybackOption.VOLUME) {
            Row(
                modifier = Modifier.widthIn(min = 150.dp, max = 205.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${settings.volume}%", color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Slider(
                    value = settings.volume.toFloat(),
                    onValueChange = { volume -> model.updatePlayback { it.copy(volume = volume.toInt()) } },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        SettingsOptionRow(model, "settings.playback.buffer", "settings.playback.buffer.description", focusedOption == PlaybackOption.BUFFER) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            PlaybackSelect(settings.bufferProfile, BufferProfile.entries.toList(), { it.label(model) }) { profile -> model.updatePlayback { it.copy(bufferProfile = profile) } }
            }
        }
        SettingsOptionRow(model, "settings.playback.aspect", "settings.playback.aspect.description", focusedOption == PlaybackOption.ASPECT_RATIO) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            PlaybackSelect(settings.aspectRatio ?: AspectRatioMode.AUTO, AspectRatioMode.entries.toList(), { it.label(model) }) { ratio -> model.updatePlayback { it.copy(aspectRatio = ratio) } }
            }
        }
        SettingsOptionRow(model, "settings.playback.reconnect", "settings.playback.reconnect.description", focusedOption == PlaybackOption.RECONNECT) {
            Switch(
                checked = settings.autoReconnect,
                onCheckedChange = { enabled -> model.updatePlayback { it.copy(autoReconnect = enabled) } },
                colors = SwitchDefaults.colors(checkedThumbColor = WukkiColors.textPrimary, checkedTrackColor = SettingsAccent, uncheckedThumbColor = SettingsMuted, uncheckedTrackColor = WukkiColors.border)
            )
        }
        SettingsOptionRow(model, "settings.playback.attempts", "settings.playback.attempts.description", focusedOption == PlaybackOption.RETRIES) {
            PlaybackStepper(
                value = settings.reconnectAttempts,
                onDecrease = { model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts - 1).coerceAtLeast(1)) } },
                onIncrease = { model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts + 1).coerceAtMost(10)) } }
            )
        }
    }
}

@Composable
private fun SettingsOptionRow(
    model: WukkiModel,
    titleKey: String,
    descriptionKey: String? = null,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(66.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) WukkiColors.surfaceSelected else WukkiColors.backgroundRaised)
            .border(1.dp, if (selected) SettingsAccent else WukkiColors.border, RoundedCornerShape(6.dp))
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 18.dp)) {
            Text(tr(model.settings.language, titleKey), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            descriptionKey?.let {
                Spacer(Modifier.height(2.dp))
                Text(tr(model.settings.language, it), color = SettingsMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        control()
    }
}

@Composable
private fun <T> PlaybackSelect(value: T, entries: List<T>, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.widthIn(min = 155.dp).clip(RoundedCornerShape(5.dp)).background(WukkiColors.surfaceInput)
                .border(1.dp, WukkiColors.border, RoundedCornerShape(5.dp)).clickable { expanded = true }
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label(value), color = WukkiColors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = SettingsMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(text = { Text(label(entry), color = WukkiColors.textPrimary) }, onClick = { onSelect(entry); expanded = false })
            }
        }
    }
}

@Composable
private fun PlaybackStepper(value: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(WukkiColors.surfaceInput)
            .border(1.dp, WukkiColors.border, RoundedCornerShape(5.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDecrease, modifier = Modifier.size(34.dp)) { Text("−", color = WukkiColors.textPrimary, fontSize = 17.sp) }
        Text(value.toString(), color = WukkiColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onIncrease, modifier = Modifier.size(34.dp)) { Text("+", color = WukkiColors.textPrimary, fontSize = 17.sp) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EpgSettings(model: WukkiModel, scope: CoroutineScope) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    SettingsOptionRow(model, "settings.epg.refresh") {
        RefreshSelector(
            model,
            model.settings.epgRefresh,
            intervals = RefreshInterval.entries.toList(),
            useHourlyLabels = true,
            onSelect = model::setEpgRefresh
        )
    }
    Spacer(Modifier.height(8.dp))
    SettingsOptionRow(model, "settings.epg.sources") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr(model.settings.language, "settings.name")) }, singleLine = true, modifier = Modifier.weight(.3f))
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(tr(model.settings.language, "settings.epg.url")) }, singleLine = true, modifier = Modifier.weight(.55f))
            Button(onClick = { if (url.isNotBlank()) scope.launch { model.addEpgSource(name, url); name = ""; url = "" } }) { Text(tr(model.settings.language, "settings.add")) }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (model.epgSources.isEmpty()) Text(tr(model.settings.language, "settings.no.sources"), color = SettingsMuted)
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        items(model.epgSources, key = { it.id }) { source ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(WukkiColors.backgroundRaised).border(1.dp, WukkiColors.border, RoundedCornerShape(6.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = source.name, onValueChange = { model.renameEpgSource(source.id, it) }, singleLine = true, modifier = Modifier.weight(1f))
                    Switch(checked = source.enabled, onCheckedChange = { model.setEpgSourceEnabled(source.id, it) }, modifier = Modifier.padding(start = 8.dp))
                    TextButton(onClick = { model.moveEpgSource(source.id, -1) }) { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) }
                    TextButton(onClick = { model.moveEpgSource(source.id, 1) }) { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) }
                }
                Text(source.url, color = SettingsMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(source.lastUpdatedAt?.let { "${tr(model.settings.language, "settings.updated")}: ${formatTime(it)}" } ?: tr(model.settings.language, "settings.not.updated"), color = SettingsMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { model.refreshEpgSource(source.id) } }) { Text(tr(model.settings.language, "settings.refresh")) }
                    TextButton(onClick = { model.removeEpgSource(source.id) }) { Text(tr(model.settings.language, "settings.delete"), color = WukkiColors.error) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DisplaySettings(model: WukkiModel, remoteOptionIndex: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingsOptionRow(model, "settings.display.scale", selected = remoteOptionIndex == 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(.9f to tr(model.settings.language, "settings.display.small"), 1f to tr(model.settings.language, "settings.display.normal"), 1.15f to tr(model.settings.language, "settings.display.large")).forEach { (scale, title) ->
                FilterChip(selected = model.settings.display.uiScale == scale, onClick = { model.updateDisplay { it.copy(uiScale = scale) } }, label = { Text(title) })
            }
        }
    }
    SettingsOptionRow(model, "settings.display.channel.list", selected = remoteOptionIndex == 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ChannelListDisplayMode.entries.forEach { mode ->
                FilterChip(
                    selected = (model.settings.display.channelListMode ?: ChannelListDisplayMode.NORMAL) == mode,
                    onClick = { model.updateDisplay { it.copy(channelListMode = mode) } },
                    label = { Text(mode.label(model)) }
                )
            }
        }
    }
    SettingsToggle(model, "settings.display.programme", model.settings.display.showChannelProgramme, remoteOptionIndex == 2) { model.updateDisplay { settings -> settings.copy(showChannelProgramme = it) } }
    SettingsToggle(model, "settings.display.mini.guide", model.settings.display.showMiniGuide, remoteOptionIndex == 3) { model.updateDisplay { settings -> settings.copy(showMiniGuide = it) } }
    SettingsToggle(model, "settings.display.logos", model.settings.display.showLogos, remoteOptionIndex == 4) { model.updateDisplay { settings -> settings.copy(showLogos = it) } }
    SettingsToggle(model, "settings.display.programme.images", model.settings.display.showProgrammeImages != false, remoteOptionIndex == 5) { model.updateDisplay { settings -> settings.copy(showProgrammeImages = it) } }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PlaylistSettings(model: WukkiModel, scope: CoroutineScope) {
    var url by remember { mutableStateOf("") }
    SettingsOptionRow(model, "settings.playlist.refresh") {
        RefreshSelector(
            model,
            model.settings.playlistRefresh,
            intervals = listOf(RefreshInterval.MANUAL, RefreshInterval.SIX_HOURS, RefreshInterval.DAILY),
            onSelect = model::setPlaylistRefresh
        )
    }
    Spacer(Modifier.height(8.dp))
    SettingsOptionRow(model, "settings.playlists") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(tr(model.settings.language, "settings.playlist.url")) }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { if (url.isNotBlank()) scope.launch { model.addPlaylistFromUrl(url); url = "" } }) { Text(tr(model.settings.language, "settings.add")) }
            Button(onClick = { choosePlaylistFile(model.settings.language)?.let { scope.launch { model.addPlaylistFromFile(it) } } }) { Text(tr(model.settings.language, "settings.file")) }
        }
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        items(model.state.playlists, key = { it.id }) { playlist ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(WukkiColors.backgroundRaised).border(1.dp, WukkiColors.border, RoundedCornerShape(6.dp)).padding(10.dp)) {
                OutlinedTextField(value = playlist.name, onValueChange = { model.renamePlaylist(playlist.id, it) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(playlist.location, color = SettingsMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(model.settings.language, "settings.channels.count", model.state.channels.count { it.playlistId == playlist.id }), color = SettingsMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { model.selectedPlaylistId = playlist.id; scope.launch { model.refreshSelected() } }) { Text(tr(model.settings.language, "settings.refresh")) }
                    TextButton(onClick = { model.selectedPlaylistId = playlist.id; model.removeSelectedPlaylist() }) { Text(tr(model.settings.language, "settings.delete"), color = WukkiColors.error) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LanguageSettings(model: WukkiModel, remoteOptionIndex: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingsOptionRow(model, "settings.language.title", selected = remoteOptionIndex == 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = model.settings.language == AppLanguage.HUNGARIAN, onClick = { model.setLanguage(AppLanguage.HUNGARIAN) }, label = { Text(tr(model.settings.language, "language.hungarian")) })
            FilterChip(selected = model.settings.language == AppLanguage.ENGLISH, onClick = { model.setLanguage(AppLanguage.ENGLISH) }, label = { Text(tr(model.settings.language, "language.english")) })
        }
    }
    SettingsOptionRow(model, "settings.language.notice") { }
    }
}

@Composable
private fun ParentalSettings(model: WukkiModel) {
    SettingsOptionRow(model, "settings.parental.coming", "settings.parental.description") { }
}

@Composable
private fun AboutSettings(model: WukkiModel) {
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    LaunchedEffect(Unit) {
        deviceInfo = withContext(Dispatchers.IO) { DeviceInfoProvider.collect() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsOptionRow(model, "settings.about", "settings.about.licenses") { Text("Wukki TV", color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.version") { Text(WukkiBuildInfo.VERSION, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.build") { Text(WukkiBuildInfo.BUILD, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.engine") { Text("VLC / libVLC", color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.platform") { Text(deviceInfo?.platform ?: tr(model.settings.language, "settings.about.loading"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.os") { Text(deviceInfo?.osVersion ?: tr(model.settings.language, "settings.about.loading"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.device.id") { Text(deviceInfo?.installationId ?: tr(model.settings.language, "settings.about.loading"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.storage") {
            Text(
                deviceInfo?.let { info -> tr(model.settings.language, "settings.about.storage.value", formatByteSize(info.appDataBytes), formatByteSize(info.availableStorageBytes)) }
                    ?: tr(model.settings.language, "settings.about.loading"),
                color = WukkiColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        SettingsOptionRow(model, "settings.about.privacy", onSelect = { legalDocument = LegalDocument.PRIVACY }) {
            Text(tr(model.settings.language, "action.open"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold)
        }
        SettingsOptionRow(model, "settings.about.licenses.title", onSelect = { legalDocument = LegalDocument.LICENSES }) {
            Text(tr(model.settings.language, "action.open"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
    legalDocument?.let { document ->
        LegalDocumentDialog(model, document) { legalDocument = null }
    }
}

private enum class LegalDocument(val resourceStem: String, val titleKey: String) {
    PRIVACY("privacy", "settings.about.privacy"),
    LICENSES("vlc_notice", "settings.about.licenses.title")
}

@Composable
private fun LegalDocumentDialog(model: WukkiModel, document: LegalDocument, onDismiss: () -> Unit) {
    val language = model.settings.language
    val text = remember(document, language) {
        val languageSuffix = if (language == AppLanguage.HUNGARIAN) "hu" else "en"
        LegalDocument::class.java.classLoader
            .getResourceAsStream("legal/${document.resourceStem}_$languageSuffix.txt")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: tr(language, "settings.about.document.unavailable")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WukkiColors.surfaceOverlay,
        titleContentColor = WukkiColors.textPrimary,
        textContentColor = WukkiColors.textSecondary,
        title = { Text(tr(language, document.titleKey), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = text,
                modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                color = WukkiColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = WukkiColors.primary, contentColor = WukkiColors.textPrimary)
            ) { Text(tr(language, "action.close")) }
        }
    )
}

@Composable
private fun SettingsToggle(model: WukkiModel, titleKey: String, checked: Boolean, selected: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    SettingsOptionRow(model, titleKey, selected = selected) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WukkiColors.textPrimary,
                checkedTrackColor = SettingsAccent,
                uncheckedThumbColor = SettingsMuted,
                uncheckedTrackColor = WukkiColors.border
            )
        )
    }
}

@Composable
private fun RefreshSelector(
    model: WukkiModel,
    selected: RefreshInterval,
    intervals: List<RefreshInterval>,
    useHourlyLabels: Boolean = false,
    onSelect: (RefreshInterval) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        intervals.forEach { interval ->
            FilterChip(
                selected = interval == selected,
                onClick = { onSelect(interval) },
                label = { Text(interval.label(model, useHourlyLabels)) }
            )
        }
    }
}

@Composable
private fun SettingsCard(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, WukkiColors.border), colors = CardDefaults.cardColors(containerColor = SettingsPanel)) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), content = content)
    }
}

private fun choosePlaylistFile(language: AppLanguage): File? {
    val dialog = FileDialog(null as java.awt.Frame?, tr(language, "file.playlist.title"), FileDialog.LOAD)
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}

private fun SettingsSection.title(model: WukkiModel): String = tr(model.settings.language, when (this) {
    SettingsSection.PLAYBACK -> "settings.playback"
    SettingsSection.EPG -> "settings.epg"
    SettingsSection.DISPLAY -> "settings.display"
    SettingsSection.PARENTAL -> "settings.parental"
    SettingsSection.PLAYLISTS -> "settings.playlists"
    SettingsSection.LANGUAGE -> "settings.language"
    SettingsSection.ABOUT -> "settings.about"
})
private fun RefreshInterval.label(model: WukkiModel, useHourlyLabels: Boolean = false): String = tr(model.settings.language, when (this) {
    RefreshInterval.MANUAL -> "refresh.manual"
    RefreshInterval.SIX_HOURS -> "refresh.six.hours"
    RefreshInterval.TWELVE_HOURS -> "refresh.twelve.hours"
    RefreshInterval.DAILY -> if (useHourlyLabels) "refresh.twentyfour.hours" else "refresh.daily"
})
private fun BufferProfile.label(model: WukkiModel): String = tr(model.settings.language, when (this) {
    BufferProfile.LOW_LATENCY -> "buffer.small"; BufferProfile.BALANCED -> "buffer.medium"; BufferProfile.STABLE -> "buffer.large"
})
private fun AspectRatioMode.label(model: WukkiModel): String = when (this) { AspectRatioMode.AUTO -> tr(model.settings.language, "aspect.auto"); AspectRatioMode.RATIO_16_9 -> "16:9"; AspectRatioMode.RATIO_4_3 -> "4:3"; AspectRatioMode.RATIO_21_9 -> "21:9"; AspectRatioMode.FILL_CROP -> tr(model.settings.language, "aspect.fill") }
private fun ChannelListDisplayMode.label(model: WukkiModel): String = tr(model.settings.language, when (this) {
    ChannelListDisplayMode.COMPACT -> "settings.display.channel.list.compact"
    ChannelListDisplayMode.NORMAL -> "settings.display.channel.list.normal"
    ChannelListDisplayMode.DETAILED -> "settings.display.channel.list.detailed"
})

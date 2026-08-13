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
import kotlinx.coroutines.launch
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
private const val WUKKI_VERSION = "1.0.0"
private enum class PlaybackOption { VOLUME, BUFFER, ASPECT_RATIO, RECONNECT, RETRIES }

@Composable
fun SettingsScreen(
    model: WukkiModel,
    scope: CoroutineScope,
    selectedSection: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
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
    scale: Float,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(12.dp * scale)
    Column(
        modifier = modifier.clip(shape).background(SettingsSurface).border(1.dp, WukkiColors.border, shape)
    ) {
        SettingsSection.entries.forEach { item ->
            val active = item == selected
            Row(
                modifier = Modifier.fillMaxWidth().height(81.dp * scale)
                    .background(if (active) WukkiColors.surfaceSelected else WukkiColors.transparent)
                    .border(0.5.dp, WukkiColors.border.copy(alpha = .72f))
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
                    SettingsSection.PLAYBACK -> PlaybackSettings(model)
                    SettingsSection.DISPLAY -> DisplaySettings(model)
                    SettingsSection.PARENTAL -> ParentalSettings(model)
                    SettingsSection.LANGUAGE -> LanguageSettings(model)
                    SettingsSection.ABOUT -> AboutSettings(model)
                    SettingsSection.EPG, SettingsSection.PLAYLISTS -> Unit
                }
            }
        }
    }
}

@Composable
private fun PlaybackSettings(model: WukkiModel) {
    val settings = model.settings.playback
    var focusedOption by remember { mutableStateOf(PlaybackOption.VOLUME) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsOptionRow(model, "settings.playback.volume", "settings.playback.volume.description", focusedOption == PlaybackOption.VOLUME, { focusedOption = PlaybackOption.VOLUME }) {
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
        SettingsOptionRow(model, "settings.playback.buffer", "settings.playback.buffer.description", focusedOption == PlaybackOption.BUFFER, { focusedOption = PlaybackOption.BUFFER }) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            PlaybackSelect(settings.bufferProfile, BufferProfile.entries.toList(), { it.label(model) }) { profile -> model.updatePlayback { it.copy(bufferProfile = profile) } }
            }
        }
        SettingsOptionRow(model, "settings.playback.aspect", "settings.playback.aspect.description", focusedOption == PlaybackOption.ASPECT_RATIO, { focusedOption = PlaybackOption.ASPECT_RATIO }) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            PlaybackSelect(settings.aspectRatio ?: AspectRatioMode.AUTO, AspectRatioMode.entries.toList(), { it.label(model) }) { ratio -> model.updatePlayback { it.copy(aspectRatio = ratio) } }
            }
        }
        SettingsOptionRow(model, "settings.playback.reconnect", "settings.playback.reconnect.description", focusedOption == PlaybackOption.RECONNECT, { focusedOption = PlaybackOption.RECONNECT }) {
            Switch(
                checked = settings.autoReconnect,
                onCheckedChange = { enabled -> model.updatePlayback { it.copy(autoReconnect = enabled) } },
                colors = SwitchDefaults.colors(checkedThumbColor = WukkiColors.textPrimary, checkedTrackColor = SettingsAccent, uncheckedThumbColor = SettingsMuted, uncheckedTrackColor = WukkiColors.border)
            )
        }
        SettingsOptionRow(model, "settings.playback.attempts", "settings.playback.attempts.description", focusedOption == PlaybackOption.RETRIES, { focusedOption = PlaybackOption.RETRIES }) {
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
        RefreshSelector(model, model.settings.epgRefresh, onSelect = model::setEpgRefresh)
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
private fun DisplaySettings(model: WukkiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingsOptionRow(model, "settings.display.scale") {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(.9f to tr(model.settings.language, "settings.display.small"), 1f to tr(model.settings.language, "settings.display.normal"), 1.15f to tr(model.settings.language, "settings.display.large")).forEach { (scale, title) ->
                FilterChip(selected = model.settings.display.uiScale == scale, onClick = { model.updateDisplay { it.copy(uiScale = scale) } }, label = { Text(title) })
            }
        }
    }
    SettingsToggle(model, "settings.display.programme", model.settings.display.showChannelProgramme) { model.updateDisplay { settings -> settings.copy(showChannelProgramme = it) } }
    SettingsToggle(model, "settings.display.mini.guide", model.settings.display.showMiniGuide) { model.updateDisplay { settings -> settings.copy(showMiniGuide = it) } }
    SettingsToggle(model, "settings.display.logos", model.settings.display.showLogos) { model.updateDisplay { settings -> settings.copy(showLogos = it) } }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PlaylistSettings(model: WukkiModel, scope: CoroutineScope) {
    var url by remember { mutableStateOf("") }
    SettingsOptionRow(model, "settings.playlist.refresh") { RefreshSelector(model, model.settings.playlistRefresh, onSelect = model::setPlaylistRefresh) }
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
private fun LanguageSettings(model: WukkiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingsOptionRow(model, "settings.language.title") {
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsOptionRow(model, "settings.about", "settings.about.licenses") { Text("Wukki TV", color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.version") { Text(WUKKI_VERSION, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.engine") { Text("VLC / libVLC", color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun SettingsRow(model: WukkiModel, titleKey: String, content: @Composable () -> Unit) =
    SettingsOptionRow(model = model, titleKey = titleKey, control = content)

@Composable
private fun SettingsToggle(model: WukkiModel, titleKey: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsOptionRow(model, titleKey) {
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
private fun RefreshSelector(model: WukkiModel, selected: RefreshInterval, onSelect: (RefreshInterval) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        RefreshInterval.entries.forEach { interval -> FilterChip(selected = interval == selected, onClick = { onSelect(interval) }, label = { Text(interval.label(model)) }) }
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
private fun RefreshInterval.label(model: WukkiModel): String = tr(model.settings.language, when (this) {
    RefreshInterval.MANUAL -> "refresh.manual"; RefreshInterval.SIX_HOURS -> "refresh.six.hours"; RefreshInterval.DAILY -> "refresh.daily"
})
private fun BufferProfile.label(model: WukkiModel): String = tr(model.settings.language, when (this) {
    BufferProfile.LOW_LATENCY -> "buffer.low.latency"; BufferProfile.BALANCED -> "buffer.balanced"; BufferProfile.STABLE -> "buffer.stable"
})
private fun AspectRatioMode.label(model: WukkiModel): String = when (this) { AspectRatioMode.AUTO -> tr(model.settings.language, "aspect.auto"); AspectRatioMode.RATIO_16_9 -> "16:9"; AspectRatioMode.RATIO_4_3 -> "4:3"; AspectRatioMode.RATIO_21_9 -> "21:9"; AspectRatioMode.FILL_CROP -> tr(model.settings.language, "aspect.fill") }

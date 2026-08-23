package hu.wukki.tv.ui.settings

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.formatTime
import hu.wukki.tv.ui.components.Localizer
import hu.wukki.tv.ui.components.tr
import hu.wukki.tv.ui.components.WukkiColors

import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsSection { PLAYBACK, EPG, DISPLAY, PARENTAL, PLAYLISTS, LANGUAGE, ABOUT }
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
    androidFullScreenSubmenus: Boolean = false,
    onCategoryFocus: (Int) -> Unit = {},
    onOptionFocus: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    playbackEngineLabel: String = "VLC / libVLC"
) {
    BoxWithConstraints(modifier) {
        val scale = minOf(
            maxWidth.value / SETTINGS_REFERENCE_WIDTH,
            maxHeight.value / SETTINGS_REFERENCE_HEIGHT
        ).coerceIn(.70f, 1f)
        if (androidFullScreenSubmenus) {
            AndroidSettingsLayout(
                model = model,
                scope = scope,
                selectedSection = selectedSection,
                onSectionChange = onSectionChange,
                remoteCategoryIndex = remoteCategoryIndex,
                remoteNavigationActive = remoteNavigationActive,
                remoteOptionIndex = remoteOptionIndex,
                onCategoryFocus = onCategoryFocus,
                onOptionFocus = onOptionFocus,
                scale = scale,
                playbackEngineLabel = playbackEngineLabel
            )
        } else Column(Modifier.fillMaxSize()) {
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
                    onCategoryFocus = onCategoryFocus,
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
                        onOptionFocus = onOptionFocus,
                        playbackEngineLabel = playbackEngineLabel,
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
    onCategoryFocus: (Int) -> Unit,
    remoteCategoryIndex: Int,
    remoteNavigationActive: Boolean,
    scale: Float,
    modifier: Modifier,
    scrollable: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp * scale)
    val listState = rememberLazyListState()
    LaunchedEffect(scrollable, selected, remoteNavigationActive, remoteCategoryIndex) {
        if (scrollable && selected == null && remoteNavigationActive) {
            listState.animateScrollToItem(remoteCategoryIndex.coerceIn(0, SettingsSection.entries.lastIndex))
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.clip(shape).background(SettingsSurface).border(1.dp, WukkiColors.border, shape)
    ) {
        itemsIndexed(SettingsSection.entries) { index, item ->
            val active = item == selected
            val focused = remoteNavigationActive && selected == null && index == remoteCategoryIndex
            SettingsListRow(
                title = item.title(model),
                selected = active,
                focused = focused,
                onClick = { onCategoryFocus(index); onSelect(item) },
                scale = scale,
                titleFontSize = 19.sp,
                titleWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            ) {
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
private fun AndroidSettingsLayout(
    model: WukkiModel,
    scope: CoroutineScope,
    selectedSection: SettingsSection?,
    onSectionChange: (SettingsSection?) -> Unit,
    remoteCategoryIndex: Int,
    remoteNavigationActive: Boolean,
    remoteOptionIndex: Int,
    onCategoryFocus: (Int) -> Unit,
    onOptionFocus: (Int) -> Unit,
    scale: Float,
    playbackEngineLabel: String
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            tr(model.settings.language, "settings.title"),
            color = WukkiColors.textPrimary,
            fontWeight = FontWeight.Black,
            fontSize = (26f * scale).sp,
            modifier = Modifier.padding(start = 8.dp * scale, top = 8.dp * scale, bottom = 18.dp * scale)
        )
        if (selectedSection == null) {
            SettingsNavigation(
                model = model,
                selected = null,
                onSelect = onSectionChange,
                onCategoryFocus = onCategoryFocus,
                remoteCategoryIndex = remoteCategoryIndex,
                remoteNavigationActive = remoteNavigationActive,
                scale = scale,
                modifier = Modifier.fillMaxWidth().weight(1f),
                scrollable = true
            )
        } else {
            val sectionTitle = selectedSection.title(model)
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(WukkiColors.backgroundRaised).clickable { onSectionChange(null) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = tr(model.settings.language, "action.back"),
                    tint = WukkiColors.textPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    sectionTitle,
                    color = WukkiColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (18f * scale).sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            SettingsDetail(
                model = model,
                scope = scope,
                selectedSection = selectedSection,
                scale = scale,
                remoteOptionIndex = remoteOptionIndex,
                onOptionFocus = onOptionFocus,
                playbackEngineLabel = playbackEngineLabel,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
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
    onOptionFocus: (Int) -> Unit,
    playbackEngineLabel: String,
    modifier: Modifier
) {
    SettingsCard(modifier) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
                when (selectedSection) {
                    SettingsSection.PLAYBACK -> PlaybackSettings(model, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.EPG -> EpgSettings(model, scope, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.DISPLAY -> DisplaySettings(model, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.PARENTAL -> ParentalSettings(model, scale)
                    SettingsSection.PLAYLISTS -> PlaylistSettings(model, scope, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.LANGUAGE -> LanguageSettings(model, remoteOptionIndex, onOptionFocus, scale)
                    SettingsSection.ABOUT -> AboutSettings(model, playbackEngineLabel, scale)
                }
        }
    }
}

@Composable
private fun PlaybackSettings(model: WukkiModel, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    val settings = model.settings.playback
    val focusedOption = PlaybackOption.entries.getOrElse(remoteOptionIndex) { PlaybackOption.AUTOPLAY }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsOptionRow(model, "settings.playback.autoplay", "settings.playback.autoplay.description", focusedOption == PlaybackOption.AUTOPLAY, onFocus = { onOptionFocus(0) }, scale = scale) {
            Switch(
                checked = settings.autoPlayOnLaunch != false,
                onCheckedChange = { enabled -> onOptionFocus(0); model.updatePlayback { it.copy(autoPlayOnLaunch = enabled) } },
                colors = SwitchDefaults.colors(checkedThumbColor = WukkiColors.textPrimary, checkedTrackColor = SettingsAccent, uncheckedThumbColor = SettingsMuted, uncheckedTrackColor = WukkiColors.border)
            )
        }
        SettingsOptionRow(model, "settings.playback.volume", "settings.playback.volume.description", focusedOption == PlaybackOption.VOLUME, onFocus = { onOptionFocus(1) }, scale = scale) {
            Row(
                modifier = Modifier.widthIn(min = 150.dp, max = 205.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${settings.volume}%", color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Slider(
                    value = settings.volume.toFloat(),
                    onValueChange = { volume -> onOptionFocus(1); model.updatePlayback { it.copy(volume = volume.toInt()) } },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        SettingsOptionRow(model, "settings.playback.buffer", "settings.playback.buffer.description", focusedOption == PlaybackOption.BUFFER, onFocus = { onOptionFocus(2) }, scale = scale) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            PlaybackSelect(settings.bufferProfile, BufferProfile.entries.toList(), { it.label(model) }, { onOptionFocus(2) }) { profile -> model.updatePlayback { it.copy(bufferProfile = profile) } }
            }
        }
        SettingsOptionRow(model, "settings.playback.aspect", "settings.playback.aspect.description", focusedOption == PlaybackOption.ASPECT_RATIO, onFocus = { onOptionFocus(3) }, scale = scale) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            PlaybackSelect(settings.aspectRatio ?: AspectRatioMode.AUTO, AspectRatioMode.entries.toList(), { it.label(model) }, { onOptionFocus(3) }) { ratio -> model.updatePlayback { it.copy(aspectRatio = ratio) } }
            }
        }
        SettingsOptionRow(model, "settings.playback.reconnect", "settings.playback.reconnect.description", focusedOption == PlaybackOption.RECONNECT, onFocus = { onOptionFocus(4) }, scale = scale) {
            Switch(
                checked = settings.autoReconnect,
                onCheckedChange = { enabled -> onOptionFocus(4); model.updatePlayback { it.copy(autoReconnect = enabled) } },
                colors = SwitchDefaults.colors(checkedThumbColor = WukkiColors.textPrimary, checkedTrackColor = SettingsAccent, uncheckedThumbColor = SettingsMuted, uncheckedTrackColor = WukkiColors.border)
            )
        }
        SettingsOptionRow(model, "settings.playback.attempts", "settings.playback.attempts.description", focusedOption == PlaybackOption.RETRIES, onFocus = { onOptionFocus(5) }, scale = scale) {
            PlaybackStepper(
                value = settings.reconnectAttempts,
                onDecrease = { onOptionFocus(5); model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts - 1).coerceAtLeast(1)) } },
                onIncrease = { onOptionFocus(5); model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts + 1).coerceAtMost(10)) } }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SettingsOptionRow(
    model: WukkiModel,
    titleKey: String,
    descriptionKey: String? = null,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    scale: Float = 1f,
    control: @Composable () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) bringIntoViewRequester.bringIntoView()
    }
    SettingsListRow(
        title = tr(model.settings.language, titleKey),
        description = descriptionKey?.let { tr(model.settings.language, it) },
        selected = selected,
        focused = selected,
        onClick = if (onFocus != null || onSelect != null) ({ onFocus?.invoke(); onSelect?.invoke() }) else null,
        scale = scale,
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
    ) { control() }
}

@Composable
private fun <T> PlaybackSelect(value: T, entries: List<T>, label: @Composable (T) -> String, onFocus: () -> Unit, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.widthIn(min = 155.dp).clip(RoundedCornerShape(5.dp)).background(WukkiColors.surfaceInput)
                .border(1.dp, WukkiColors.border, RoundedCornerShape(5.dp)).clickable { onFocus(); expanded = true }
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label(value), color = WukkiColors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = SettingsMuted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(text = { Text(label(entry), color = WukkiColors.textPrimary) }, onClick = { onFocus(); onSelect(entry); expanded = false })
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
private fun EpgSettings(model: WukkiModel, scope: CoroutineScope, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingsOptionRow(model, "settings.epg.refresh", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
        RefreshSelector(
            model,
            model.settings.epgRefresh,
            intervals = RefreshInterval.entries.toList(),
            useHourlyLabels = true,
            onFocus = { onOptionFocus(0) },
            onSelect = model::setEpgRefresh
        )
    }
    FixedSourceCard(
        model = model,
        titleKey = "settings.epg.source",
        sourceName = model.officialEpgSource?.name ?: tr(model.settings.language, "settings.no.sources"),
        location = model.officialEpgSource?.url,
        updatedAt = model.officialEpgSource?.lastUpdatedAt,
        selected = remoteOptionIndex == 1,
        onFocus = { onOptionFocus(1) },
        onRefresh = { scope.launch { model.refreshOfficialEpg() } },
        scale = scale
    )
    }
}
@Composable
private fun DisplaySettings(model: WukkiModel, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingsOptionRow(model, "settings.display.scale", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(.9f to tr(model.settings.language, "settings.display.small"), 1f to tr(model.settings.language, "settings.display.normal"), 1.15f to tr(model.settings.language, "settings.display.large")).forEach { (scale, title) ->
                FilterChip(selected = model.settings.display.uiScale == scale, onClick = { onOptionFocus(0); model.updateDisplay { it.copy(uiScale = scale) } }, label = { Text(title) })
            }
        }
    }
    SettingsOptionRow(model, "settings.display.channel.list", selected = remoteOptionIndex == 1, onFocus = { onOptionFocus(1) }, scale = scale) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ChannelListDisplayMode.entries.forEach { mode ->
                FilterChip(
                    selected = (model.settings.display.channelListMode ?: ChannelListDisplayMode.NORMAL) == mode,
                    onClick = { onOptionFocus(1); model.updateDisplay { it.copy(channelListMode = mode) } },
                    label = { Text(mode.label(model)) }
                )
            }
        }
    }
    SettingsToggle(model, "settings.display.programme", model.settings.display.showChannelProgramme, remoteOptionIndex == 2, { onOptionFocus(2) }, scale) { model.updateDisplay { settings -> settings.copy(showChannelProgramme = it) } }
    SettingsToggle(model, "settings.display.mini.guide", model.settings.display.showMiniGuide, remoteOptionIndex == 3, { onOptionFocus(3) }, scale) { model.updateDisplay { settings -> settings.copy(showMiniGuide = it) } }
    SettingsToggle(model, "settings.display.logos", model.settings.display.showLogos, remoteOptionIndex == 4, { onOptionFocus(4) }, scale) { model.updateDisplay { settings -> settings.copy(showLogos = it) } }
    SettingsToggle(model, "settings.display.programme.images", model.settings.display.showProgrammeImages != false, remoteOptionIndex == 5, { onOptionFocus(5) }, scale) { model.updateDisplay { settings -> settings.copy(showProgrammeImages = it) } }
    }
}

@Composable
private fun PlaylistSettings(model: WukkiModel, scope: CoroutineScope, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingsOptionRow(model, "settings.playlist.refresh", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
        RefreshSelector(
            model,
            model.settings.playlistRefresh,
            intervals = listOf(RefreshInterval.MANUAL, RefreshInterval.SIX_HOURS, RefreshInterval.DAILY),
            onFocus = { onOptionFocus(0) },
            onSelect = model::setPlaylistRefresh
        )
    }
    FixedSourceCard(
        model = model,
        titleKey = "settings.playlist.source",
        sourceName = model.officialPlaylist.name,
        location = model.officialPlaylist.location,
        updatedAt = model.officialPlaylist.updatedAt,
        footer = tr(model.settings.language, "settings.channels.count", model.state.channels.size),
        selected = remoteOptionIndex == 1,
        onFocus = { onOptionFocus(1) },
        onRefresh = { scope.launch { model.refreshOfficialPlaylist() } },
        scale = scale
    )
    }
}

@Composable
private fun FixedSourceCard(
    model: WukkiModel,
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
    val details = footer ?: updatedAt?.let { "${tr(model.settings.language, "settings.updated")}: ${formatTime(it)}" }
        ?: tr(model.settings.language, "settings.not.updated")
    SettingsListRow(
        title = tr(model.settings.language, titleKey),
        description = details,
        selected = selected,
        focused = selected,
        onClick = onFocus,
        scale = scale
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(sourceName, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            location?.let { Text(it, color = SettingsMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            TextButton(onClick = { onFocus(); onRefresh() }) { Text(tr(model.settings.language, "settings.refresh")) }
        }
    }
}
@Composable
private fun LanguageSettings(model: WukkiModel, remoteOptionIndex: Int, onOptionFocus: (Int) -> Unit, scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SettingsOptionRow(model, "settings.language.title", selected = remoteOptionIndex == 0, onFocus = { onOptionFocus(0) }, scale = scale) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = model.settings.language == AppLanguage.HUNGARIAN, onClick = { onOptionFocus(0); model.setLanguage(AppLanguage.HUNGARIAN) }, label = { Text(tr(model.settings.language, "language.hungarian")) })
            FilterChip(selected = model.settings.language == AppLanguage.ENGLISH, onClick = { onOptionFocus(0); model.setLanguage(AppLanguage.ENGLISH) }, label = { Text(tr(model.settings.language, "language.english")) })
        }
    }
    SettingsOptionRow(model, "settings.language.notice", scale = scale) { }
    }
}

@Composable
private fun ParentalSettings(model: WukkiModel, scale: Float) {
    SettingsOptionRow(model, "settings.parental.coming", "settings.parental.description", scale = scale) { }
}

@Composable
private fun AboutSettings(model: WukkiModel, playbackEngineLabel: String, scale: Float) {
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
    LaunchedEffect(Unit) {
        deviceInfo = withContext(Dispatchers.Default) { DeviceInfoProvider.collect() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsOptionRow(model, "settings.about", "settings.about.licenses", scale = scale) { Text("Wukki TV", color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.version", scale = scale) { Text(WukkiBuildInfo.VERSION, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.build", scale = scale) { Text(WukkiBuildInfo.BUILD, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.engine", scale = scale) { Text(playbackEngineLabel, color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.platform", scale = scale) { Text(deviceInfo?.platform ?: tr(model.settings.language, "settings.about.loading"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.os", scale = scale) { Text(deviceInfo?.osVersion ?: tr(model.settings.language, "settings.about.loading"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.device.id", scale = scale) { Text(deviceInfo?.installationId ?: tr(model.settings.language, "settings.about.loading"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold) }
        SettingsOptionRow(model, "settings.about.storage", scale = scale) {
            Text(
                deviceInfo?.let { info -> tr(model.settings.language, "settings.about.storage.value", formatByteSize(info.appDataBytes), formatByteSize(info.availableStorageBytes)) }
                    ?: tr(model.settings.language, "settings.about.loading"),
                color = WukkiColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        SettingsOptionRow(model, "settings.about.privacy", onSelect = { legalDocument = LegalDocument.PRIVACY }, scale = scale) {
            Text(tr(model.settings.language, "action.open"), color = WukkiColors.textPrimary, fontWeight = FontWeight.SemiBold)
        }
        SettingsOptionRow(model, "settings.about.licenses.title", onSelect = { legalDocument = LegalDocument.LICENSES }, scale = scale) {
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
            Localizer.legalText("legal/${document.resourceStem}_$languageSuffix.txt")
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
private fun SettingsToggle(
    model: WukkiModel,
    titleKey: String,
    checked: Boolean,
    selected: Boolean = false,
    onFocus: () -> Unit,
    scale: Float,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsOptionRow(model, titleKey, selected = selected, onFocus = onFocus, scale = scale) {
        Switch(
            checked = checked,
            onCheckedChange = { value -> onFocus(); onCheckedChange(value) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = WukkiColors.textPrimary,
                checkedTrackColor = SettingsAccent,
                uncheckedThumbColor = SettingsMuted,
                uncheckedTrackColor = WukkiColors.border
            )
        )
    }
}

/** Shared visual shell for category and detail rows in the settings experience. */
@Composable
private fun SettingsListRow(
    title: String,
    description: String? = null,
    selected: Boolean = false,
    focused: Boolean = false,
    onClick: (() -> Unit)? = null,
    scale: Float = 1f,
    titleFontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    titleWeight: FontWeight = FontWeight.SemiBold,
    modifier: Modifier = Modifier,
    control: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp * scale)
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 81.dp * scale)
            .clip(shape)
            .background(if (selected) WukkiColors.surfaceSelected else SettingsSurface)
            .border(if (focused) 2.dp else .5.dp, if (focused) SettingsAccent else WukkiColors.border.copy(alpha = .72f), shape)
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .padding(horizontal = 24.dp * scale, vertical = 10.dp * scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 18.dp * scale)) {
            Text(
                title,
                color = WukkiColors.textPrimary,
                fontSize = titleFontSize * scale,
                fontWeight = titleWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            description?.let {
                Spacer(Modifier.height(2.dp * scale))
                Text(it, color = SettingsMuted, fontSize = 11.sp * scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        control()
    }
}

@Composable
private fun RefreshSelector(
    model: WukkiModel,
    selected: RefreshInterval,
    intervals: List<RefreshInterval>,
    useHourlyLabels: Boolean = false,
    onFocus: () -> Unit,
    onSelect: (RefreshInterval) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        intervals.forEach { interval ->
            FilterChip(
                selected = interval == selected,
                onClick = { onFocus(); onSelect(interval) },
                label = { Text(interval.label(model, useHourlyLabels)) }
            )
        }
    }
}

@Composable
private fun SettingsCard(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, WukkiColors.border), colors = CardDefaults.cardColors(containerColor = SettingsSurface)) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), content = content)
    }
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

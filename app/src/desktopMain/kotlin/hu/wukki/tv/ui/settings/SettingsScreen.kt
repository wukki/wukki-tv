package hu.wukki.tv.ui.settings

import hu.wukki.tv.*
import hu.wukki.tv.ui.components.formatTime

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
private val SettingsPanel = Color(0xF20A1421)
private val SettingsSurface = Color(0xFF111E2F)
private val SettingsMuted = Color(0xFF9BA9BE)
private val SettingsAccent = Color(0xFF8B5CF6)
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
                t(model, "BEÁLLÍTÁSOK", "SETTINGS"),
                color = Color.White,
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
        modifier = modifier.clip(shape).background(SettingsSurface).border(1.dp, Color(0xFF263648), shape)
    ) {
        SettingsSection.entries.forEach { item ->
            val active = item == selected
            Row(
                modifier = Modifier.fillMaxWidth().height(81.dp * scale)
                    .background(if (active) Color(0xFF292141) else Color.Transparent)
                    .border(0.5.dp, Color(0xFF263648).copy(alpha = .72f))
                    .clickable { onSelect(item) }.padding(horizontal = 24.dp * scale),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.title(model),
                    color = Color.White,
                    fontSize = (19f * scale).sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item == SettingsSection.LANGUAGE) {
                    Text(
                        if (model.settings.language == AppLanguage.HUNGARIAN) "Magyar" else "English",
                        color = Color.White,
                        fontSize = (16f * scale).sp,
                        modifier = Modifier.padding(end = 14.dp * scale)
                    )
                }
                Text("›", color = Color(0xFFD0D7E1), fontSize = (34f * scale).sp, fontWeight = FontWeight.Light)
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
            t(model, "Állítsd be az alkalmazást\na saját igényeid szerint.", "Customize the application\nto suit your needs."),
            color = Color(0xFFC1C8D2),
            fontSize = (18f * scale).sp,
            lineHeight = (28f * scale).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsGear(scale: Float) {
    Canvas(Modifier.size(184.dp * scale)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val brush = Brush.linearGradient(
            colors = listOf(Color(0xFF5EA7FF), Color(0xFF7654F5), Color(0xFF9A4DE0)),
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height)
        )
        val inner = size.minDimension * .31f
        val outer = size.minDimension * .45f
        repeat(8) { index ->
            val angle = index * PI.toFloat() / 4f
            drawLine(
                brush = brush,
                start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                end = Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
                strokeWidth = size.minDimension * .16f,
                cap = StrokeCap.Square
            )
        }
        drawCircle(brush = brush, radius = size.minDimension * .34f, center = center)
        drawCircle(color = Color(0xFF07121C), radius = size.minDimension * .17f, center = center)
    }
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
        Text(selectedSection.title(model), color = Color.White, fontWeight = FontWeight.Black, fontSize = (23f * scale).sp)
        Spacer(Modifier.height(16.dp * scale))
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
    ) {
        PlaybackSettingRow(model, "Alkalmazás hangerő", "Application volume", "A Wukki TV lejátszási hangereje", "Wukki TV playback volume", focusedOption == PlaybackOption.VOLUME, { focusedOption = PlaybackOption.VOLUME }) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
                Text("${settings.volume}%", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Slider(value = settings.volume.toFloat(), onValueChange = { volume -> model.updatePlayback { it.copy(volume = volume.toInt()) } }, valueRange = 0f..100f)
            }
        }
        PlaybackSettingRow(model, "Puffer profil", "Buffer profile", "Nagyobb puffer stabilabb lejátszást biztosít", "A larger buffer provides more stable playback", focusedOption == PlaybackOption.BUFFER, { focusedOption = PlaybackOption.BUFFER }) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            PlaybackSelect(settings.bufferProfile, BufferProfile.entries.toList(), { it.label(model) }) { profile -> model.updatePlayback { it.copy(bufferProfile = profile) } }
            }
        }
        PlaybackSettingRow(model, "Képarány", "Aspect ratio", "A kép megjelenítési aránya", "The display ratio of the video", focusedOption == PlaybackOption.ASPECT_RATIO, { focusedOption = PlaybackOption.ASPECT_RATIO }) {
            Column(modifier = Modifier.widthIn(min = 150.dp, max = 205.dp)) {
            PlaybackSelect(settings.aspectRatio ?: AspectRatioMode.AUTO, AspectRatioMode.entries.toList(), { it.label(model) }) { ratio -> model.updatePlayback { it.copy(aspectRatio = ratio) } }
            }
        }
        PlaybackSettingRow(model, "Automatikus újracsatlakozás", "Automatic reconnect", "A stream megszakadásakor újrapróbálkozik", "Retries when the stream is interrupted", focusedOption == PlaybackOption.RECONNECT, { focusedOption = PlaybackOption.RECONNECT }) {
            Switch(
                checked = settings.autoReconnect,
                onCheckedChange = { enabled -> model.updatePlayback { it.copy(autoReconnect = enabled) } },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SettingsAccent, uncheckedThumbColor = SettingsMuted, uncheckedTrackColor = Color(0xFF26364A))
            )
        }
        PlaybackSettingRow(model, "Újracsatlakozási kísérletek száma", "Reconnect attempts", "Hányszor próbálja újraindítani a streamet", "How many times the stream is restarted", focusedOption == PlaybackOption.RETRIES, { focusedOption = PlaybackOption.RETRIES }) {
            PlaybackStepper(
                value = settings.reconnectAttempts,
                onDecrease = { model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts - 1).coerceAtLeast(1)) } },
                onIncrease = { model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts + 1).coerceAtMost(10)) } }
            )
        }
    }
}

@Composable
private fun PlaybackSettingRow(
    model: WukkiModel,
    titleHu: String,
    titleEn: String,
    descriptionHu: String,
    descriptionEn: String,
    selected: Boolean,
    onSelect: () -> Unit,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(66.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF1B1B3B) else Color(0xFF0D1A2A))
            .border(1.dp, if (selected) SettingsAccent else Color(0xFF24364B), RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 18.dp)) {
            Text(t(model, titleHu, titleEn), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(t(model, descriptionHu, descriptionEn), color = SettingsMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        control()
    }
}

@Composable
private fun <T> PlaybackSelect(value: T, entries: List<T>, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.widthIn(min = 155.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF17283A))
                .border(1.dp, Color(0xFF263C53), RoundedCornerShape(5.dp)).clickable { expanded = true }
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label(value), color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("⌄", color = SettingsMuted, fontSize = 15.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(text = { Text(label(entry), color = Color.White) }, onClick = { onSelect(entry); expanded = false })
            }
        }
    }
}

@Composable
private fun PlaybackStepper(value: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF17283A))
            .border(1.dp, Color(0xFF263C53), RoundedCornerShape(5.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDecrease, modifier = Modifier.size(34.dp)) { Text("−", color = Color.White, fontSize = 17.sp) }
        Text(value.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onIncrease, modifier = Modifier.size(34.dp)) { Text("+", color = Color.White, fontSize = 17.sp) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EpgSettings(model: WukkiModel, scope: CoroutineScope) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    SettingsRow(model, "Automatikus EPG frissítés", "Automatic EPG refresh") {
        RefreshSelector(model, model.settings.epgRefresh, onSelect = model::setEpgRefresh)
    }
    Text(t(model, "EPG források", "EPG sources"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t(model, "Név", "Name")) }, singleLine = true, modifier = Modifier.weight(.3f))
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("XMLTV URL") }, singleLine = true, modifier = Modifier.weight(.55f))
        Button(onClick = { if (url.isNotBlank()) scope.launch { model.addEpgSource(name, url); name = ""; url = "" } }) { Text(t(model, "Hozzáadás", "Add")) }
    }
    Spacer(Modifier.height(8.dp))
    if (model.epgSources.isEmpty()) Text(t(model, "Nincs EPG forrás.", "No EPG sources."), color = SettingsMuted)
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        items(model.epgSources, key = { it.id }) { source ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SettingsSurface).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = source.name, onValueChange = { model.renameEpgSource(source.id, it) }, singleLine = true, modifier = Modifier.weight(1f))
                    Switch(checked = source.enabled, onCheckedChange = { model.setEpgSourceEnabled(source.id, it) }, modifier = Modifier.padding(start = 8.dp))
                    TextButton(onClick = { model.moveEpgSource(source.id, -1) }) { Text("↑") }
                    TextButton(onClick = { model.moveEpgSource(source.id, 1) }) { Text("↓") }
                }
                Text(source.url, color = SettingsMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(source.lastUpdatedAt?.let { "${t(model, "Frissítve", "Updated")}: ${formatTime(it)}" } ?: t(model, "Még nincs frissítve", "Not updated yet"), color = SettingsMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { model.refreshEpgSource(source.id) } }) { Text(t(model, "Frissítés", "Refresh")) }
                    TextButton(onClick = { model.removeEpgSource(source.id) }) { Text(t(model, "Törlés", "Delete"), color = Color(0xFFFFA4A1)) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun DisplaySettings(model: WukkiModel) {
    SettingsRow(model, "Felület mérete", "Interface size") {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(.9f to t(model, "Kicsi", "Small"), 1f to t(model, "Normál", "Normal"), 1.15f to t(model, "Nagy", "Large")).forEach { (scale, title) ->
                FilterChip(selected = model.settings.display.uiScale == scale, onClick = { model.updateDisplay { it.copy(uiScale = scale) } }, label = { Text(title) })
            }
        }
    }
    SettingsToggle(model, "Csatornalista műsorinformáció", "Channel-list programme information", model.settings.display.showChannelProgramme) { model.updateDisplay { settings -> settings.copy(showChannelProgramme = it) } }
    SettingsToggle(model, "Mini guide", "Mini guide", model.settings.display.showMiniGuide) { model.updateDisplay { settings -> settings.copy(showMiniGuide = it) } }
    SettingsToggle(model, "Csatornalogók", "Channel logos", model.settings.display.showLogos) { model.updateDisplay { settings -> settings.copy(showLogos = it) } }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PlaylistSettings(model: WukkiModel, scope: CoroutineScope) {
    var url by remember { mutableStateOf("") }
    SettingsRow(model, "Automatikus playlist frissítés", "Automatic playlist refresh") { RefreshSelector(model, model.settings.playlistRefresh, onSelect = model::setPlaylistRefresh) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { if (url.isNotBlank()) scope.launch { model.addPlaylistFromUrl(url); url = "" } }) { Text(t(model, "Hozzáadás", "Add")) }
        Button(onClick = { choosePlaylistFile()?.let { scope.launch { model.addPlaylistFromFile(it) } } }) { Text(t(model, "Fájl", "File")) }
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        items(model.state.playlists, key = { it.id }) { playlist ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SettingsSurface).padding(10.dp)) {
                OutlinedTextField(value = playlist.name, onValueChange = { model.renamePlaylist(playlist.id, it) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(playlist.location, color = SettingsMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${model.state.channels.count { it.playlistId == playlist.id }} ${t(model, "csatorna", "channels")}", color = SettingsMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { model.selectedPlaylistId = playlist.id; scope.launch { model.refreshSelected() } }) { Text(t(model, "Frissítés", "Refresh")) }
                    TextButton(onClick = { model.selectedPlaylistId = playlist.id; model.removeSelectedPlaylist() }) { Text(t(model, "Törlés", "Delete"), color = Color(0xFFFFA4A1)) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LanguageSettings(model: WukkiModel) {
    Text(t(model, "Alkalmazás nyelve", "Application language"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = model.settings.language == AppLanguage.HUNGARIAN, onClick = { model.setLanguage(AppLanguage.HUNGARIAN) }, label = { Text("Magyar") })
        FilterChip(selected = model.settings.language == AppLanguage.ENGLISH, onClick = { model.setLanguage(AppLanguage.ENGLISH) }, label = { Text("English") })
    }
    Spacer(Modifier.height(18.dp))
    SettingsNotice(model, "A beállítások és a dashboard fő navigációja azonnal a választott nyelvre vált.", "Settings and primary dashboard navigation switch immediately to the selected language.")
}

@Composable
private fun ParentalSettings(model: WukkiModel) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(SettingsSurface).border(1.dp, Color(0xFF263648), RoundedCornerShape(10.dp))
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t(model, "Fejlesztés alatt", "Coming soon"), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                t(
                    model,
                    "A PIN-kódos védelem és a korhatár szerinti csatornazárolás egy későbbi verzióban lesz elérhető.",
                    "PIN protection and age-based channel locking will be available in a future version."
                ),
                color = SettingsMuted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AboutSettings(model: WukkiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Wukki TV", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
        SettingsInfoLine(t(model, "Verzió", "Version"), WUKKI_VERSION)
        SettingsInfoLine(t(model, "Lejátszómotor", "Playback engine"), "VLC / libVLC")
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SettingsSurface)
                .border(1.dp, Color(0xFF263648), RoundedCornerShape(10.dp)).padding(16.dp)
        ) {
            Text(
                t(
                    model,
                    "A Wukki TV a VideoLAN VLC/libVLC technológiáját és más nyílt forráskódú komponenseket használ. A harmadik féltől származó licencek és szerzői jogi értesítések az alkalmazáscsomag LICENSES mappájában találhatók.",
                    "Wukki TV uses VideoLAN VLC/libVLC technology and other open-source components. Third-party licences and copyright notices are included in the application's LICENSES directory."
                ),
                color = SettingsMuted,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SettingsInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SettingsSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SettingsMuted, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsRow(model: WukkiModel, hungarian: String, english: String, value: String? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(model, hungarian, english), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            value?.let { Text(it, color = SettingsMuted) }
        }
        Spacer(Modifier.height(8.dp)); content()
    }
}

@Composable
private fun SettingsToggle(model: WukkiModel, hungarian: String, english: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t(model, hungarian, english), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RefreshSelector(model: WukkiModel, selected: RefreshInterval, onSelect: (RefreshInterval) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        RefreshInterval.entries.forEach { interval -> FilterChip(selected = interval == selected, onClick = { onSelect(interval) }, label = { Text(interval.label(model)) }) }
    }
}

@Composable
private fun SettingsNotice(model: WukkiModel, hungarian: String, english: String) {
    Text(t(model, hungarian, english), color = SettingsMuted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Color(0xFF12263A)).padding(10.dp))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsCard(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF24344A)), colors = CardDefaults.cardColors(containerColor = SettingsPanel)) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), content = content)
    }
}

private fun choosePlaylistFile(): File? {
    val dialog = FileDialog(null as java.awt.Frame?, "M3U playlist", FileDialog.LOAD)
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}

@Composable private fun t(model: WukkiModel, hungarian: String, english: String): String = if (model.settings.language == AppLanguage.HUNGARIAN) hungarian else english
@Composable private fun SettingsSection.title(model: WukkiModel): String = when (this) {
    SettingsSection.PLAYBACK -> t(model, "Lejátszási beállítások", "Playback settings")
    SettingsSection.EPG -> t(model, "EPG beállítások", "EPG settings")
    SettingsSection.DISPLAY -> t(model, "Megjelenítés", "Appearance")
    SettingsSection.PARENTAL -> t(model, "Szülői felügyelet", "Parental controls")
    SettingsSection.PLAYLISTS -> t(model, "Playlist kezelése", "Playlist management")
    SettingsSection.LANGUAGE -> t(model, "Nyelv", "Language")
    SettingsSection.ABOUT -> t(model, "Névjegy", "About")
}
@Composable private fun RefreshInterval.label(model: WukkiModel): String = when (this) { RefreshInterval.MANUAL -> t(model, "Kézi", "Manual"); RefreshInterval.SIX_HOURS -> t(model, "6 óra", "6 hours"); RefreshInterval.DAILY -> t(model, "Napi", "Daily") }
@Composable private fun BufferProfile.label(model: WukkiModel): String = when (this) { BufferProfile.LOW_LATENCY -> t(model, "Alacsony késés", "Low latency"); BufferProfile.BALANCED -> t(model, "Kiegyensúlyozott", "Balanced"); BufferProfile.STABLE -> t(model, "Stabil", "Stable") }
@Composable private fun AspectRatioMode.label(model: WukkiModel): String = when (this) { AspectRatioMode.AUTO -> t(model, "Automatikus", "Automatic"); AspectRatioMode.RATIO_16_9 -> "16:9"; AspectRatioMode.RATIO_4_3 -> "4:3"; AspectRatioMode.RATIO_21_9 -> "21:9"; AspectRatioMode.FILL_CROP -> t(model, "Kitöltés", "Fill") }

package hu.wukki.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.io.File

enum class SettingsSection { PLAYBACK, EPG, DISPLAY, PLAYLISTS, LANGUAGE }
private val SettingsPanel = Color(0xF20A1421)
private val SettingsSurface = Color(0xFF111E2F)
private val SettingsMuted = Color(0xFF9BA9BE)
private val SettingsAccent = Color(0xFF8B5CF6)

@Composable
fun SettingsScreen(model: WukkiModel, scope: CoroutineScope, initialSection: SettingsSection, onBack: () -> Unit) {
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF050C15)).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsNavigation(model, section, onSelect = { section = it }, onBack = onBack, modifier = Modifier.width(250.dp).fillMaxHeight())
        SettingsCard(Modifier.weight(1f).fillMaxHeight()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(t(model, "BEÁLLÍTÁSOK", "SETTINGS"), fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Text(section.title(model), color = SettingsMuted, fontSize = 13.sp)
                }
                TextButton(onClick = onBack) { Text("← ${t(model, "Vissza", "Back")}") }
            }
            Spacer(Modifier.height(12.dp))
            when (section) {
                SettingsSection.PLAYBACK -> PlaybackSettings(model)
                SettingsSection.EPG -> EpgSettings(model, scope)
                SettingsSection.DISPLAY -> DisplaySettings(model)
                SettingsSection.PLAYLISTS -> PlaylistSettings(model, scope)
                SettingsSection.LANGUAGE -> LanguageSettings(model)
            }
        }
    }
}

@Composable
private fun SettingsNavigation(model: WukkiModel, selected: SettingsSection, onSelect: (SettingsSection) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    SettingsCard(modifier) {
        Text("WUKKI TV", fontSize = 22.sp, fontWeight = FontWeight.Black, color = WukkiBlue)
        Spacer(Modifier.height(24.dp))
        SettingsSection.entries.forEach { item ->
            val active = item == selected
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(if (active) SettingsAccent.copy(alpha = .35f) else Color.Transparent)
                    .clickable { onSelect(item) }.padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.icon(), modifier = Modifier.width(28.dp), color = if (active) Color.White else SettingsMuted)
                Text(item.title(model), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← ${t(model, "Dashboard", "Dashboard")}") }
    }
}

@Composable
private fun PlaybackSettings(model: WukkiModel) {
    SettingsNotice(model, "Ezek az értékek mentődnek. A beágyazott natív lejátszó bevezetésekor lesznek aktívak.", "These values are saved. They will become active with the embedded native player.")
    SettingsRow(model, "Alkalmazás hangerő", "Application volume", "${model.settings.playback.volume}%") {
        Slider(value = model.settings.playback.volume.toFloat(), onValueChange = { model.updatePlayback { settings -> settings.copy(volume = it.toInt()) } }, valueRange = 0f..100f)
    }
    SettingsRow(model, "Buffer profil", "Buffer profile") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BufferProfile.entries.forEach { profile ->
                FilterChip(selected = model.settings.playback.bufferProfile == profile, onClick = { model.updatePlayback { it.copy(bufferProfile = profile) } }, label = { Text(profile.label(model), fontSize = 12.sp) })
            }
        }
    }
    SettingsToggle(model, "Automatikus újracsatlakozás", "Automatic reconnect", model.settings.playback.autoReconnect) { enabled -> model.updatePlayback { it.copy(autoReconnect = enabled) } }
    SettingsRow(model, "Újracsatlakozási próbálkozások", "Reconnect attempts", model.settings.playback.reconnectAttempts.toString()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts - 1).coerceAtLeast(1)) } }) { Text("−") }
            Text(model.settings.playback.reconnectAttempts.toString(), fontSize = 18.sp)
            TextButton(onClick = { model.updatePlayback { it.copy(reconnectAttempts = (it.reconnectAttempts + 1).coerceAtMost(10)) } }) { Text("+") }
        }
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
    SettingsSection.PLAYBACK -> t(model, "Lejátszás", "Playback")
    SettingsSection.EPG -> "EPG"
    SettingsSection.DISPLAY -> t(model, "Megjelenítés", "Appearance")
    SettingsSection.PLAYLISTS -> t(model, "Playlist kezelés", "Playlists")
    SettingsSection.LANGUAGE -> t(model, "Nyelv", "Language")
}
private fun SettingsSection.icon(): String = when (this) { SettingsSection.PLAYBACK -> "▶"; SettingsSection.EPG -> "▦"; SettingsSection.DISPLAY -> "◐"; SettingsSection.PLAYLISTS -> "☷"; SettingsSection.LANGUAGE -> "A" }
@Composable private fun RefreshInterval.label(model: WukkiModel): String = when (this) { RefreshInterval.MANUAL -> t(model, "Kézi", "Manual"); RefreshInterval.SIX_HOURS -> t(model, "6 óra", "6 hours"); RefreshInterval.DAILY -> t(model, "Napi", "Daily") }
@Composable private fun BufferProfile.label(model: WukkiModel): String = when (this) { BufferProfile.LOW_LATENCY -> t(model, "Alacsony késés", "Low latency"); BufferProfile.BALANCED -> t(model, "Kiegyensúlyozott", "Balanced"); BufferProfile.STABLE -> t(model, "Stabil", "Stable") }

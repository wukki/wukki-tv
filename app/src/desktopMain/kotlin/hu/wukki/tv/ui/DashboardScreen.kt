package hu.wukki.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DashboardPanel = Color(0xED07101B)
private val DashboardMuted = Color(0xFF93A0B5)
private val DashboardBorder = Color(0xFF223047)
private val FocusPurple = Color(0xFF8B5CF6)

@Composable
fun DashboardScreen(model: WukkiModel, playbackController: PlaybackController, scope: CoroutineScope, tick: Long, onOpenSettings: (SettingsSection) -> Unit) {
    var activeSection by remember { mutableStateOf("live") }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideNavigation(model, activeSection, onSelect = { section ->
                activeSection = section
                if (section == "settings") onOpenSettings(SettingsSection.PLAYBACK)
            }, modifier = Modifier.width(250.dp).fillMaxHeight())
            when (activeSection) {
                "live" -> LiveTvScreen(model, playbackController, Modifier.weight(1f).fillMaxHeight())
                "guide" -> EpgTimeline(model, tick, modifier = Modifier.weight(1f).fillMaxHeight())
                "channels" -> ChannelScreen(model, tick, modifier = Modifier.weight(1f).fillMaxHeight())
                else -> Unit
            }
        }
        model.error?.let { DashboardMessage("Hiba: $it", Color(0xFFFFB4AB), Color(0xFF5F1D22)) }
        model.status?.let { DashboardMessage(it, Color(0xFFB9F6CA), Color(0xFF12352C)) }
        RemoteHintBar(model)
    }
}

@Composable
private fun LiveTvScreen(model: WukkiModel, playbackController: PlaybackController, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.weight(.08f))
        LivePlayerCard(model, playbackController, modifier = Modifier.weight(.84f).fillMaxHeight())
        Spacer(Modifier.weight(.08f))
    }
}

@Composable
private fun ChannelScreen(model: WukkiModel, tick: Long, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ChannelDirectory(model, modifier = Modifier.weight(.59f).fillMaxHeight())
        ProgrammeInformation(model, tick, modifier = Modifier.weight(.41f).fillMaxHeight())
    }
}

@Composable
private fun SideNavigation(model: WukkiModel, activeSection: String, onSelect: (String) -> Unit, modifier: Modifier) {
    val entries = listOf(
        Triple("▣", "live", d(model, "Élő adás", "Live TV")),
        Triple("▦", "guide", d(model, "Műsorújság", "TV Guide")),
        Triple("▤", "channels", d(model, "Csatornák", "Channels")),
        Triple("⚙", "settings", d(model, "Beállítások", "Settings"))
    )
    DashboardCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("IPTV", fontWeight = FontWeight.Black, fontSize = 30.sp)
            Spacer(Modifier.width(6.dp))
            Text("TV", color = Color.White, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(FocusPurple).padding(horizontal = 5.dp, vertical = 3.dp))
        }
        Spacer(Modifier.height(52.dp))
        entries.forEach { (icon, id, title) ->
            val selected = id == activeSection
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
                    .background(if (selected) FocusPurple.copy(alpha = .35f) else Color.Transparent)
                    .clickable { onSelect(id) }.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, color = if (selected) Color.White else DashboardMuted, fontSize = 22.sp, modifier = Modifier.width(42.dp))
                Text(title, color = if (selected) Color.White else Color(0xFFE6EAF2), fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(formatTime(System.currentTimeMillis()), fontSize = 33.sp, fontWeight = FontWeight.Light)
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d., EEEE")), color = DashboardMuted, fontSize = 13.sp)
    }
}

@Composable
private fun LivePlayerCard(model: WukkiModel, playbackController: PlaybackController, modifier: Modifier) {
    val channel = model.selectedChannel()
    DashboardCard(modifier, contentPadding = 14.dp) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF102943), Color(0xFF07111D))))
        ) {
            if (channel == null) {
                Text(d(model, "Tölts be egy M3U playlistet a kezdéshez.", "Load an M3U playlist to get started."), color = DashboardMuted, modifier = Modifier.align(Alignment.Center))
            } else {
                EmbeddedVlcPlayer(playbackController, Modifier.fillMaxSize())
                DashboardLogo(model, channel, Modifier.size(92.dp).align(Alignment.TopStart).padding(12.dp))
                Row(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = Color.Red, fontSize = 16.sp)
                    Text(" ${d(model, "ÉLŐ", "LIVE")}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(channel.name, color = Color.White.copy(alpha = .12f), fontSize = 58.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
                PlaybackStatus(playbackController, model, Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
        }
        channel?.let { currentChannel ->
            val current = model.currentProgram(currentChannel)
            val next = current?.let { model.nextProgram(currentChannel, it) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(currentChannel.tvgChno?.toString() ?: "–", fontSize = 28.sp, fontWeight = FontWeight.Light, modifier = Modifier.width(55.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(current?.title ?: currentChannel.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(current?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: d(model, "EPG nincs", "No EPG"), color = DashboardMuted, fontSize = 12.sp)
                    current?.let { ProgrammeProgress(it, System.currentTimeMillis()) }
                    Text(next?.let { "${d(model, "Következő", "Next")}: ${it.title}" } ?: d(model, "Következő műsor nem elérhető", "Next programme unavailable"), color = DashboardMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PlaybackStatus(controller: PlaybackController, model: WukkiModel, modifier: Modifier = Modifier) {
    val label = when (controller.state) {
        PlaybackState.IDLE -> null
        PlaybackState.OPENING -> d(model, "Betöltés", "Opening")
        PlaybackState.BUFFERING -> d(model, "Pufferelés", "Buffering")
        PlaybackState.PLAYING -> null
        PlaybackState.RECONNECTING -> d(model, "Újracsatlakozás", "Reconnecting")
        PlaybackState.ERROR -> d(model, "Lejátszási hiba", "Playback error")
    } ?: return
    Text(
        text = listOf(label, controller.detail).filterNotNull().joinToString(" · "),
        color = if (controller.state == PlaybackState.ERROR) Color(0xFFFFB4AB) else Color.White,
        fontSize = 12.sp,
        modifier = modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xD90A1420)).padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
private fun EpgTimeline(model: WukkiModel, tick: Long, modifier: Modifier) {
    val channels = model.filteredChannels().take(5)
    DashboardCard(modifier, contentPadding = 0.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(d(model, "MŰSORÚJSÁG", "TV GUIDE"), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("‹", color = DashboardMuted, fontSize = 26.sp)
            Text(d(model, "Ma", "Today"), modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(FocusPurple.copy(alpha = .45f)).padding(horizontal = 18.dp, vertical = 8.dp))
            Text("›", color = DashboardMuted, fontSize = 26.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1825)).padding(vertical = 7.dp)) {
            Spacer(Modifier.width(94.dp))
            listOf("18:00", "18:30", "19:00", "19:30", "20:00").forEach { time -> Text(time, color = DashboardMuted, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
        }
        if (channels.isEmpty()) {
            Text(d(model, "Az EPG idővonal a playlist és XMLTV betöltése után jelenik meg.", "The EPG timeline appears after loading a playlist and XMLTV."), color = DashboardMuted, modifier = Modifier.padding(20.dp))
        } else {
            channels.forEach { channel -> EpgChannelRow(model, channel, tick) }
        }
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1825)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("● -24 ${d(model, "óra", "hours")}", color = Color(0xFFFF5C50), fontSize = 11.sp)
            Text("● +24 ${d(model, "óra", "hours")}", color = Color(0xFF55D967), fontSize = 11.sp)
            Text("● ${d(model, "Most", "Now")}", color = Color(0xFFFFB800), fontSize = 11.sp)
            Text("☰ ${d(model, "Opciók", "Options")}", color = DashboardMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EpgChannelRow(model: WukkiModel, channel: Channel, tick: Long) {
    val current = model.currentProgram(channel)
    val next = current?.let { model.nextProgram(channel, it) }
    Row(modifier = Modifier.fillMaxWidth().height(61.dp).background(Color(0xFF09131F)).clickable { model.selectChannel(channel.id) }, verticalAlignment = Alignment.CenterVertically) {
        Text(channel.tvgChno?.toString() ?: "–", color = DashboardMuted, modifier = Modifier.width(27.dp).padding(start = 8.dp))
        Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, modifier = Modifier.width(67.dp))
        EpgCell(model, current, selected = channel.id == model.selectedChannelId, modifier = Modifier.weight(1f), now = tick)
        EpgCell(model, next, selected = false, modifier = Modifier.weight(1f), now = tick)
    }
}

@Composable
private fun EpgCell(model: WukkiModel, programme: Programme?, selected: Boolean, modifier: Modifier, now: Long) {
    val color = if (selected) FocusPurple.copy(alpha = .32f) else Color(0xFF142234)
    Column(modifier = modifier.fillMaxHeight().background(color).then(if (selected) Modifier.clip(RoundedCornerShape(5.dp)) else Modifier).padding(7.dp)) {
        Text(programme?.title ?: d(model, "EPG nincs", "No EPG"), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(programme?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: "", color = DashboardMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ChannelDirectory(model: WukkiModel, modifier: Modifier) {
    val channels = model.filteredChannels()
    DashboardCard(modifier, contentPadding = 12.dp) {
        Text(d(model, "CSATORNÁK", "CHANNELS"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            FilterChip(selected = model.category == null && !model.onlyFavorites, onClick = { model.category = null; model.onlyFavorites = false }, label = { Text(d(model, "Minden", "All"), fontSize = 11.sp) })
            FilterChip(selected = model.onlyFavorites, onClick = { model.onlyFavorites = !model.onlyFavorites }, label = { Text(d(model, "Kedvencek", "Favorites"), fontSize = 11.sp) })
            model.categories().take(3).forEach { category -> FilterChip(selected = model.category == category, onClick = { model.category = if (model.category == category) null else category }, label = { Text(category, fontSize = 11.sp) }) }
        }
        OutlinedTextField(value = model.query, onValueChange = { model.query = it }, singleLine = true, label = { Text(d(model, "Keresés", "Search")) }, modifier = Modifier.fillMaxWidth())
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            items(channels, key = { it.id }) { channel ->
                val selected = channel.id == model.selectedChannelId
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (selected) FocusPurple.copy(alpha = .23f) else Color.Transparent).clickable { model.selectChannel(channel.id) }.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.tvgChno?.toString() ?: "–", modifier = Modifier.width(25.dp), color = DashboardMuted)
                    DashboardLogo(model, channel, Modifier.size(32.dp).padding(end = 6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (model.settings.display.showChannelProgramme) Text(model.currentProgram(channel)?.title ?: d(model, "EPG nincs", "No EPG"), color = DashboardMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (channel.favorite) "♥" else "♡", color = if (channel.favorite) FocusPurple else DashboardMuted, modifier = Modifier.clickable { model.toggleFavorite(channel.id) })
                }
            }
        }
    }
}

@Composable
private fun ProgrammeInformation(model: WukkiModel, tick: Long, modifier: Modifier) {
    val channel = model.selectedChannel()
    val programme = channel?.let { model.currentProgram(it) }
    DashboardCard(modifier) {
        Text(d(model, "MŰSOR INFORMÁCIÓ", "PROGRAMME INFORMATION"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (channel == null) {
            Text(d(model, "Válassz csatornát.", "Select a channel."), color = DashboardMuted)
        } else {
            DashboardLogo(model, channel, Modifier.fillMaxWidth().height(115.dp))
            Spacer(Modifier.height(10.dp))
            Text(d(model, "Most", "Now"), color = DashboardMuted, fontSize = 11.sp)
            Text(programme?.title ?: channel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(programme?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: d(model, "EPG nincs", "No EPG"), color = DashboardMuted, fontSize = 12.sp)
            programme?.let { ProgrammeProgress(it, tick) }
            Spacer(Modifier.height(10.dp))
            if (model.settings.display.showMiniGuide) Text(programme?.description ?: d(model, "A műsor leírása az XMLTV adataiból jelenik meg itt.", "The XMLTV programme description appears here."), color = DashboardMuted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { model.toggleFavorite(channel.id) }) { Text(if (channel.favorite) "★ ${d(model, "Kedvenc", "Favorite")}" else "☆ ${d(model, "Kedvencekhez adom", "Add to favorites")}") }
        }
    }
}

@Composable
private fun SettingsPreview(model: WukkiModel, scope: CoroutineScope, onOpenSettings: (SettingsSection) -> Unit, modifier: Modifier) {
    DashboardCard(modifier) {
        Text(d(model, "BEÁLLÍTÁSOK", "SETTINGS"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        listOf(
            SettingsSection.PLAYBACK to d(model, "Lejátszási beállítások", "Playback"), SettingsSection.EPG to "EPG",
            SettingsSection.DISPLAY to d(model, "Megjelenítés", "Appearance"), SettingsSection.PLAYLISTS to d(model, "Playlist kezelése", "Playlists"),
            SettingsSection.LANGUAGE to d(model, "Nyelv", "Language")
        ).forEach { (section, setting) ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF111D2B)).clickable { onOpenSettings(section) }.padding(horizontal = 11.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(setting, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text("›", color = DashboardMuted, fontSize = 20.sp)
            }
            Spacer(Modifier.height(3.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(d(model, "Playlist kezelés", "Playlists"), color = DashboardMuted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { scope.launch { model.refreshSelected() } }, enabled = model.selectedPlaylistId != null) { Text(d(model, "Frissítés", "Refresh"), fontSize = 11.sp) }
            TextButton(onClick = { model.setAutoRefresh(if (model.settings.playlistRefresh == RefreshInterval.MANUAL) 6 else 0) }) { Text(if (model.settings.playlistRefresh == RefreshInterval.MANUAL) d(model, "Auto: ki", "Auto: off") else "Auto: ${model.settings.playlistRefresh.hours}h", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ProgrammeProgress(programme: Programme, now: Long) {
    val progress = ((now - programme.start).toFloat() / (programme.end - programme.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    LinearProgressIndicator(progress = { progress }, color = FocusPurple, trackColor = Color(0xFF27364B), modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)))
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, contentPadding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, DashboardBorder), colors = CardDefaults.cardColors(containerColor = DashboardPanel)) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding), content = content)
    }
}

@Composable
private fun DashboardMessage(message: String, color: Color, background: Color) {
    Text(message, color = color, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(background).padding(horizontal = 12.dp, vertical = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RemoteHintBar(model: WukkiModel) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0xFF07111B)).padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("◉ ${d(model, "Navigáció", "Navigate")}", "OK ${d(model, "Kiválasztás", "Select")}", "↩ ${d(model, "Vissza", "Back")}", "● ${d(model, "Felvétel", "Record")}", "+ +24 ${d(model, "óra", "hours")}", "■ ${d(model, "Most", "Now")}", "INFO ${d(model, "Információ", "Info")}").forEach { hint -> Text(hint, color = DashboardMuted, fontSize = 11.sp) }
    }
}

@Composable
private fun DashboardLogo(model: WukkiModel, channel: Channel, modifier: Modifier) {
    if (model.settings.display.showLogos) ChannelLogo(channel, modifier)
    else Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF273653)), contentAlignment = Alignment.Center) { Text(channel.name.firstOrNull()?.uppercase() ?: "TV", fontWeight = FontWeight.Bold) }
}

@Composable
private fun d(model: WukkiModel, hungarian: String, english: String): String = if (model.settings.language == AppLanguage.HUNGARIAN) hungarian else english

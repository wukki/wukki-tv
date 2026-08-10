package hu.wukki.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DashboardPanel = AppBackground
private val DashboardMuted = Color(0xFF93A0B5)
private val DashboardBorder = Color(0xFF223047)
private val FocusPurple = Color(0xFF8B5CF6)

@Composable
fun DashboardScreen(
    model: WukkiModel,
    playbackController: PlaybackController,
    scope: CoroutineScope,
    tick: Long,
    activeSection: DashboardSection,
    guideState: EpgGuideState,
    onSectionChange: (DashboardSection) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SideNavigation(
                model = model,
                activeSection = activeSection,
                onSelect = onSectionChange,
                onOpenSettings = { onOpenSettings(SettingsSection.PLAYBACK) },
                modifier = Modifier.width(250.dp).fillMaxHeight()
            )
            when (activeSection) {
                DashboardSection.LIVE -> LiveTvScreen(model, playbackController, Modifier.fillMaxHeight().fillMaxWidth())
                DashboardSection.GUIDE -> EpgGuideScreen(model, tick, guideState, modifier = Modifier.weight(1f).fillMaxHeight().fillMaxWidth())
                DashboardSection.CHANNELS -> ChannelScreen(model, tick, modifier = Modifier.weight(1f).fillMaxHeight().fillMaxWidth())
            }
        }
        model.error?.let { DashboardMessage("Hiba: $it", Color(0xFFFFB4AB), Color(0xFF5F1D22)) }
        model.status?.let { DashboardMessage(it, Color(0xFFB9F6CA), Color(0xFF12352C)) }
        RemoteHintBar(model)
    }
}

@Composable
private fun LiveTvScreen(model: WukkiModel, playbackController: PlaybackController, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        Spacer(Modifier.weight(.08f))
        LivePlayerCard(model, playbackController, modifier = Modifier.fillMaxHeight())
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
private fun SideNavigation(
    model: WukkiModel,
    activeSection: DashboardSection,
    onSelect: (DashboardSection) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier
) {
    val entries = listOf(
        Triple("▣", DashboardSection.LIVE, d(model, "Élő adás", "Live TV")),
        Triple("▦", DashboardSection.GUIDE, d(model, "Műsorújság", "TV Guide")),
        Triple("▤", DashboardSection.CHANNELS, d(model, "Csatornák", "Channels"))
    )
    DashboardCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wukki", fontWeight = FontWeight.Black, fontSize = 30.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                "TV",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(FocusPurple)
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
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
                Text(
                    icon,
                    color = if (selected) Color.White else DashboardMuted,
                    fontSize = 22.sp,
                    modifier = Modifier.width(42.dp)
                )
                Text(
                    title,
                    color = if (selected) Color.White else Color(0xFFE6EAF2),
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).clickable(onClick = onOpenSettings)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚙", color = DashboardMuted, fontSize = 22.sp, modifier = Modifier.width(42.dp))
            Text(d(model, "Beállítások", "Settings"), color = Color(0xFFE6EAF2), fontSize = 17.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(formatTime(System.currentTimeMillis()), fontSize = 33.sp, fontWeight = FontWeight.Light)
        Text(
            LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d., EEEE")),
            color = DashboardMuted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun LivePlayerCard(model: WukkiModel, playbackController: PlaybackController, modifier: Modifier) {
    val channel = model.selectedChannel()
    DashboardCard(modifier, contentPadding = 1.dp) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF102943), Color(0xFF07111D))))
        ) {
            if (channel == null) {
                Text(
                    d(model, "Tölts be egy M3U playlistet a kezdéshez.", "Load an M3U playlist to get started."),
                    color = DashboardMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                EmbeddedVlcPlayer(playbackController, Modifier.fillMaxSize())
            }
        }
        channel?.let { currentChannel ->
            val current = model.currentProgram(currentChannel)
            val next = current?.let { model.nextProgram(currentChannel, it) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        currentChannel.tvgChno?.toString() ?: "–",
                        textAlign = TextAlign.Center,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.size(92.dp)
                    )
                    DashboardLogo(model, channel, Modifier.size(92.dp))
                }
                Column(modifier = Modifier.weight(7f).padding(horizontal = 12.dp)) {
                    Text(current?.title ?: currentChannel.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(current?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: d(
                        model,
                        "EPG nincs",
                        "No EPG"
                    ), color = DashboardMuted, fontSize = 12.sp)
                    current?.let { ProgrammeProgress(it, System.currentTimeMillis()) }
                    Text(next?.let { "${d(model, "Következő", "Next")}: ${it.title}" } ?: d(
                        model,
                        "Következő műsor nem elérhető",
                        "Next programme unavailable"
                    ), color = DashboardMuted, fontSize = 12.sp)
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
        modifier = modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xD90A1420))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

@Composable
private fun ChannelDirectory(model: WukkiModel, modifier: Modifier) {
    val channels = model.filteredChannels()
    DashboardCard(modifier, contentPadding = 12.dp) {
        Text(d(model, "CSATORNÁK", "CHANNELS"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            FilterChip(
                selected = model.category == null && !model.onlyFavorites,
                onClick = { model.category = null; model.onlyFavorites = false },
                label = { Text(d(model, "Minden", "All"), fontSize = 11.sp) })
            FilterChip(
                selected = model.onlyFavorites,
                onClick = { model.onlyFavorites = !model.onlyFavorites },
                label = { Text(d(model, "Kedvencek", "Favorites"), fontSize = 11.sp) })
            model.categories().take(3).forEach { category ->
                FilterChip(
                    selected = model.category == category,
                    onClick = { model.category = if (model.category == category) null else category },
                    label = { Text(category, fontSize = 11.sp) })
            }
        }
        OutlinedTextField(
            value = model.query,
            onValueChange = { model.query = it },
            singleLine = true,
            label = { Text(d(model, "Keresés", "Search")) },
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            items(channels, key = { it.id }) { channel ->
                val selected = channel.id == model.selectedChannelId
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(if (selected) FocusPurple.copy(alpha = .23f) else Color.Transparent)
                        .clickable { model.selectChannel(channel.id) }.padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(channel.tvgChno?.toString() ?: "–", modifier = Modifier.width(25.dp), color = DashboardMuted)
                    DashboardLogo(model, channel, Modifier.size(32.dp).padding(end = 6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            channel.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (model.settings.display.showChannelProgramme) Text(
                            model.currentProgram(channel)?.title ?: d(
                                model,
                                "EPG nincs",
                                "No EPG"
                            ), color = DashboardMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        if (channel.favorite) "♥" else "♡",
                        color = if (channel.favorite) FocusPurple else DashboardMuted,
                        modifier = Modifier.clickable { model.toggleFavorite(channel.id) })
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
            Text(programme?.let { "${formatTime(it.start)} – ${formatTime(it.end)}" } ?: d(
                model,
                "EPG nincs",
                "No EPG"
            ), color = DashboardMuted, fontSize = 12.sp)
            programme?.let { ProgrammeProgress(it, tick) }
            Spacer(Modifier.height(10.dp))
            if (model.settings.display.showMiniGuide) Text(
                programme?.description ?: d(
                    model,
                    "A műsor leírása az XMLTV adataiból jelenik meg itt.",
                    "The XMLTV programme description appears here."
                ), color = DashboardMuted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { model.toggleFavorite(channel.id) }) {
                Text(
                    if (channel.favorite) "★ ${
                        d(
                            model,
                            "Kedvenc",
                            "Favorite"
                        )
                    }" else "☆ ${d(model, "Kedvencekhez adom", "Add to favorites")}"
                )
            }
        }
    }
}

@Composable
private fun SettingsPreview(
    model: WukkiModel,
    scope: CoroutineScope,
    onOpenSettings: (SettingsSection) -> Unit,
    modifier: Modifier
) {
    DashboardCard(modifier) {
        Text(d(model, "BEÁLLÍTÁSOK", "SETTINGS"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        listOf(
            SettingsSection.PLAYBACK to d(model, "Lejátszási beállítások", "Playback"),
            SettingsSection.EPG to "EPG",
            SettingsSection.DISPLAY to d(model, "Megjelenítés", "Appearance"),
            SettingsSection.PLAYLISTS to d(model, "Playlist kezelése", "Playlists"),
            SettingsSection.LANGUAGE to d(model, "Nyelv", "Language")
        ).forEach { (section, setting) ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF111D2B))
                    .clickable { onOpenSettings(section) }.padding(horizontal = 11.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(setting, modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text("›", color = DashboardMuted, fontSize = 20.sp)
            }
            Spacer(Modifier.height(3.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(d(model, "Playlist kezelés", "Playlists"), color = DashboardMuted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { scope.launch { model.refreshSelected() } },
                enabled = model.selectedPlaylistId != null
            ) { Text(d(model, "Frissítés", "Refresh"), fontSize = 11.sp) }
            TextButton(onClick = { model.setAutoRefresh(if (model.settings.playlistRefresh == RefreshInterval.MANUAL) 6 else 0) }) {
                Text(
                    if (model.settings.playlistRefresh == RefreshInterval.MANUAL) d(
                        model,
                        "Auto: ki",
                        "Auto: off"
                    ) else "Auto: ${model.settings.playlistRefresh.hours}h",
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ProgrammeProgress(programme: Programme, now: Long) {
    val progress =
        ((now - programme.start).toFloat() / (programme.end - programme.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { progress },
        color = FocusPurple,
        trackColor = Color(0xFF27364B),
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp))
    )
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, DashboardBorder),
        colors = CardDefaults.cardColors(containerColor = DashboardPanel)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding), content = content)
    }
}

@Composable
private fun DashboardMessage(message: String, color: Color, background: Color) {
    Text(
        message,
        color = color,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(background)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RemoteHintBar(model: WukkiModel) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0xFF07111B))
            .padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            "◉ ${d(model, "Navigáció", "Navigate")}",
            "OK ${d(model, "Kiválasztás", "Select")}",
            "↩ ${d(model, "Vissza", "Back")}",
            "● ${d(model, "Felvétel", "Record")}",
            "+ +24 ${d(model, "óra", "hours")}",
            "■ ${d(model, "Most", "Now")}",
            "INFO ${d(model, "Információ", "Info")}"
        ).forEach { hint -> Text(hint, color = DashboardMuted, fontSize = 11.sp) }
    }
}

@Composable
private fun DashboardLogo(model: WukkiModel, channel: Channel, modifier: Modifier) {
    ChannelLogo(channel, modifier)
}

@Composable
private fun d(model: WukkiModel, hungarian: String, english: String): String =
    if (model.settings.language == AppLanguage.HUNGARIAN) hungarian else english

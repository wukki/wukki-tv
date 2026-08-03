package hu.wukki.tv

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun Header(model: WukkiModel, scope: CoroutineScope) {
    var playlistUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf(model.state.epgUrl) }
    Row(
        modifier = Modifier.fillMaxWidth().background(AppPanel).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("WUKKI TV", fontWeight = FontWeight.Black, fontSize = 20.sp, color = WukkiBlue)
        OutlinedTextField(value = playlistUrl, onValueChange = { playlistUrl = it }, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = {
            if (playlistUrl.isBlank()) model.showError("Adj meg egy M3U URL-t.") else scope.launch { model.addPlaylistFromUrl(playlistUrl.trim()) }
        }) { Text("Hozzáadás") }
        Button(onClick = { chooseM3uFile()?.let { file -> scope.launch { model.addPlaylistFromFile(file) } } }) { Text("Fájl import") }
        OutlinedTextField(value = epgUrl, onValueChange = { epgUrl = it }, label = { Text("XMLTV URL") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = {
            if (epgUrl.isBlank()) model.showError("Adj meg egy XMLTV URL-t.") else scope.launch { model.importEpg(epgUrl.trim()) }
        }) { Text("EPG betöltés") }
    }
}

@Composable
fun PlaylistPanel(model: WukkiModel, scope: CoroutineScope, modifier: Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(AppPanel).padding(12.dp)) {
        Text("PLAYLISTEK", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(model.state.playlists, key = { it.id }) { playlist ->
                val selected = model.selectedPlaylistId == playlist.id
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (selected) WukkiBlue.copy(alpha = .25f) else Color.Transparent)
                        .clickable { model.selectedPlaylistId = playlist.id }.padding(10.dp)
                ) {
                    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${model.state.channels.count { it.playlistId == playlist.id }} csatorna", color = Color.LightGray, fontSize = 12.sp)
                    Text("Frissítve: ${formatTime(playlist.updatedAt)}", color = Color.Gray, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { scope.launch { model.refreshSelected() } }, enabled = model.selectedPlaylistId != null) { Text("Frissítés") }
            TextButton(onClick = { model.removeSelectedPlaylist() }, enabled = model.selectedPlaylistId != null) { Text("Törlés") }
        }
        Spacer(Modifier.height(10.dp))
        Text("Automatikus frissítés", fontSize = 12.sp, color = Color.LightGray)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0 to "Kézi", 6 to "6 óra", 24 to "Napi").forEach { (hours, title) ->
                FilterChip(selected = model.state.autoRefreshHours == hours, onClick = { model.setAutoRefresh(hours) }, label = { Text(title) })
            }
        }
    }
}

@Composable
fun ChannelPanel(model: WukkiModel, modifier: Modifier) {
    val channels = model.filteredChannels()
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(AppPanel).padding(16.dp)) {
        val selected = model.selectedChannel()
        Text("MOST JÁTSZIK", color = Color.LightGray, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            selected?.let { ChannelLogo(it, Modifier.size(52.dp)) }
            Text(selected?.name ?: "Válassz playlistet", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        selected?.let { channel ->
            val programme = model.currentProgram(channel)
            Text(programme?.title ?: "EPG nincs", color = Color.LightGray)
            programme?.let { Progress(it, System.currentTimeMillis()) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { model.openSelectedStream() }) { Text("▶ Stream megnyitása") }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(value = model.query, onValueChange = { model.query = it }, label = { Text("Keresés csatornák között") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = model.onlyFavorites, onClick = { model.onlyFavorites = !model.onlyFavorites }, label = { Text("★ Kedvencek") })
            model.categories().take(4).forEach { category ->
                FilterChip(selected = model.category == category, onClick = { model.category = if (model.category == category) null else category }, label = { Text(category) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("CSATORNÁK (${channels.size})", fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(channels, key = { it.id }) { channel ->
                val isSelected = channel.id == model.selectedChannelId
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) WukkiBlue.copy(alpha = .35f) else Color.Transparent)
                        .clickable { model.selectChannel(channel.id) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (channel.favorite) "★" else "☆", color = if (channel.favorite) Color(0xFFFFD54F) else Color.Gray,
                        modifier = Modifier.clickable { model.toggleFavorite(channel.id) }.padding(end = 8.dp))
                    ChannelLogo(channel, Modifier.size(40.dp).padding(end = 8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(model.currentProgram(channel)?.title ?: "EPG nincs", color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(channel.group, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 76.dp))
                }
            }
        }
        Text("Billentyűzet: ↑/↓ vagy PageUp/PageDown vált, Enter stream, számok csatorna", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
fun GuidePanel(model: WukkiModel, tick: Long, modifier: Modifier) {
    val channel = model.selectedChannel()
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(AppPanel).padding(16.dp)) {
        Text("MINI GUIDE", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (channel == null) {
            Text("Tölts be egy playlistet a kezdéshez.", color = Color.LightGray)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelLogo(channel, Modifier.size(44.dp))
            Text(channel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        val current = model.currentProgram(channel)
        if (current == null) {
            Text("EPG nincs ehhez a csatornához.", color = Color.LightGray)
            Spacer(Modifier.height(8.dp))
            Text("Az XMLTV betöltése után a Wukki tvg-id, tvg-name és csatornanév alapján automatikusan párosít.", color = Color.Gray, fontSize = 12.sp)
        } else {
            Text("${formatTime(current.start)}–${formatTime(current.end)}", color = Color.LightGray)
            Text(current.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Progress(current, tick)
            val next = model.nextProgram(channel, current)
            Spacer(Modifier.height(12.dp))
            Text("KÖVETKEZŐ", color = Color.Gray, fontSize = 12.sp)
            Text(next?.title ?: "Nincs további műsor", fontWeight = FontWeight.SemiBold)
            next?.let { Text("${formatTime(it.start)}–${formatTime(it.end)}", color = Color.LightGray, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(18.dp))
        Text("TV GUIDE", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(model.filteredChannels().take(6), key = { it.id }) { item ->
                val programme = model.currentProgram(item)
                Column(modifier = Modifier.fillMaxWidth().clickable { model.selectChannel(item.id) }.padding(vertical = 8.dp)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    Text(programme?.let { "${formatTime(it.start)} ${it.title}" } ?: "EPG nincs", color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = modifier.clip(shape).background(Color(0xFF273653)), contentAlignment = Alignment.Center) {
        if (channel.logo.isNullOrBlank()) {
            LogoFallback(channel)
        } else {
            SubcomposeAsyncImage(
                model = channel.logo,
                contentDescription = "${channel.name} logója",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { LogoFallback(channel) },
                error = { LogoFallback(channel) }
            )
        }
    }
}

@Composable
private fun LogoFallback(channel: Channel) {
    Text(channel.name.trim().firstOrNull()?.uppercase() ?: "TV", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
}

@Composable
private fun Progress(programme: Programme, now: Long) {
    val duration = (programme.end - programme.start).coerceAtLeast(1)
    val progress = ((now - programme.start).toFloat() / duration).coerceIn(0f, 1f)
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)))
}

private fun chooseM3uFile(): File? {
    val dialog = FileDialog(null as java.awt.Frame?, "M3U playlist kiválasztása", FileDialog.LOAD)
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}

private fun formatTime(millis: Long): String = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

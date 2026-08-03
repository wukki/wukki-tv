package hu.wukki.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun WukkiApp() {
    val model = remember { WukkiModel() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    LaunchedEffect(model.state.autoRefreshHours) {
        val hours = model.state.autoRefreshHours
        if (hours > 0) {
            while (true) {
                delay(hours * 60L * 60L * 1000L)
                model.refreshAll(scope)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.PageDown, Key.DirectionDown -> model.moveChannel(1)
                    Key.PageUp, Key.DirectionUp -> model.moveChannel(-1)
                    Key.Enter -> model.openSelectedStream()
                    Key.One -> model.selectChannelByNumber("1")
                    Key.Two -> model.selectChannelByNumber("2")
                    Key.Three -> model.selectChannelByNumber("3")
                    Key.Four -> model.selectChannelByNumber("4")
                    Key.Five -> model.selectChannelByNumber("5")
                    Key.Six -> model.selectChannelByNumber("6")
                    Key.Seven -> model.selectChannelByNumber("7")
                    Key.Eight -> model.selectChannelByNumber("8")
                    Key.Nine -> model.selectChannelByNumber("9")
                    Key.Zero -> model.selectChannelByNumber("0")
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
    ) {
        Header(model, scope)
        model.error?.let { message ->
            Text("Hiba: $message", color = Color(0xFFFFB4AB), modifier = Modifier.background(Color(0xFF5F1D22)).padding(12.dp))
        }
        model.status?.let { message ->
            Text(message, color = Color(0xFFB9F6CA), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlaylistPanel(model, scope, Modifier.width(255.dp).fillMaxHeight())
            ChannelPanel(model, Modifier.weight(1f).fillMaxHeight())
            GuidePanel(model, tick, Modifier.width(355.dp).fillMaxHeight())
        }
    }
}

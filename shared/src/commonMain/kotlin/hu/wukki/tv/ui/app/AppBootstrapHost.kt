package hu.wukki.tv.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.wukki.tv.AppBootstrap
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.BootstrapState
import hu.wukki.tv.WukkiModel
import hu.wukki.tv.ui.components.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AppBootstrapHost(
    bootstrap: AppBootstrap,
    content: @Composable (WukkiModel) -> Unit,
) {
    val state by bootstrap.state.collectAsState()
    LaunchedEffect(bootstrap) { bootstrap.start() }
    when (val current = state) {
        BootstrapState.Loading -> {
            BootstrapMessage("storage.loading") { CircularProgressIndicator() }
        }

        is BootstrapState.Failed -> {
            BootstrapMessage("storage.load.failed") {
                Button(onClick = bootstrap::start) { Text(tr(AppLanguage.HUNGARIAN, "action.retry")) }
            }
        }

        is BootstrapState.Ready -> {
            ReadyContent(current, content)
        }
    }
}

@Composable
private fun BootstrapMessage(
    key: String,
    action: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr(AppLanguage.HUNGARIAN, key))
            action()
        }
    }
}

@Composable
private fun ReadyContent(
    ready: BootstrapState.Ready,
    content: @Composable (WukkiModel) -> Unit,
) {
    val failure by ready.writer.failure.collectAsState()
    val scope = rememberCoroutineScope()
    val language = ready.model.settings.language
    LaunchedEffect(ready) {
        if (ready.cacheWarning) ready.model.showRawError(tr(language, "storage.cache.recovered"))
    }
    Box(Modifier.fillMaxSize()) {
        content(ready.model)
        if (failure != null) {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                ready.writer.flush()
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (_: Exception) {
                                // The writer keeps the error visible until a write succeeds.
                            }
                        }
                    }) { Text(tr(language, "action.retry")) }
                },
            ) { Text(tr(language, "storage.save.failed")) }
        }
    }
}

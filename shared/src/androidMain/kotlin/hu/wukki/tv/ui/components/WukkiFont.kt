package hu.wukki.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

private object FontResourceAnchor

@Composable
internal actual fun wukkiFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        FontFamily(
            listOf(300, 400, 500, 600, 700, 900).map { weight ->
                val file = File(context.cacheDir, "wukki-inter-v1-$weight.ttf")
                if (!file.exists()) {
                    val stream = checkNotNull(FontResourceAnchor::class.java.classLoader?.getResourceAsStream("fonts/inter_$weight.ttf"))
                    stream.use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                Font(file, FontWeight(weight))
            },
        )
    }
}

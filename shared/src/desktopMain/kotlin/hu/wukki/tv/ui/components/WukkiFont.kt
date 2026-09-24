package hu.wukki.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

@Composable
internal actual fun wukkiFontFamily(): FontFamily =
    remember {
        FontFamily(
            listOf(300, 400, 500, 600, 700, 900).map { weight ->
                Font("fonts/inter_$weight.ttf", FontWeight(weight))
            },
        )
    }

package hu.wukki.tv.ui.app

import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AppFeedback(message: String) {
    Snackbar { Text(message, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

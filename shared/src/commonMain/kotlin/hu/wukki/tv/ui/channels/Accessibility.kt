package hu.wukki.tv.ui.channels

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Places the accessible name on the clickable icon-button container. */
internal fun Modifier.iconButtonSemantics(label: String): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = label
    }

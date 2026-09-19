package hu.wukki.tv.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun desktopLiveNavigationPointerModifier(onShowNavigation: () -> Unit): Modifier {
    val currentOnShow by rememberUpdatedState(onShowNavigation)
    val density = LocalDensity.current.density
    return Modifier
        .onPointerEvent(PointerEventType.Move) { event ->
            if (event.changes.any { isInDesktopNavigationEdge(it.position.y / density) }) currentOnShow()
        }.onPointerEvent(PointerEventType.Enter) { event ->
            if (event.changes.any { isInDesktopNavigationEdge(it.position.y / density) }) currentOnShow()
        }
}

internal fun isInDesktopNavigationEdge(logicalY: Float): Boolean = logicalY >= 0f && logicalY < 20f

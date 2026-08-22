package hu.wukki.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import hu.wukki.tv.AppLanguage
import hu.wukki.tv.Programme

/** Stable-size XMLTV programme artwork backed by Coil's shared memory and disk cache. */
@Composable
fun ProgrammeArtwork(
    programme: Programme,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val imageUrl = programme.imageUrl ?: return
    val shape = RoundedCornerShape(8.dp)
    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = tr(language, "programme.image.description", programme.displayTitle(language)),
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape),
        loading = { ArtworkPlaceholder() },
        error = { ArtworkPlaceholder() }
    )
}

@Composable
private fun ArtworkPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(WukkiColors.surfaceInput),
        contentAlignment = Alignment.Center
    ) { }
}

package androidx.compose.ui

import androidx.compose.ui.draw.clip as drawClip
import androidx.compose.ui.graphics.Shape

/**
 * Compatibility bridge for code that imports Modifier.clip from androidx.compose.ui.
 * The actual Compose implementation lives in androidx.compose.ui.draw.clip.
 */
fun Modifier.clip(shape: Shape): Modifier = drawClip(shape)

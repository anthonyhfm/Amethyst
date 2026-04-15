package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Displays content constrained to a desired aspect ratio.
 *
 * @param ratio Width-to-height ratio (e.g. 16f / 9f for widescreen, 1f for square)
 * @param modifier Modifier applied to the outer container
 * @param content Composable content rendered inside the aspect ratio container
 */
@Composable
fun AspectRatio(
    ratio: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.aspectRatio(ratio),
    ) {
        content()
    }
}

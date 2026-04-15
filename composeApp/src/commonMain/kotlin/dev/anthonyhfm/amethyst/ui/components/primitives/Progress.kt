package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProgressBar
import com.composeunstyled.ProgressIndicator
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary

@Composable
fun Progress(
    value: Float,
    modifier: Modifier = Modifier,
) {
    ProgressIndicator(
        progress = value.coerceIn(0f, 1f),
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
        shape = FullShape,
        backgroundColor = Theme[colors][secondary],
        contentColor = Theme[colors][primary],
    ) {
        ProgressBar(shape = FullShape)
    }
}

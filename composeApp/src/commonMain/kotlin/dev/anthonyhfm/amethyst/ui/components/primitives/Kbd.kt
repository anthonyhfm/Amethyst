package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.inlineCode
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.typography
import androidx.compose.foundation.text.BasicText

@Composable
fun Kbd(
    keys: String,
    modifier: Modifier = Modifier,
) {
    val bg = Theme[colors][muted]
    val fg = Theme[colors][foreground]
    val borderColor = Theme[colors][border]
    val textStyle = Theme[typography][inlineCode].copy(
        color = fg,
        textAlign = TextAlign.Center,
    )

    Box(
        modifier = modifier
            .clip(SmallShape)
            .border(1.dp, borderColor, SmallShape)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        BasicText(
            text = keys,
            style = textStyle,
        )
    }
}

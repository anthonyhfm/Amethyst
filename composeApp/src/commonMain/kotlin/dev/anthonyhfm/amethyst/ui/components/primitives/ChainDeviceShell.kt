package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.chainSurfaceRaised
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.selectionForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun ChainDeviceShell(
    title: String,
    isSelected: Boolean,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
    titleBarModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val titleBarColor = if (isSelected) {
        Theme[colors][selectionSurface]
    } else {
        Theme[chainColorTokens][chainSurfaceRaised]
    }

    val titleColor = if (isSelected) {
        Theme[colors][selectionForeground]
    } else {
        Theme[colors][cardForeground]
    }

    val borderColor = if (isSelected) {
        Theme[colors][selectionSurface]
    } else {
        Theme[chainColorTokens][chainBorder]
    }

    Column(
        modifier = modifier
            .clip(DefaultShape)
            .fillMaxHeight()
            .background(Theme[chainColorTokens][chainSurface])
            .border(1.dp, borderColor, DefaultShape)
            .alpha(if (isDragging) 0.2f else 1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(titleBarColor)
                .then(titleBarModifier)
        ) {
            Text(
                text = title,
                style = Theme[typography][small],
                color = titleColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

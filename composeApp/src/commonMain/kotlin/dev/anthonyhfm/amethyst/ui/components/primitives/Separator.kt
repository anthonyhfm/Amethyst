package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors

@Composable
fun Separator(
    modifier: Modifier = Modifier,
    orientation: SeparatorOrientation = SeparatorOrientation.Horizontal,
    decorative: Boolean = true,
) {
    val color = Theme[colors][border]
    val semanticsModifier = if (decorative) {
        modifier.clearAndSetSemantics { }
    } else {
        modifier
    }

    when (orientation) {
        SeparatorOrientation.Horizontal -> {
            Box(semanticsModifier.fillMaxWidth().height(1.dp).background(color))
        }
        SeparatorOrientation.Vertical -> {
            Box(semanticsModifier.fillMaxHeight().width(1.dp).background(color))
        }
    }
}

enum class SeparatorOrientation {
    Horizontal,
    Vertical,
}

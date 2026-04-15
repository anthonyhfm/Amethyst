package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.composeunstyled.RelativeAlignment
import com.composeunstyled.Text
import com.composeunstyled.Tooltip
import com.composeunstyled.TooltipPanel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun Tooltip(
    text: String,
    modifier: Modifier = Modifier,
    placement: RelativeAlignment = RelativeAlignment.BottomCenter,
    enabled: Boolean = true,
    anchor: @Composable () -> Unit,
) {
    Tooltip(
        modifier = modifier,
        placement = placement,
        enabled = enabled,
        anchor = anchor,
        content = {
            Text(
                text = text,
                style = Theme[typography][small],
                fontSize = 12.sp,
            )
        },
    )
}

@Composable
fun Tooltip(
    modifier: Modifier = Modifier,
    placement: RelativeAlignment = RelativeAlignment.BottomCenter,
    enabled: Boolean = true,
    anchor: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Tooltip(
            enabled = enabled,
            placement = placement,
            hoverDelayMillis = 400L,
            panel = {
                TooltipPanel(
                    shape = SmallShape,
                    backgroundColor = Theme[colors][foreground],
                    contentColor = Theme[colors][background],
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.zIndex(1000f),
                    content = content,
                )
            },
            anchor = anchor,
        )
    }
}

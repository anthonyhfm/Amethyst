package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.composeunstyled.RelativeAlignment
import com.composeunstyled.Tooltip
import com.composeunstyled.TooltipPanel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground

@Composable
fun HoverCard(
    modifier: Modifier = Modifier,
    placement: RelativeAlignment = RelativeAlignment.BottomCenter,
    openDelay: Long = 700L,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
    trigger: @Composable () -> Unit,
) {
    Tooltip(
        enabled = enabled,
        placement = placement,
        hoverDelayMillis = openDelay,
        panel = {
            TooltipPanel(
                shape = DefaultShape,
                backgroundColor = Theme[colors][popover],
                contentColor = Theme[colors][popoverForeground],
                contentPadding = PaddingValues(16.dp),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = modifier
                    .shadow(8.dp, DefaultShape)
                    .border(1.dp, Theme[colors][border], DefaultShape),
            ) {
                content()
            }
        },
        anchor = trigger,
    )
}

@Composable
fun HoverCardTrigger(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
    }
}

@Composable
fun HoverCardContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(8.dp, DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(Theme[colors][popover], DefaultShape)
            .clip(DefaultShape)
            .padding(16.dp),
    ) {
        content()
    }
}

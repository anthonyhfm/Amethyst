package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun Item(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .clip(DefaultShape)
            .hoverable(interactionSource)
            .hoverBackground(interactionSource, Theme[colors][accent])
            .padding(horizontal = 12.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
fun ItemIcon(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(20.dp),
    ) {
        ProvideTextStyle(Theme[typography][p].copy(color = Theme[colors][foreground])) {
            content()
        }
    }
}

@Composable
fun RowScope.ItemContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.weight(1f),
    ) {
        content()
    }
}

@Composable
fun ItemTitle(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][foreground])) {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
fun ItemDescription(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][mutedForeground])) {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
fun ItemTrailing(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier,
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][mutedForeground])) {
            content()
        }
    }
}

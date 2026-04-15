package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.composeunstyled.DropdownPanelAnchor
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuPanel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.h4
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun Popover(
    expanded: Boolean,
    onExpandRequest: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: DropdownPanelAnchor = DropdownPanelAnchor.BottomStart,
    trigger: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    UnstyledDropdownMenu(
        onExpandRequest = onExpandRequest,
        modifier = modifier,
    ) {
        trigger()

        UnstyledDropdownMenuPanel(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            shape = DefaultShape,
            backgroundColor = Theme[colors][popover],
            contentColor = Theme[colors][popoverForeground],
            contentPadding = PaddingValues(16.dp),
            anchor = anchor,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 200.dp)
                .border(1.dp, Theme[colors][border], DefaultShape)
                .shadow(8.dp, DefaultShape),
            content = content,
        )
    }
}

@Composable
fun PopoverHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun PopoverTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = Theme[typography][h4],
        color = Theme[colors][foreground],
    )
}

@Composable
fun PopoverDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = Theme[typography][small],
        color = Theme[colors][mutedForeground],
    )
}

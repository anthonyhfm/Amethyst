package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

/**
 * A command menu container for search and quick actions, inspired by cmdk / shadcn Command.
 */
@Composable
fun Command(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(DefaultShape)
            .background(Theme[colors][popover])
            .border(1.dp, Theme[colors][border], DefaultShape),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][popoverForeground])) {
            content()
        }
    }
}

/**
 * Search input field for the command menu with a leading search icon.
 */
@Composable
fun CommandInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type a command or search…",
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Theme[colors][mutedForeground],
        )

        Spacer(Modifier.width(8.dp))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Theme[typography][small].copy(color = Theme[colors][foreground]),
            cursorBrush = SolidColor(Theme[colors][foreground]),
            interactionSource = interactionSource,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = Theme[typography][small],
                            color = Theme[colors][mutedForeground],
                        )
                    }
                    innerTextField()
                }
            },
        )
    }

    Separator()
}

/**
 * Scrollable container for command items, groups, and empty states.
 */
@Composable
fun CommandList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .heightIn(max = 300.dp)
            .verticalScroll(scrollState)
            .padding(4.dp),
    ) {
        content()
    }
}

/**
 * Displayed when the command list has no results.
 */
@Composable
fun CommandEmpty(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][mutedForeground])) {
            content()
        }
    }
}

/**
 * A labelled group of command items.
 */
@Composable
fun CommandGroup(
    modifier: Modifier = Modifier,
    heading: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        if (heading != null) {
            Text(
                text = heading,
                style = Theme[typography][small].copy(
                    color = Theme[colors][mutedForeground],
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        content()
    }
}

/**
 * A selectable item in the command menu with hover highlighting.
 */
@Composable
fun CommandItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .then(
                if (hovered) Modifier.background(Theme[colors][accent])
                else Modifier
            ),
    ) {
        ProvideTextStyle(
            Theme[typography][small].copy(
                color = if (hovered) Theme[colors][accentForeground] else Theme[colors][popoverForeground],
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

/**
 * A horizontal separator between command groups.
 */
@Composable
fun CommandSeparator(
    modifier: Modifier = Modifier,
) {
    Separator(
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * A keyboard shortcut hint displayed at the trailing edge of a CommandItem.
 * Must be called inside a [Row] scope (e.g. within [CommandItem] content).
 */
@Composable
fun RowScope.CommandShortcut(
    text: String,
    modifier: Modifier = Modifier,
) {
    Spacer(Modifier.weight(1f))
    Text(
        text = text,
        style = Theme[typography][small].copy(
            color = Theme[colors][mutedForeground],
        ),
        modifier = modifier,
    )
}

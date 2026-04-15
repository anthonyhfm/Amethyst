package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.ring
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

private val LocalInputGroupFocused = compositionLocalOf { false }
private val LocalInputGroupBorderColor = compositionLocalOf { Color.Unspecified }

/**
 * Container that groups an input with prefix/suffix addons into a connected visual unit.
 */
@Composable
fun InputGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = DefaultShape

    val borderColor = if (focused) Theme[colors][ring] else Theme[colors][input]
    val borderWidth = if (focused) 2.dp else 1.dp

    CompositionLocalProvider(
        LocalInputGroupFocused provides focused,
        LocalInputGroupBorderColor provides Theme[colors][input],
    ) {
        Row(
            modifier = modifier
                .height(40.dp)
                .clip(shape)
                .border(borderWidth, borderColor, shape)
                .background(Theme[colors][background]),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

enum class InputGroupAddonAlign {
    Start,
    End,
}

/**
 * Addon slot for prefix or suffix content (icons, text, buttons) within an [InputGroup].
 * Shares a border with the adjacent input, creating a connected visual unit.
 */
@Composable
fun InputGroupAddon(
    modifier: Modifier = Modifier,
    align: InputGroupAddonAlign = InputGroupAddonAlign.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val separatorColor = LocalInputGroupBorderColor.current
        .takeIf { it != Color.Unspecified }
        ?: Theme[colors][input]

    val separatorModifier = Modifier.drawBehind {
        val strokeWidth = 1.dp.toPx()
        when (align) {
            InputGroupAddonAlign.Start -> {
                // Draw separator on the right edge
                drawLine(
                    color = separatorColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth,
                )
            }
            InputGroupAddonAlign.End -> {
                // Draw separator on the left edge
                drawLine(
                    color = separatorColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = strokeWidth,
                )
            }
        }
    }

    Row(
        modifier = modifier
            .then(separatorModifier)
            .background(Theme[colors][muted])
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][mutedForeground])) {
            content()
        }
    }
}

/**
 * Input variant styled for use inside an [InputGroup].
 * Removes its own border and shape so it blends with the group container.
 */
@Composable
fun RowScope.InputGroupInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .weight(1f)
            .padding(horizontal = 12.dp),
        enabled = enabled,
        singleLine = singleLine,
        textStyle = Theme[typography][small].copy(color = Theme[colors][foreground]),
        cursorBrush = SolidColor(Theme[colors][foreground]),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
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

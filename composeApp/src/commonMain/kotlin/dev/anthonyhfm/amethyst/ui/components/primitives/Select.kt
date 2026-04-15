package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuPanel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

private val LocalSelectDismissRequest = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * A styled select dropdown following shadcn/ui patterns.
 *
 * Renders a trigger button that opens a dropdown panel populated by the
 * [content] lambda — typically a list of [SelectItem], [SelectLabel], and
 * [SelectSeparator] composables.
 */
@Composable
fun Select(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    shape: Shape = DefaultShape,
    triggerHeight: Dp = 40.dp,
    triggerContentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    var triggerWidth by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(LocalSelectDismissRequest provides { expanded = false }) {
        UnstyledDropdownMenu(
            onExpandRequest = { if (enabled) expanded = true },
            modifier = modifier.then(if (!enabled) Modifier.alpha(0.5f) else Modifier),
        ) {
            // Trigger
            UnstyledButton(
                onClick = { if (enabled) expanded = true },
                shape = shape,
                interactionSource = interactionSource,
                indication = null,
                contentPadding = triggerContentPadding,
                borderColor = Theme[colors][input],
                borderWidth = 1.dp,
                modifier = Modifier
                    .height(triggerHeight)
                    .clip(shape)
                    .background(Theme[colors][background])
                    .onSizeChanged { triggerWidth = with(density) { it.width.toDp() } },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val displayText = value.ifEmpty { placeholder }
                    val textColor =
                        if (value.isEmpty()) Theme[colors][mutedForeground]
                        else Theme[colors][foreground]

                    Text(
                        text = displayText,
                        style = Theme[typography][small],
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Open select",
                        modifier = Modifier.size(16.dp),
                        tint = Theme[colors][mutedForeground],
                    )
                }
            }

            // Panel
            UnstyledDropdownMenuPanel(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = shape,
                backgroundColor = Theme[colors][popover],
                contentColor = Theme[colors][popoverForeground],
                contentPadding = PaddingValues(4.dp),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(100)),
                modifier = Modifier
                    .then(if (triggerWidth > 0.dp) Modifier.width(triggerWidth) else Modifier)
                    .shadow(8.dp, shape)
                    .border(1.dp, Theme[colors][border], shape),
                content = content,
            )
        }
    }
}

/**
 * Convenience overload that renders a flat list of string [options].
 */
@Composable
fun Select(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    shape: Shape = DefaultShape,
    triggerHeight: Dp = 40.dp,
    triggerContentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    Select(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        shape = shape,
        triggerHeight = triggerHeight,
        triggerContentPadding = triggerContentPadding,
    ) {
        options.forEach { option ->
            SelectItem(
                text = option,
                selected = option == value,
                onClick = { onValueChange(option) },
            )
        }
    }
}

/**
 * An item inside a [Select] dropdown panel.
 *
 * Displays a check icon when [selected] and highlights on hover following
 * shadcn/ui conventions.
 */
@Composable
fun SelectItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dismiss = LocalSelectDismissRequest.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = hovered || selected

    UnstyledButton(
        onClick = {
            if (enabled) {
                onClick()
                dismiss()
            }
        },
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .then(if (!enabled) Modifier.alpha(0.5f) else Modifier)
            .then(
                if (highlighted) Modifier.background(Theme[colors][accent])
                else Modifier
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (highlighted) Theme[colors][accentForeground]
                            else Theme[colors][foreground],
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ProvideTextStyle(
                Theme[typography][small].copy(
                    color = if (highlighted) Theme[colors][accentForeground]
                        else Theme[colors][foreground]
                )
            ) {
                Text(text)
            }
        }
    }
}

/**
 * A horizontal divider for grouping items within a [Select] panel.
 */
@Composable
fun SelectSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Theme[colors][border])
    )
}

/**
 * A non-interactive label for grouping items within a [Select] panel.
 */
@Composable
fun SelectLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    ProvideTextStyle(
        Theme[typography][small].copy(color = Theme[colors][mutedForeground])
    ) {
        Text(
            text = text,
            modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

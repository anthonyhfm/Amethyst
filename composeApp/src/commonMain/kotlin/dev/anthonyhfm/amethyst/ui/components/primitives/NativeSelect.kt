package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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

/**
 * A simple native-style select dropdown following shadcn/ui NativeSelect patterns.
 *
 * Unlike [Select], this component renders a flat list of options without check
 * icons or complex interactions — similar to an HTML `<select>` element.
 *
 * @param value The currently selected value, or empty string for no selection.
 * @param onValueChange Callback invoked when the user picks an option.
 * @param options The list of available [NativeSelectOption] entries.
 * @param modifier Optional [Modifier] for the root element.
 * @param placeholder Text shown when [value] is empty.
 * @param enabled Whether the select is interactive.
 */
@Composable
fun NativeSelect(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<NativeSelectOption>,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = DefaultShape
    val density = LocalDensity.current
    var triggerWidth by remember { mutableStateOf(0.dp) }

    val displayText = options.firstOrNull { it.value == value }?.label ?: value.ifEmpty { placeholder }
    val textColor =
        if (value.isEmpty()) Theme[colors][mutedForeground]
        else Theme[colors][foreground]

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
            contentPadding = PaddingValues(horizontal = 12.dp),
            borderColor = Theme[colors][input],
            borderWidth = 1.dp,
            modifier = Modifier
                .height(40.dp)
                .clip(shape)
                .background(Theme[colors][background])
                .onSizeChanged { triggerWidth = with(density) { it.width.toDp() } },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
        ) {
            options.forEach { option ->
                NativeSelectOptionItem(
                    option = option,
                    selected = option.value == value,
                    onClick = {
                        onValueChange(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Represents a single option within a [NativeSelect].
 *
 * @param value The backing value emitted through `onValueChange`.
 * @param label The display text shown for this option.
 * @param disabled Whether the option is non-interactive.
 */
data class NativeSelectOption(
    val value: String,
    val label: String = value,
    val disabled: Boolean = false,
)

@Composable
private fun NativeSelectOptionItem(
    option: NativeSelectOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = hovered || selected

    UnstyledButton(
        onClick = { if (!option.disabled) onClick() },
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .then(if (option.disabled) Modifier.alpha(0.5f) else Modifier)
            .then(
                if (highlighted) Modifier.background(Theme[colors][accent])
                else Modifier
            ),
    ) {
        ProvideTextStyle(
            Theme[typography][small].copy(
                color = if (highlighted) Theme[colors][accentForeground]
                else Theme[colors][foreground]
            )
        ) {
            Text(
                text = option.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            )
        }
    }
}

package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.ring
import dev.anthonyhfm.amethyst.ui.theme.typography

internal data class InputOtpState(
    val value: String,
    val maxLength: Int,
    val isFocused: Boolean,
    val enabled: Boolean,
)

internal val LocalInputOtpState = compositionLocalOf<InputOtpState> {
    error("InputOtpSlot must be used within an InputOtp")
}

/**
 * OTP input that manages a full value string and distributes characters to visual slots.
 * A hidden [BasicTextField] captures actual keyboard input.
 *
 * @param value Current OTP string.
 * @param onValueChange Called when the value changes.
 * @param maxLength Maximum number of characters (e.g. 6).
 * @param enabled Whether the input is interactive.
 * @param pattern Optional regex that every intermediate value must match. Defaults to digits only.
 * @param content Slot layout – typically [InputOtpGroup] and [InputOtpSeparator] composables.
 */
@Composable
fun InputOtp(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pattern: Regex = Regex("^\\d*$"),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val state = InputOtpState(
        value = value,
        maxLength = maxLength,
        isFocused = isFocused,
        enabled = enabled,
    )

    // Hidden text field that captures keyboard input
    Box(modifier = modifier.alpha(if (enabled) 1f else 0.5f)) {
        CompositionLocalProvider(LocalInputOtpState provides state) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }

        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                if (newValue.length <= maxLength && pattern.matches(newValue)) {
                    onValueChange(newValue)
                }
            },
            modifier = Modifier
                .matchParentSize()
                .alpha(0f),
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = Theme[typography][p].copy(color = Theme[colors][foreground]),
            cursorBrush = SolidColor(Theme[colors][foreground]),
            interactionSource = interactionSource,
            decorationBox = { innerTextField -> innerTextField() },
        )
    }
}

/**
 * Groups [InputOtpSlot] composables into a contiguous row without extra spacing.
 */
@Composable
fun InputOtpGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * Individual digit slot that displays a single character from the OTP value.
 *
 * @param index Zero-based index into the OTP value string this slot represents.
 */
@Composable
fun InputOtpSlot(
    index: Int,
    modifier: Modifier = Modifier,
) {
    val state = LocalInputOtpState.current
    val char = state.value.getOrNull(index)
    val isActive = state.isFocused && index == state.value.length.coerceAtMost(state.maxLength - 1)
    val shape = SmallShape

    val borderColor = when {
        isActive -> Theme[colors][ring]
        else -> Theme[colors][input]
    }
    val borderWidth = if (isActive) 2.dp else 1.dp

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(Theme[colors][background])
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) {
            Text(
                text = char.toString(),
                style = Theme[typography][p],
                color = Theme[colors][foreground],
            )
        } else if (isActive) {
            // Blinking cursor indicator
            Box(
                Modifier
                    .size(width = 1.dp, height = 20.dp)
                    .background(Theme[colors][foreground])
            )
        }
    }
}

/**
 * Separator rendered between [InputOtpGroup] composables (e.g. a dash).
 */
@Composable
fun InputOtpSeparator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.width(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "–",
            style = Theme[typography][p],
            color = Theme[colors][mutedForeground],
            textAlign = TextAlign.Center,
        )
    }
}

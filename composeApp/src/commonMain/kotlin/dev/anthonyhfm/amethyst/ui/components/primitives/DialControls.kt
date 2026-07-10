package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.ui.components.DialType

@Composable
fun <T> Dial(
    type: DialType<T>,
    value: T,
    onValueChange: (T) -> Unit,
    onStartValueChange: (T) -> Unit = {},
    onFinishValueChange: (T) -> Unit = {},
    title: String? = null,
    text: String? = null,
    onResolveTextValue: ((String) -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    dialColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    defaultValue: T? = null,
) = dev.anthonyhfm.amethyst.ui.components.Dial(
    type = type,
    value = value,
    onValueChange = onValueChange,
    onStartValueChange = onStartValueChange,
    onFinishValueChange = onFinishValueChange,
    title = title,
    text = text,
    onResolveTextValue = onResolveTextValue,
    containerColor = containerColor,
    dialColor = dialColor,
    modifier = modifier,
    enabled = enabled,
    defaultValue = defaultValue,
)

@Composable
fun TimeDial(
    title: String? = "Duration",
    timing: Timing,
    onSelectTiming: (timing: Timing, msValue: Long) -> Unit,
    onStartValueChange: (timing: Timing, msValue: Long) -> Unit = { _, _ -> },
    onFinishValueChange: (timing: Timing, msValue: Long) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    text: String? = null,
) = dev.anthonyhfm.amethyst.ui.components.TimeDial(
    title = title,
    timing = timing,
    onSelectTiming = onSelectTiming,
    onStartValueChange = onStartValueChange,
    onFinishValueChange = onFinishValueChange,
    enabled = enabled,
    text = text,
)

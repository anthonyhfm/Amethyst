package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.util.Timing

@Composable
fun TextDial(
    text: String,
    headline: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    onStartValueChange: (Float) -> Unit = { },
    onFinishValueChange: (Float) -> Unit = { },
    onResolveTextValue: (String) -> Unit,
    containerColor: Color = Color.Unspecified,
    dialColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    dev.anthonyhfm.amethyst.ui.components.TextDial(
        text = text,
        headline = headline,
        value = value,
        onValueChange = onValueChange,
        onStartValueChange = onStartValueChange,
        onFinishValueChange = onFinishValueChange,
        onResolveTextValue = onResolveTextValue,
        containerColor = containerColor,
        dialColor = dialColor,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun <T> StepDial(
    steps: List<T>,
    value: T,
    onStartValueChange: (T) -> Unit = { },
    onValueChange: (T) -> Unit,
    onFinishValueChange: (T) -> Unit,
    containerColor: Color = Color.Unspecified,
    dialColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    dev.anthonyhfm.amethyst.ui.components.StepDial(
        steps = steps,
        value = value,
        onStartValueChange = onStartValueChange,
        onValueChange = onValueChange,
        onFinishValueChange = onFinishValueChange,
        containerColor = containerColor,
        dialColor = dialColor,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun <T> StepTextDial(
    text: String,
    headline: String? = null,
    steps: List<T>,
    value: T,
    onValueChange: (T) -> Unit,
    onStartValueChange: (T) -> Unit = { },
    onFinishValueChange: (T) -> Unit = { },
    onResolveTextValue: (String) -> Unit,
    containerColor: Color = Color.Unspecified,
    dialColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    dev.anthonyhfm.amethyst.ui.components.StepTextDial(
        text = text,
        headline = headline,
        steps = steps,
        value = value,
        onValueChange = onValueChange,
        onStartValueChange = onStartValueChange,
        onFinishValueChange = onFinishValueChange,
        onResolveTextValue = onResolveTextValue,
        containerColor = containerColor,
        dialColor = dialColor,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun TimeDial(
    headline: String = "Duration",
    timing: Timing,
    onSelectTiming: (timing: Timing, msValue: Long) -> Unit,
    onStartValueChange: (timing: Timing, msValue: Long) -> Unit = { _, _ -> },
    onFinishValueChange: (timing: Timing, msValue: Long) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    text: String? = null,
) {
    dev.anthonyhfm.amethyst.ui.components.TimeDial(
        headline = headline,
        timing = timing,
        onSelectTiming = onSelectTiming,
        onStartValueChange = onStartValueChange,
        onFinishValueChange = onFinishValueChange,
        enabled = enabled,
        text = text,
    )
}

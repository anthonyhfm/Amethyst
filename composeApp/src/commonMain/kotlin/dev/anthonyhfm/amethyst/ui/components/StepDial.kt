package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

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
    enabled: Boolean = true
) {
    var selectedIndex by remember {
        mutableStateOf(steps.indexOf(value).coerceAtLeast(0))
    }
    var dialProgress by remember {
        mutableStateOf(progressForSelection(index = selectedIndex, size = steps.size))
    }

    LaunchedEffect(value, steps) {
        val index = steps.indexOf(value).coerceAtLeast(0)
        selectedIndex = index
        dialProgress = progressForSelection(index = index, size = steps.size)
    }

    LaunchedEffect(selectedIndex) {
        onValueChange(steps[selectedIndex])
    }

    DialSurface(
        progress = displayProgressForSelection(index = selectedIndex, size = steps.size),
        onDragStart = { onStartValueChange(steps[selectedIndex]) },
        onDragProgressChange = { newProgress ->
            dialProgress = newProgress

            val safeSize = (steps.size - 1).coerceAtLeast(1)
            val newIndex = if (steps.size <= 1) {
                0
            } else {
                (dialProgress * safeSize).roundToInt().coerceIn(0, steps.size - 1)
            }

            if (newIndex != selectedIndex) {
                selectedIndex = newIndex
            }
        },
        onDragEnd = { onFinishValueChange(steps[selectedIndex]) },
        containerColor = containerColor,
        dialColor = dialColor,
        modifier = modifier,
        enabled = enabled
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
    enabled: Boolean = true
) {
    EditableDialControl(
        text = text,
        headline = headline,
        enabled = enabled,
        modifier = modifier,
        onResolveTextValue = onResolveTextValue
    ) { dialModifier ->
        StepDial(
            steps = steps,
            value = value,
            onValueChange = onValueChange,
            onStartValueChange = onStartValueChange,
            onFinishValueChange = onFinishValueChange,
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = dialModifier,
            enabled = enabled
        )
    }
}

private fun progressForSelection(index: Int, size: Int): Float {
    val safeSize = (size - 1).coerceAtLeast(1)
    return if (size <= 1) 0f else index.toFloat() / safeSize.toFloat()
}

private fun displayProgressForSelection(index: Int, size: Int): Float {
    val safeSize = (size - 1).coerceAtLeast(1)
    return if (size <= 1) 1f else index.toFloat() / safeSize.toFloat()
}

package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

@Suppress("UNCHECKED_CAST")
@Composable
fun <T> FlatDial(
    type: DialType<T>,
    value: T,
    onValueChange: (T) -> Unit,
    onStartValueChange: (T) -> Unit = { },
    onFinishValueChange: (T) -> Unit = { },
    title: String? = null,
    text: String? = null,
    onResolveTextValue: ((String) -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    dialColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    defaultValue: T? = null,
) {
    when (type) {
        DialType.Continuous -> ContinuousFlatDial(
            value = value as Float,
            onStartValueChange = { onStartValueChange(it as T) },
            onValueChange = { onValueChange(it as T) },
            onFinishValueChange = { onFinishValueChange(it as T) },
            defaultValue = (defaultValue as? Float) ?: 0.5f,
            knob = false,
            title = title,
            text = text,
            onResolveTextValue = onResolveTextValue,
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = modifier,
            enabled = enabled,
        )

        DialType.Knob -> ContinuousFlatDial(
            value = value as Float,
            onStartValueChange = { onStartValueChange(it as T) },
            onValueChange = { onValueChange(it as T) },
            onFinishValueChange = { onFinishValueChange(it as T) },
            defaultValue = (defaultValue as? Float) ?: 0.5f,
            knob = true,
            title = title,
            text = text,
            onResolveTextValue = onResolveTextValue,
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = modifier,
            enabled = enabled,
        )

        is DialType.Steps<*> -> SteppedFlatDial(
            values = type.values as List<T>,
            value = value,
            onStartValueChange = onStartValueChange,
            onValueChange = onValueChange,
            onFinishValueChange = onFinishValueChange,
            defaultValue = defaultValue,
            title = title,
            text = text,
            onResolveTextValue = onResolveTextValue,
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = modifier,
            enabled = enabled,
        )
    }
}

@Composable
private fun ContinuousFlatDial(
    value: Float,
    onStartValueChange: (Float) -> Unit,
    onValueChange: (Float) -> Unit,
    onFinishValueChange: (Float) -> Unit,
    defaultValue: Float,
    knob: Boolean,
    title: String?,
    text: String?,
    onResolveTextValue: ((String) -> Unit)?,
    containerColor: Color,
    dialColor: Color,
    modifier: Modifier,
    enabled: Boolean,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dialValue by remember { mutableStateOf(value.coerceIn(0f, 1f)) }
    LaunchedEffect(value) {
        if (!isDragging) {
            dialValue = value.coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(dialValue) { onValueChange(dialValue) }

    FlatDialContent(title, text, enabled, modifier, onResolveTextValue) { dialModifier ->
        DialSurface(
            progress = dialValue,
            onDragStart = {
                isDragging = true
                onStartValueChange(dialValue)
            },
            onDragProgressChange = { dialValue = it },
            onDragEnd = {
                isDragging = false
                onFinishValueChange(dialValue)
            },
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = dialModifier,
            enabled = enabled,
            knob = knob,
            onDoubleClick = {
                dialValue = defaultValue
                onValueChange(defaultValue)
                onFinishValueChange(defaultValue)
            },
            onIncrement = {
                changeContinuousValue(dialValue, 0.01f, onStartValueChange) {
                    dialValue = it; onFinishValueChange(it)
                }
            },
            onDecrement = {
                changeContinuousValue(dialValue, -0.01f, onStartValueChange) {
                    dialValue = it; onFinishValueChange(it)
                }
            },
        )
    }
}

@Composable
private fun <T> SteppedFlatDial(
    values: List<T>,
    value: T,
    onStartValueChange: (T) -> Unit,
    onValueChange: (T) -> Unit,
    onFinishValueChange: (T) -> Unit,
    defaultValue: T?,
    title: String?,
    text: String?,
    onResolveTextValue: ((String) -> Unit)?,
    containerColor: Color,
    dialColor: Color,
    modifier: Modifier,
    enabled: Boolean,
) {
    var isDragging by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf(values.indexOf(value).coerceAtLeast(0)) }
    var progress by remember { mutableStateOf(progressForSelection(values.indexOf(value).coerceAtLeast(0), values.size)) }
    LaunchedEffect(value, values) {
        if (!isDragging) {
            index = values.indexOf(value).coerceAtLeast(0)
            progress = progressForSelection(index, values.size)
        }
    }
    LaunchedEffect(index) { onValueChange(values[index]) }
    FlatDialContent(title, text, enabled, modifier, onResolveTextValue) { dialModifier ->
        DialSurface(
            progress = displayProgressForSelection(index, values.size),
            onDragStart = {
                isDragging = true
                onStartValueChange(values[index])
            },
            onDragProgressChange = { newProgress ->
                progress = newProgress
                val next = if (values.size <= 1) 0 else (progress * (values.size - 1)).roundToInt()
                    .coerceIn(0, values.lastIndex)
                index = next
            },
            onDragEnd = {
                isDragging = false
                onFinishValueChange(values[index])
            },
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = dialModifier,
            enabled = enabled,
            onDoubleClick = {
                index = values.indexOf(defaultValue ?: values.first()).coerceAtLeast(0)
                progress = progressForSelection(index, values.size)
                onValueChange(values[index])
                onFinishValueChange(values[index])
            },
            onIncrement = {
                if (index < values.lastIndex) {
                    onStartValueChange(values[index])
                    index++
                    progress = progressForSelection(index, values.size)
                    onFinishValueChange(values[index])
                }
            },
            onDecrement = {
                if (index > 0) {
                    onStartValueChange(values[index])
                    index--
                    progress = progressForSelection(index, values.size)
                    onFinishValueChange(values[index])
                }
            },
        )
    }
}

@Composable
internal fun FlatDialContent(
    title: String?,
    text: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onResolveTextValue: ((String) -> Unit)?,
    dial: @Composable (Modifier) -> Unit,
) {
    if (text == null) {
        FlatDialControlFrame(title = title, dial = { dial(modifier) }, readout = {})
        return
    }

    var editing by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(text) }

    LaunchedEffect(text, editing) {
        if (!editing) {
            textValue = text
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            editing = false
        }
    }

    val beginEditing = {
        if (enabled) {
            textValue = text
            editing = true
        }
    }

    val editModifier = if (enabled) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { beginEditing() })
        }
    } else {
        Modifier
    }

    val submitTextValue = {
        onResolveTextValue?.invoke(textValue)
        editing = false
    }

    FlatDialControlFrame(
        title = title,
        dial = { dial(modifier) },
        readout = {
            if (editing) {
                DialReadoutEditor(
                    value = textValue,
                    onValueChange = { textValue = it },
                    onSubmit = submitTextValue,
                    onCancel = { editing = false },
                    enabled = enabled
                )
            } else {
                DialReadoutLabel(
                    text = text,
                    enabled = enabled,
                    modifier = editModifier
                )
            }
        }
    )
}

@Composable
internal fun FlatDialControlFrame(
    title: String?,
    dial: @Composable () -> Unit,
    readout: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dial()

        if (title != null || readout != {}) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                        textAlign = TextAlign.Start
                    )
                }

                readout()
            }
        }
    }
}

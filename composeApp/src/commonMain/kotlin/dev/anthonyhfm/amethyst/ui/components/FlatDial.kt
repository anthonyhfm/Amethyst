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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

import dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.devices.LocalChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.automationParameters
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.LocalCompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial

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
    automationParameter: AutomationParameter? = null,
    isAutomated: Boolean = false,
    hasAutomation: Boolean = false,
    isAutomatable: Boolean = true,
) {
    val chainDevice = LocalChainDevice.current
    val node = LocalCompositionNode.current
    val resolvedAutomationParameter = automationParameter ?: (chainDevice as? ParameterOwner)
        ?.parameterDescriptors
        ?.firstOrNull { descriptor -> descriptor.label == title && descriptor.automatable }
        ?.let { descriptor ->
            object : AutomationParameter {
                override val id = descriptor.id
                override val label = descriptor.label
            }
        }
    val accessibilityText = listOfNotNull(title, text).joinToString(": ")
    val accessibleModifier = if (accessibilityText.isNotBlank()) {
        modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibilityText
        }
    } else {
        modifier
    }

    if (isAutomatable && !isAutomated && resolvedAutomationParameter != null && chainDevice != null) {
        AutomatableDial(
            parameterId = resolvedAutomationParameter.id,
            automationParameter = resolvedAutomationParameter,
            type = type,
            value = value,
            defaultValue = defaultValue ?: value,
            title = title ?: resolvedAutomationParameter.label,
            text = text ?: "",
            onValueChange = onValueChange,
            onResolveTextValue = onResolveTextValue,
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = accessibleModifier,
            isFlat = true,
        )
        return
    }

    if (isAutomatable && !isAutomated && title != null && node != null) {
        val paramId = title.lowercase().replace(" ", "_")
        val hasNodeParam = node.automationParameters().any { it.id == paramId }
        if (hasNodeParam) {
            AutomatableDial(
                parameterId = paramId,
                type = type,
                value = value,
                defaultValue = defaultValue ?: value,
                title = title,
                text = text ?: "",
                onValueChange = onValueChange,
                onResolveTextValue = onResolveTextValue,
                containerColor = containerColor,
                dialColor = dialColor,
                modifier = accessibleModifier,
                isFlat = true,
            )
            return
        }
    }

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
            modifier = accessibleModifier,
            enabled = enabled,
            isAutomated = hasAutomation,
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
            modifier = accessibleModifier,
            enabled = enabled,
            isAutomated = hasAutomation,
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
            modifier = accessibleModifier,
            enabled = enabled,
            isAutomated = hasAutomation,
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
    isAutomated: Boolean = false,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value.coerceIn(0f, 1f)) }

    val currentProgress = if (isDragging) dragValue else value.coerceIn(0f, 1f)

    FlatDialContent(title, text, enabled, modifier, onResolveTextValue) { dialModifier ->
        DialSurface(
            progress = currentProgress,
            onDragStart = {
                isDragging = true
                dragValue = value.coerceIn(0f, 1f)
                onStartValueChange(dragValue)
            },
            onDragProgressChange = { newProgress ->
                dragValue = newProgress
                onValueChange(newProgress)
            },
            onDragEnd = {
                isDragging = false
                onFinishValueChange(dragValue)
            },
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = dialModifier,
            enabled = enabled,
            knob = knob,
            isAutomated = isAutomated,
            onDoubleClick = {
                isDragging = false
                dragValue = defaultValue.coerceIn(0f, 1f)
                onStartValueChange(currentProgress)
                onValueChange(defaultValue)
                onFinishValueChange(defaultValue)
            },
            onIncrement = {
                changeContinuousValue(currentProgress, 0.01f, onStartValueChange) { next ->
                    onValueChange(next)
                    onFinishValueChange(next)
                }
            },
            onDecrement = {
                changeContinuousValue(currentProgress, -0.01f, onStartValueChange) { next ->
                    onValueChange(next)
                    onFinishValueChange(next)
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
    isAutomated: Boolean = false,
) {
    var isDragging by remember { mutableStateOf(false) }
    val currentIndex = values.indexOf(value).coerceAtLeast(0)
    var dragIndex by remember { mutableStateOf(currentIndex) }
    var dragProgress by remember { mutableStateOf(progressForSelection(currentIndex, values.size)) }

    val effectiveIndex = if (isDragging) dragIndex else currentIndex
    val surfaceProgress = if (isDragging) dragProgress else displayProgressForSelection(currentIndex, values.size)

    FlatDialContent(title, text, enabled, modifier, onResolveTextValue) { dialModifier ->
        DialSurface(
            progress = surfaceProgress,
            onDragStart = {
                isDragging = true
                dragIndex = currentIndex
                dragProgress = progressForSelection(currentIndex, values.size)
                onStartValueChange(values[currentIndex])
            },
            onDragProgressChange = { newProgress ->
                dragProgress = newProgress
                val next = if (values.size <= 1) 0 else (newProgress * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex)
                if (next != dragIndex) {
                    dragIndex = next
                    onValueChange(values[next])
                }
            },
            onDragEnd = {
                isDragging = false
                onFinishValueChange(values[dragIndex])
            },
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = dialModifier,
            enabled = enabled,
            isAutomated = isAutomated,
            onDoubleClick = {
                val target = defaultValue ?: values.first()
                val targetIndex = values.indexOf(target).coerceAtLeast(0)
                isDragging = false
                dragIndex = targetIndex
                dragProgress = progressForSelection(targetIndex, values.size)
                onStartValueChange(values[effectiveIndex])
                onValueChange(target)
                onFinishValueChange(target)
            },
            onIncrement = {
                if (effectiveIndex < values.lastIndex) {
                    val next = values[effectiveIndex + 1]
                    onStartValueChange(values[effectiveIndex])
                    onValueChange(next)
                    onFinishValueChange(next)
                }
            },
            onDecrement = {
                if (effectiveIndex > 0) {
                    val next = values[effectiveIndex - 1]
                    onStartValueChange(values[effectiveIndex])
                    onValueChange(next)
                    onFinishValueChange(next)
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

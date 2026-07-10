package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.modifier.VerticalDrag
import dev.anthonyhfm.amethyst.ui.modifier.gesturesDisabled
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.ring
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.selectionBorder
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

internal val DialSurfaceSize = 52.dp
internal val DialReadoutWidth = 56.dp
private val DialReadoutHeight = 20.dp
private val DialOuterPadding = 6.dp
private val DialIndicatorInset = 8.dp
private val DialIndicatorWidth = 4.dp
private val DialIndicatorHeight = 10.dp
private const val DialDragFactor = 0.005f
private const val DialBoundaryEpsilon = 0.001f
private const val DialRotationStart = -148f
private const val DialRotationSweep = 296f
private const val DialArcStart = 122f
private const val DialArcSweep = 296f

@Composable
fun <T> Dial(
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
        DialType.Continuous -> ContinuousDial(
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

        DialType.Knob -> ContinuousDial(
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

        is DialType.Steps<*> -> SteppedDial(
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
private fun ContinuousDial(
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
    var dialValue by remember { mutableStateOf(value.coerceIn(0f, 1f)) }
    LaunchedEffect(value) { dialValue = value.coerceIn(0f, 1f) }
    LaunchedEffect(dialValue) { onValueChange(dialValue) }

    DialContent(title, text, enabled, modifier, onResolveTextValue) { dialModifier ->
        DialSurface(
            progress = dialValue,
            onDragStart = { onStartValueChange(dialValue) },
            onDragProgressChange = { dialValue = it },
            onDragEnd = { onFinishValueChange(dialValue) },
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

private fun changeContinuousValue(value: Float, delta: Float, onStart: (Float) -> Unit, onChanged: (Float) -> Unit) {
    val next = (value + delta).coerceIn(0f, 1f)
    if (next != value) {
        onStart(value); onChanged(next)
    }
}

@Composable
private fun <T> SteppedDial(
    values: List<T>, value: T, onStartValueChange: (T) -> Unit, onValueChange: (T) -> Unit,
    onFinishValueChange: (T) -> Unit, defaultValue: T?, title: String?, text: String?,
    onResolveTextValue: ((String) -> Unit)?, containerColor: Color, dialColor: Color,
    modifier: Modifier, enabled: Boolean,
) {
    var index by remember { mutableStateOf(values.indexOf(value).coerceAtLeast(0)) }
    var progress by remember { mutableStateOf(progressForSelection(index, values.size)) }
    LaunchedEffect(value, values) {
        index = values.indexOf(value).coerceAtLeast(0); progress = progressForSelection(index, values.size)
    }
    LaunchedEffect(index) { onValueChange(values[index]) }
    DialContent(title, text, enabled, modifier, onResolveTextValue) { dialModifier ->
        DialSurface(
            progress = displayProgressForSelection(index, values.size),
            onDragStart = { onStartValueChange(values[index]) },
            onDragProgressChange = { newProgress ->
                progress = newProgress
                val next = if (values.size <= 1) 0 else (progress * (values.size - 1)).roundToInt()
                    .coerceIn(0, values.lastIndex)
                index = next
            },
            onDragEnd = { onFinishValueChange(values[index]) },
            containerColor = containerColor, dialColor = dialColor, modifier = dialModifier, enabled = enabled,
            onDoubleClick = {
                index = values.indexOf(defaultValue ?: values.first()).coerceAtLeast(0)
                progress = progressForSelection(index, values.size)
                onValueChange(values[index]); onFinishValueChange(values[index])
            },
            onIncrement = {
                if (index < values.lastIndex) {
                    onStartValueChange(values[index]); index++; progress =
                        progressForSelection(index, values.size); onFinishValueChange(values[index])
                }
            },
            onDecrement = {
                if (index > 0) {
                    onStartValueChange(values[index]); index--; progress =
                        progressForSelection(index, values.size); onFinishValueChange(values[index])
                }
            },
        )
    }
}

private fun progressForSelection(index: Int, size: Int): Float = if (size <= 1) 0f else index.toFloat() / (size - 1)
private fun displayProgressForSelection(index: Int, size: Int): Float =
    if (size <= 1) 1f else index.toFloat() / (size - 1)

@Composable
internal fun DialSurface(
    progress: Float,
    onDragStart: () -> Unit,
    onDragProgressChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    containerColor: Color,
    dialColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    knob: Boolean = false,
    onDoubleClick: () -> Unit = { },
    onIncrement: (() -> Unit)? = null,
    onDecrement: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    val resolvedProgress = progress.coerceIn(0f, 1f)
    val currentProgress by rememberUpdatedState(resolvedProgress)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragProgressChange by rememberUpdatedState(onDragProgressChange)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnIncrement by rememberUpdatedState(onIncrement)
    val currentOnDecrement by rememberUpdatedState(onDecrement)
    val resolvedContainerColor = resolveContainerColor(containerColor)
    val focusedBorderColor = Theme[colors][selectionBorder]
    val focusedDialColor = Theme[colors][selectionSurface]
    val resolvedDialColor = if (isFocused) focusedDialColor else resolveDialColor(dialColor)
    val trackColor = Theme[colors][foreground].copy(alpha = 0.12f)
    val centerColor = Theme[colors][background]
    val outlineColor = Theme[colors][input]
    val indicatorColor = Theme[colors][foreground].copy(alpha = 0.9f)
    val innerOutlineColor = Theme[colors][foreground].copy(alpha = 0.06f)

    val borderColor = if (isFocused) Color.Transparent else outlineColor
    val borderWidth = 1.dp

    Box(
        modifier = modifier
            .gesturesDisabled(!enabled)
            .alpha(if (enabled) 1f else 0.45f)
            .size(DialSurfaceSize)
            .then(
                if (isFocused) {
                    Modifier.drawBehind {
                        val strokeWidth = 2.dp.toPx()
                        val outlineRadius = (size.minDimension / 2f) + (strokeWidth / 2f)
                        drawCircle(
                            color = focusedBorderColor,
                            radius = outlineRadius,
                            style = Stroke(width = strokeWidth)
                        )
                    }
                } else {
                    Modifier
                }
            )
            .shadow(1.dp, CircleShape)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.pointerHoverIcon(PointerIcon.VerticalDrag)
                } else {
                    Modifier
                }
            )
            .focusRequester(focusRequester)
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            currentOnIncrement?.invoke()
                            true
                        }

                        Key.DirectionDown -> {
                            currentOnDecrement?.invoke()
                            true
                        }

                        Key.Escape -> {
                            focusManager.clearFocus()
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    focusRequester.requestFocus()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { onDoubleClick() }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                var dragProgress = currentProgress

                detectDragGestures(
                    onDragStart = {
                        focusRequester.requestFocus()
                        dragProgress = currentProgress
                        currentOnDragStart()
                    },
                    onDrag = { _, offset ->
                        val raw = dragProgress + (offset.y * -1f) * DialDragFactor
                        dragProgress = when {
                            raw >= 1f - DialBoundaryEpsilon -> 1f
                            raw <= DialBoundaryEpsilon -> 0f
                            else -> raw.coerceIn(0f, 1f)
                        }
                        currentOnDragProgressChange(dragProgress)
                    },
                    onDragEnd = currentOnDragEnd,
                )
            }
            .background(resolvedContainerColor)
            .border(borderWidth, borderColor, CircleShape)
            .padding(DialOuterPadding)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.5.dp.toPx()
            val dialRadius = size.minDimension / 2f - strokeWidth / 2f
            val innerRadius = dialRadius - strokeWidth * 1.15f

            drawArc(
                color = trackColor,
                startAngle = if (knob) -90f else DialArcStart,
                sweepAngle = if (knob) 360f else DialArcSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = resolvedDialColor,
                startAngle = if (knob) -90f else DialArcStart,
                sweepAngle = (if (knob) 360f else DialArcSweep) * resolvedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawCircle(
                color = centerColor,
                radius = innerRadius.coerceAtLeast(0f),
                center = center
            )

            drawCircle(
                color = innerOutlineColor,
                radius = innerRadius.coerceAtLeast(0f),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .padding(DialIndicatorInset)
                .rotate(if (knob) resolvedProgress * 360f else DialRotationStart + (resolvedProgress * DialRotationSweep))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(CircleShape)
                    .width(DialIndicatorWidth)
                    .height(DialIndicatorHeight)
                    .background(indicatorColor)
            )
        }
    }
}

@Composable
internal fun DialContent(
    title: String?,
    text: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onResolveTextValue: ((String) -> Unit)?,
    dial: @Composable (Modifier) -> Unit,
) {
    if (text == null) {
        DialControlFrame(title = title, dial = { dial(modifier) }, readout = {})
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

    DialControlFrame(
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
internal fun DialControlFrame(
    title: String?,
    dial: @Composable () -> Unit,
    readout: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        title?.let {
            Text(
                text = it,
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
                textAlign = TextAlign.Center
            )
        }

        dial()
        readout()
    }
}

@Composable
private fun DialReadoutLabel(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.6f)
            .width(DialReadoutWidth)
            .height(DialReadoutHeight)
            .clip(SmallShape)
            .background(Theme[colors][background])
            .border(1.dp, Theme[colors][input], SmallShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = Theme[typography][small],
            color = Theme[colors][foreground],
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DialReadoutEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) Theme[colors][ring] else Theme[colors][input]
    val borderWidth = if (focused) 2.dp else 1.dp

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.6f)
            .focusRequester(focusRequester)
            .width(DialReadoutWidth)
            .height(DialReadoutHeight)
            .clip(SmallShape)
            .background(Theme[colors][background])
            .border(borderWidth, borderColor, SmallShape)
            .padding(horizontal = 6.dp)
            .onFocusChanged {
                WorkspaceRepository.isInputFocused = it.isFocused
            }
            .onKeyEvent {
                when (it.key) {
                    Key.Enter -> {
                        onSubmit()
                        true
                    }

                    Key.Escape -> {
                        onCancel()
                        true
                    }

                    else -> false
                }
            },
        enabled = enabled,
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(Theme[colors][foreground]),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Unspecified,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        textStyle = Theme[typography][small].copy(
            color = Theme[colors][foreground],
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                innerTextField()
            }
        }
    )
}

@Composable
internal fun resolveContainerColor(containerColor: Color): Color {
    return if (containerColor == Color.Unspecified) {
        Theme[colors][secondary]
    } else {
        containerColor
    }
}

@Composable
internal fun resolveDialColor(dialColor: Color): Color {
    return if (dialColor == Color.Unspecified) {
        Theme[colors][destructive]
    } else {
        dialColor
    }
}

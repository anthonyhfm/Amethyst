package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.ring
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

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
fun Dial(
    value: Float,
    onStartValueChange: (Float) -> Unit = { },
    onValueChange: (Float) -> Unit,
    onFinishValueChange: (Float) -> Unit,
    containerColor: Color = Color.Unspecified,
    dialColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var dialValue by remember { mutableStateOf(value.coerceIn(0f, 1f)) }

    LaunchedEffect(value) {
        dialValue = value.coerceIn(0f, 1f)
    }

    LaunchedEffect(dialValue) {
        onValueChange(dialValue)
    }

    DialSurface(
        progress = dialValue,
        onDragStart = { onStartValueChange(dialValue) },
        onDragProgressChange = { dialValue = it },
        onDragEnd = { onFinishValueChange(dialValue) },
        containerColor = containerColor,
        dialColor = dialColor,
        modifier = modifier,
        enabled = enabled
    )
}

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
    EditableDialControl(
        text = text,
        headline = headline,
        enabled = enabled,
        modifier = modifier,
        onResolveTextValue = onResolveTextValue
    ) { dialModifier ->
        Dial(
            value = value,
            onStartValueChange = onStartValueChange,
            onValueChange = onValueChange,
            onFinishValueChange = onFinishValueChange,
            containerColor = containerColor,
            dialColor = dialColor,
            modifier = dialModifier,
            enabled = enabled
        )
    }
}

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
) {
    val resolvedProgress = progress.coerceIn(0f, 1f)
    val currentProgress by rememberUpdatedState(resolvedProgress)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragProgressChange by rememberUpdatedState(onDragProgressChange)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val resolvedContainerColor = resolveContainerColor(containerColor)
    val resolvedDialColor = resolveDialColor(dialColor)
    val trackColor = Theme[colors][foreground].copy(alpha = 0.12f)
    val centerColor = Theme[colors][background]
    val outlineColor = Theme[colors][input]
    val indicatorColor = Theme[colors][foreground].copy(alpha = 0.9f)
    val innerOutlineColor = Theme[colors][foreground].copy(alpha = 0.06f)

    Box(
        modifier = modifier
            .gesturesDisabled(!enabled)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(1.dp, CircleShape)
            .clip(CircleShape)
            .size(DialSurfaceSize)
            .then(
                if (enabled) {
                    Modifier.pointerHoverIcon(PointerIcon.VerticalDrag)
                } else {
                    Modifier
                }
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                var dragProgress = currentProgress

                detectDragGestures(
                    onDragStart = {
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
            .border(1.dp, outlineColor, CircleShape)
            .padding(DialOuterPadding)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.5.dp.toPx()
            val dialRadius = size.minDimension / 2f - strokeWidth / 2f
            val innerRadius = dialRadius - strokeWidth * 1.15f

            drawArc(
                color = trackColor,
                startAngle = DialArcStart,
                sweepAngle = DialArcSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = resolvedDialColor,
                startAngle = DialArcStart,
                sweepAngle = DialArcSweep * resolvedProgress,
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
                .rotate(DialRotationStart + (resolvedProgress * DialRotationSweep))
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
internal fun EditableDialControl(
    text: String,
    headline: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onResolveTextValue: (String) -> Unit,
    dial: @Composable (Modifier) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(text) }
    val focusRequester = remember { FocusRequester() }

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

    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
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
        onResolveTextValue(textValue)
        editing = false
    }

    DialControlFrame(
        headline = headline,
        dial = { dial(modifier.then(editModifier)) },
        readout = {
            if (editing) {
                DialReadoutEditor(
                    value = textValue,
                    onValueChange = { textValue = it },
                    onSubmit = submitTextValue,
                    onCancel = { editing = false },
                    focusRequester = focusRequester,
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
    headline: String?,
    dial: @Composable () -> Unit,
    readout: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        headline?.let {
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
    focusRequester: FocusRequester,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) Theme[colors][ring] else Theme[colors][input]
    val borderWidth = if (focused) 2.dp else 1.dp

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
        Theme[colors][primary]
    } else {
        dialColor
    }
}

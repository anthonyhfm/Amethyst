package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.modifier.gesturesDisabled
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import kotlin.math.roundToInt

@Composable
fun <T> StepDial(
    steps: List<T>,
    value: T,
    onValueChange: (T) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(32.dp),
    dialColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var dialValue by remember { mutableStateOf(0f) }
    var displayValue by remember { mutableStateOf(0f) }
    var dragLock: Boolean by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (dragLock) return@LaunchedEffect

        dialValue = steps.indexOf(value).toFloat() / (steps.size - 1)
    }

    LaunchedEffect(dialValue) {
        val newValue = (dialValue * (steps.size - 1)).roundToInt()
        val newDisplayValue = newValue / (steps.size - 1).toFloat()

        if (displayValue != newDisplayValue) {
            displayValue = newDisplayValue
        }

        onValueChange(steps[newValue])
    }

    Box(
        modifier = modifier
            .gesturesDisabled(!enabled)
            .alpha(if (!enabled) 0.4f else 1f)
            .clip(CircleShape)
            .size(52.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragLock = true
                    },
                    onDragEnd = {
                        dragLock = false
                    }
                ) { input, offset ->
                    dialValue = (dialValue + (offset * -1) * 0.005f).coerceIn(0f, 1f)
                }
            }
            .background(containerColor)
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(48.dp), CircleShape)
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            val outerRadius = size.minDimension / 2f
            val innerRadius = outerRadius - strokeWidth
            val arcStart = 135f - 16f
            val arcSweep = 270f + 32f

            drawArc(
                color = Color.LightGray.copy(alpha = 0.2f),
                startAngle = arcStart,
                sweepAngle = arcSweep,
                useCenter = true
            )

            drawArc(
                color = dialColor,
                startAngle = arcStart,
                sweepAngle = arcSweep * displayValue,
                useCenter = true
            )

            drawCircle(
                color = containerColor,
                radius = innerRadius,
                center = center
            )
        }
    }
}

@Composable
fun <T> StepTextDial(
    text: String,
    headline: String? = null,
    steps: List<T>,
    value: T,
    onValueChange: (T) -> Unit,
    onResolveTextValue: (String) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(32.dp),
    dialColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var editing by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(text) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        headline?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall
            )
        }

        StepDial(
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            editing = !editing
                        }
                    )
                },
            steps = steps,
            value = value,
            containerColor = containerColor,
            dialColor = dialColor,
            onValueChange = {
                onValueChange(it)
            },
            enabled = enabled
        )

        if (!editing) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .width(52.dp)
                    .height(16.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            BasicTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .width(52.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .onKeyEvent {
                        if (it.key == Key.Enter) {
                            onResolveTextValue(textValue)
                            editing = false
                            true
                        }

                        false
                    },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Unspecified,
                    imeAction = ImeAction.Done
                ),
                textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

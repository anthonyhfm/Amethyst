package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable

@Composable
fun <T> StepDial(
    steps: List<T>,
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialValue by remember { mutableStateOf(0f) }
    var displayValue by remember { mutableStateOf(0f) }

    LaunchedEffect(value) {
        dialValue = steps.indexOf(value).toFloat() / (steps.size - 1)

        displayValue = (dialValue * (steps.size)) / (steps.size)
    }

    LaunchedEffect(dialValue) {
        onValueChange(steps[(dialValue * (steps.size - 1)).toInt()])
    }

    val background = MaterialTheme.colorScheme.surfaceColorAtElevation(32.dp)
    val dialColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .clip(CircleShape)
            .size(52.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { input, offset ->
                    dialValue = (dialValue + (offset * -1) * 0.01f).coerceIn(0f, 1f)
                }
            }
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(32.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(48.dp), CircleShape)
            .padding(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            drawArc(
                color = Color.LightGray.copy(0.2f),
                startAngle = 135f - 16f,
                sweepAngle = 270f + 32f,
                useCenter = true
            )

            drawArc(
                color = dialColor,
                startAngle = 135f - 16f,
                sweepAngle = (270f + 32f) * displayValue,
                useCenter = true
            )

            drawCircle(
                color = background,
                radius = 30f
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
    modifier: Modifier = Modifier
) {
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
            modifier = modifier,
            steps = steps,
            value = value,
            onValueChange = {
                onValueChange(it)
            }
        )

        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun Dial(
    value: Float,
    onValueChange: (Float) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(32.dp),
    dialColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
) {
    var dialValue by remember { mutableStateOf(value) }
    
    LaunchedEffect(value) {
        dialValue = value
    }

    LaunchedEffect(dialValue) {
        onValueChange(dialValue)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .size(52.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { input, offset ->
                    dialValue = (dialValue + (offset * -1) * 0.01f).coerceIn(0f, 1f)
                }
            }
            .background(containerColor)
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
                sweepAngle = (270f + 32f) * dialValue,
                useCenter = true
            )

            drawCircle(
                color = containerColor,
                radius = 30f
            )
        }
    }
}

@Composable
fun TextDial(
    text: String,
    headline: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(32.dp),
    dialColor: Color = MaterialTheme.colorScheme.tertiary,
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

        Dial(
            modifier = modifier,
            value = value,
            containerColor = containerColor,
            dialColor = dialColor,
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
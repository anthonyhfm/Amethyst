package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import kotlin.math.roundToInt

@Composable
fun RepeatsControl(
    repeats: Int,
    onRepeatsChange: (Int) -> Unit
) {
    val repeatsState = rememberUpdatedState(repeats)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Repeats",
            style = MaterialTheme.typography.labelLarge
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .size(64.dp, 36.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .rightClickable { onRepeatsChange(1) }
                .pointerInput(Unit) {
                    var accumulated = 0f
                    detectDragGestures(
                        onDragStart = { accumulated = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += -dragAmount.y / 10f
                            val delta = accumulated.toInt()
                            if (delta != 0) {
                                val newValue = (repeatsState.value + delta).coerceAtLeast(1)
                                if (newValue != repeatsState.value) {
                                    onRepeatsChange(newValue)
                                    accumulated -= delta
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = repeats.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Drag ↑ / ↓",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

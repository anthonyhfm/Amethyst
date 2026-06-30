package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

@Composable
fun RepeatsControl(
    repeats: Int,
    onRepeatsChange: (Int) -> Unit
) {
    val repeatsState = rememberUpdatedState(repeats)

    Column(
        modifier = Modifier
            .clip(DefaultShape)
            .width(220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Repeats",
            style = Theme[typography][small],
            color = Theme[colors][foreground],
        )

        Box(
            modifier = Modifier
                .clip(SmallShape)
                .size(56.dp, 32.dp)
                .background(Theme[colors][secondary])
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
                style = Theme[typography][small],
                color = Theme[colors][foreground],
            )
        }

        Text(
            text = "Drag ↑ / ↓",
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )
    }
}

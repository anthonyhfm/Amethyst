package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composeunstyled.Slider
import com.composeunstyled.SliderState
import com.composeunstyled.rememberSliderState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.components.DialEditPhase
import dev.anthonyhfm.amethyst.ui.components.LocalDialEditSession

@Composable
fun Slider(
    state: SliderState,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
) {
    val trackShape = RoundedCornerShape(50)

    Slider(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
        enabled = enabled,
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Theme[colors][secondary], trackShape)
            ) {
                val range = valueRange.endInclusive - valueRange.start
                val fraction = if (range > 0f) {
                    ((state.value - valueRange.start) / range).coerceIn(0f, 1f)
                } else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .background(Theme[colors][primary], trackShape)
                )
            }
        },
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .shadow(2.dp, CircleShape)
                    .background(Theme[colors][background], CircleShape)
                    .border(2.dp, Theme[colors][primary], CircleShape)
            )
        }
    )
}

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val editSession = LocalDialEditSession.current
    val state = rememberSliderState(
        initialValue = value,
        valueRange = valueRange,
        steps = steps,
    )
    state.value = value

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(state) {
        snapshotFlow { state.value }
            .collect { newValue ->
                if (newValue != currentValue) {
                    editSession?.dispatch(DialEditPhase.Preview) { currentOnValueChange(newValue) }
                        ?: currentOnValueChange(newValue)
                }
            }
    }

    Slider(
        state = state,
        modifier = modifier.then(
            if (editSession == null) Modifier else Modifier.pointerInput(editSession) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (waitForUpOrCancellation() != null) {
                        editSession.dispatch(DialEditPhase.Commit) { currentOnValueChange(state.value) }
                    }
                }
            }
        ),
        valueRange = valueRange,
        enabled = enabled,
    )
}

package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Slider
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

private val RangeSliderTouchTargetHeight: Dp = 44.dp
private val RangeSliderThumbRadius: Dp = 8.dp
private val RangeSliderTrackThickness: Dp = 6.dp

@Composable
internal fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Theme[typography][small], color = Theme[colors][mutedForeground])
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

/** A two-thumb slider whose active track represents the selected time interval. */
@Composable
internal fun LabeledRangeSlider(
    label: String,
    start: Float,
    end: Float,
    onRangeChange: (start: Float, end: Float) -> Unit,
) {
    val selectedStart = minOf(start, end).coerceIn(0f, 1f)
    val selectedEnd = maxOf(start, end).coerceIn(0f, 1f)
    val trackColor = Theme[colors][secondary]
    val activeTrackColor = Theme[colors][primary]
    val thumbColor = Theme[colors][background]
    val latestStart = rememberUpdatedState(selectedStart)
    val latestEnd = rememberUpdatedState(selectedEnd)
    val latestOnRangeChange = rememberUpdatedState(onRangeChange)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground]
            )

            Text(
                text = "${(selectedStart * 100).toInt()}% - ${(selectedEnd * 100).toInt()}%",
                style = Theme[typography][small],
                color = Theme[colors][foreground],
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(RangeSliderTouchTargetHeight)
                .semantics { contentDescription = "$label: ${(selectedStart * 100).toInt()}% to ${(selectedEnd * 100).toInt()}%" }
                .pointerInput(Unit) {
                    var draggingStart = true
                    detectDragGestures(
                        onDragStart = { offset ->
                            val thumbRadius = RangeSliderThumbRadius.toPx()
                            val trackStart = thumbRadius
                            val trackWidth = (size.width - thumbRadius * 2).coerceAtLeast(1f)
                            val startX = trackStart + latestStart.value * trackWidth
                            val endX = trackStart + latestEnd.value * trackWidth
                            draggingStart = kotlin.math.abs(offset.x - startX) <= kotlin.math.abs(offset.x - endX)
                        },
                        onDrag = { change, _ ->
                            val thumbRadius = RangeSliderThumbRadius.toPx()
                            val trackStart = thumbRadius
                            val trackWidth = (size.width - thumbRadius * 2).coerceAtLeast(1f)
                            val value = (kotlin.math.round(((change.position.x - trackStart) / trackWidth).coerceIn(0f, 1f) * 100f) / 100f)
                            val currentStart = latestStart.value
                            val currentEnd = latestEnd.value
                            if (draggingStart) latestOnRangeChange.value(value.coerceAtMost(currentEnd), currentEnd)
                            else latestOnRangeChange.value(currentStart, value.coerceAtLeast(currentStart))
                            change.consume()
                        },
                    )
                },
        ) {
            val thumbRadius = RangeSliderThumbRadius.toPx()
            val trackStart = thumbRadius
            val trackEnd = size.width - thumbRadius
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val centerY = size.height / 2f
            val startX = trackStart + selectedStart * trackWidth
            val endX = trackStart + selectedEnd * trackWidth
            val trackStroke = RangeSliderTrackThickness.toPx()

            drawLine(trackColor, Offset(trackStart, centerY), Offset(trackEnd, centerY), trackStroke, cap = StrokeCap.Round)
            drawLine(activeTrackColor, Offset(startX, centerY), Offset(endX, centerY), trackStroke, cap = StrokeCap.Round)

            listOf(startX, endX).forEach { x ->
                drawCircle(thumbColor, thumbRadius, Offset(x, centerY))
                drawCircle(activeTrackColor, thumbRadius, Offset(x, centerY), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

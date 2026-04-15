package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

@Composable
fun KeyframesPinchControl(
    pinch: Float,
    onPinchChange: (Float) -> Unit,
    bilateral: Boolean,
    onToggleBilateral: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(DefaultShape)
            .width(220.dp)
            .background(Theme[colors][card])
            .border(1.dp, Theme[colors][border], DefaultShape)
            .padding(horizontal = 12.dp)
            .padding(vertical = 8.dp)
            .clickable(
                indication = null,
                interactionSource = null,
                onClick = { }
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pinch",
            style = Theme[typography][small],
            color = Theme[colors][foreground],
        )

        PinchGraph(
            pinch = pinch,
            onPinchChange = onPinchChange,
            bilateral = bilateral,
            onToggleBilateral = onToggleBilateral,
            modifier = Modifier.size(64.dp)
        )

        val clampedPinch = pinch.coerceIn(-2f, 2f)
        val display = ((clampedPinch * 100f).roundToInt() / 100f).toString()

        Text(
            text = display,
            style = Theme[typography][small],
            color = Theme[colors][foreground],
        )

        Text(
            text = "Drag ↑ / ↓  |  Right-Click Mode",
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )
    }
}

@Composable
fun PinchGraph(
    pinch: Float,
    onPinchChange: (Float) -> Unit,
    bilateral: Boolean,
    onToggleBilateral: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedPinch = pinch.coerceIn(-2f, 2f)
    val surfaceHigh = Theme[colors][secondary]
    val curveColor = if (bilateral) Color(0xFFFF9800) else Color(0xFF3D6BFF)
    val pinchState = rememberUpdatedState(clampedPinch)

    Canvas(
        modifier = modifier
            .clip(DefaultShape)
            .background(surfaceHigh)
            .rightClickable {
                onPinchChange(0f)
                onToggleBilateral()
            }
            .pointerInput(Unit) {
                var baselinePinch = 0f
                var accumulated = 0f
                detectDragGestures(
                    onDragStart = {
                        baselinePinch = pinchState.value
                        accumulated = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val height = size.height
                        if (height > 0f) {
                            accumulated += (-dragAmount.y / height) * 4f
                            val newValue = (baselinePinch + accumulated).coerceIn(-2f, 2f)
                            if (newValue != pinchState.value) onPinchChange(newValue)
                        }
                    }
                )
            },
    ) {
        val pScaled = (clampedPinch / 2f).coerceIn(-1f, 1f)

        val w = size.width
        val h = size.height
        val margin = (w * 0.22f) // Relative margin
        val start = Offset(margin, h - margin)
        val end = Offset(w - margin, margin)
        val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)

        val strokeWidth = (w * 0.08f) // Relative stroke width

        val path = Path().apply {
            moveTo(start.x, start.y)
            if (!bilateral || pScaled == 0f) {
                val horizontalIntensity = 0.75f
                val verticalIntensity = 0.75f
                val controlX = mid.x - horizontalIntensity * mid.x * pScaled
                val controlY = mid.y - (verticalIntensity * mid.y * pScaled)
                quadraticTo(controlX, controlY, end.x, end.y)
            } else {
                val diag = end - start
                val len = kotlin.math.hypot(diag.x, diag.y).coerceAtLeast(1f)
                val norm = Offset(diag.x / len, diag.y / len)
                val perp = Offset(-norm.y, norm.x)

                val startMid = Offset((start.x + mid.x) / 2f, (start.y + mid.y) / 2f)
                val midEnd = Offset((mid.x + end.x) / 2f, (mid.y + end.y) / 2f)

                val halfLen = len / 2f
                val magnitude = halfLen * 0.5f * kotlin.math.abs(pScaled)
                val baseSign = if (pScaled >= 0f) 1f else -1f

                val control1 = startMid + perp * (-baseSign * magnitude)
                val control2 = midEnd + perp * (+baseSign * magnitude)

                quadraticTo(control1.x, control1.y, mid.x, mid.y)
                quadraticTo(control2.x, control2.y, end.x, end.y)
            }
        }

        drawPath(
            path = path,
            color = curveColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
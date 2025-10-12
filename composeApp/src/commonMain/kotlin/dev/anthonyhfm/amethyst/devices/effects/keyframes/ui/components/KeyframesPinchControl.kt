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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlin.math.roundToInt

@Composable
fun KeyframesPinchControl(
    pinch: Float,
    onPinchChange: (Float) -> Unit,
    bilateral: Boolean,
    onToggleBilateral: () -> Unit
) {
    val clampedPinch = pinch.coerceIn(-2f, 2f)

    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val curveColor = if (bilateral) Color(0xFFFF9800) else Color(0xFF3D6BFF)

    val pinchState = rememberUpdatedState(clampedPinch)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp)
            .padding(vertical = 8.dp)
            .clickable(
                indication = null,
                interactionSource = null,
                onClick = {  }
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pinch",
            style = MaterialTheme.typography.labelLarge.copy(
                lineHeight = MaterialTheme.typography.labelLarge.fontSize
            ),
        )

        Canvas(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .size(86.dp)
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
            val margin = 16.dp.toPx()
            val start = Offset(margin, h - margin)
            val end = Offset(w - margin, margin)
            val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)

            val strokeWidth = 6.dp.toPx()

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

        val display = ((clampedPinch * 100f).roundToInt() / 100f).toString()

        Text(
            text = display,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "Drag ↑ / ↓  |  Right-Click Mode",
            style = MaterialTheme.typography.labelSmall.copy(color = onSurface.copy(alpha = 0.6f))
        )
    }
}
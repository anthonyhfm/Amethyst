package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composeunstyled.Icon
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun WorkspaceDirectionPicker(
    parameterId: String,
    angleDegrees: Float,
    onAngleChange: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    AutomatableAngleControl(
        parameterId = parameterId,
        angleDegrees = angleDegrees,
        onAngleChange = onAngleChange,
        modifier = modifier
            .clip(DefaultShape)
            .background(Theme[colors][secondary]),
    ) {
        Icon(
            imageVector = Lucide.ArrowUp,
            contentDescription = contentDescription,
            tint = Theme[colors][foreground],
            modifier = Modifier
                .size(32.dp)
                .rotate(angleDegrees + 90f),
        )

        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val angleRadians = angleDegrees * PI / 180.0
            val distance = min(size.width, size.height) / 2f - 8.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = center + Offset(
                    x = cos(angleRadians).toFloat() * distance,
                    y = sin(angleRadians).toFloat() * distance,
                ),
            )
        }
    }
}

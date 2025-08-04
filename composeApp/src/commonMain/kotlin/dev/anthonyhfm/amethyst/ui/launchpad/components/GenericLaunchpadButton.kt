package dev.anthonyhfm.amethyst.ui.launchpad.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.InnerShadowPainter
import androidx.compose.ui.graphics.shadow.Shadow
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate

@Composable
fun GenericLaunchpadButton(
    effect: RawUpdate = RawUpdate(0, Color.Black),
    sizeModifier: Modifier,
    enableLightSpot: Boolean = true,
    shape: Shape = RoundedCornerShape(10)
) {
    Canvas(
        modifier = sizeModifier
            .clip(shape)
            .background(computeColor(effect))
    ) {
        if (enableLightSpot) {
            val buttonWidth = size.width
            val buttonHeight = size.height

            val gradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(0.1f),
                    Color.Transparent
                ),
                center = Offset(buttonWidth / 2f, buttonHeight / 2f),
                radius = size.minDimension / 2f
            )

            drawCircle(
                brush = gradient,
                radius = size.minDimension / 2f,
                center = Offset(buttonWidth / 2f, buttonHeight / 2f)
            )
        }
    }
}

private fun computeColor(effectData: RawUpdate): Color {
    val minComponent = 0x3C
    val maxComponent = 0xFF

    fun scaleColor(component: Float): Int {
        return (component * (maxComponent - minComponent) + minComponent).toInt()
    }

    val red = scaleColor(effectData.color.red)
    val green = scaleColor(effectData.color.green)
    val blue = scaleColor(effectData.color.blue)

    return Color(red, green, blue)
}
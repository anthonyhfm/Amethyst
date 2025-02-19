package dev.anthonyhfm.amethyst.ui.launchpad.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData

@Composable
fun GenericLaunchpadButton(
    effect: MidiEffectData = MidiEffectData(-1, -1, 0, 0, 0),
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
                center = Offset(buttonWidth / 2f, buttonHeight / 2f), // Zentrum des Kreises
                radius = size.minDimension / 2f
            )

            // Mittlerer, heller Spot mit radialem Fade
            drawCircle(
                brush = gradient,
                radius = size.minDimension / 2f,
                center = Offset(buttonWidth / 2f, buttonHeight / 2f) // In der Mitte der Buttonfläche
            )
        }
    }
}

private fun computeColor(effectData: MidiEffectData): Color {
    val minComponent = 0x50
    val maxComponent = 0xFF

    fun scaleColor(component: Int): Int {
        return ((component / 63f) * (maxComponent - minComponent) + minComponent).toInt()
    }

    val red = scaleColor(effectData.r)
    val green = scaleColor(effectData.g)
    val blue = scaleColor(effectData.b)

    return Color(red, green, blue)
}
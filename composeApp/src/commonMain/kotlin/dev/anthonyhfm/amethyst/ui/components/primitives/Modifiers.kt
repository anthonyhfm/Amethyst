package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DefaultShape = RoundedCornerShape(6.dp)
val SmallShape = RoundedCornerShape(4.dp)
val FullShape = RoundedCornerShape(50)

fun Modifier.hoverBackground(
    interactionSource: MutableInteractionSource,
    hoverColor: Color,
): Modifier = composed {
    val hovered by interactionSource.collectIsHoveredAsState()
    if (hovered) background(hoverColor) else this
}

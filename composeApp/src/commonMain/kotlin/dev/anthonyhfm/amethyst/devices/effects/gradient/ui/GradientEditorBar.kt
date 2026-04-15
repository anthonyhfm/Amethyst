package dev.anthonyhfm.amethyst.devices.effects.gradient.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientSmoothness
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuRadioItem
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.background as themeBackground
import dev.anthonyhfm.amethyst.ui.theme.border as themeBorder
import dev.anthonyhfm.amethyst.ui.theme.colors as themeColors
import dev.anthonyhfm.amethyst.ui.theme.input as themeInput
import dev.anthonyhfm.amethyst.ui.theme.primary as themePrimary

@Composable
fun GradientEditorBar(
    selectedColor: String?,
    onSelectionChange: (String?) -> Unit,
    colors: List<GradientChainDeviceState.GradientColor>,
    onGradientDataEmit: (List<GradientChainDeviceState.GradientColor>) -> Unit,
    onAddGradientPoint: (position: Float) -> Unit,
    onGradientDragStart: () -> Unit,
    onGradientDragFinish: () -> Unit,
    onSmoothnessChange: (String, GradientSmoothness) -> Unit,
) {
    val density = LocalDensity.current

    // Positions getrennt von Device-State für flüssiges Dragging
    val positionStates = remember(colors.map { it.selectionUUID }) {
        colors.associate { it.selectionUUID to mutableStateOf(it.position) }
    }

    Box(
        modifier = Modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Theme[themeColors][themeInput])
                    .border(1.dp, Theme[themeColors][themeBorder], RoundedCornerShape(6.dp))
                    .padding(4.dp)
                    .rightClickable { offset ->
                        val position = offset.x / constraints.maxWidth.toFloat()
                        val clampedPosition = position.coerceIn(0f, 1f)
                        onAddGradientPoint(clampedPosition)
                    }
            ) {
                val sortedColors = colors.map { c ->
                    val p = positionStates[c.selectionUUID]?.value ?: c.position
                    c.copy(position = p)
                }.sortedBy { it.position }

                if (sortedColors.size < 2) return@Canvas

                val visualSteps = 200
                val stepWidth = size.width / visualSteps

                for (step in 0..visualSteps) {
                    val progress = step.toDouble() / visualSteps

                    var segmentIndex = 0
                    for (i in 0 until sortedColors.size - 1) {
                        if (progress >= sortedColors[i].position && progress <= sortedColors[i + 1].position) {
                            segmentIndex = i
                            break
                        }
                    }

                    val startColor = sortedColors[segmentIndex]
                    val endColor = sortedColors.getOrNull(segmentIndex + 1) ?: sortedColors.last()
                    val smoothness = startColor.smoothness

                    val segmentStart = startColor.position.toDouble()
                    val segmentEnd = endColor.position.toDouble()
                    val segmentDuration = segmentEnd - segmentStart

                    val linearT = if (segmentDuration > 0.0001) {
                        ((progress - segmentStart) / segmentDuration).coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }

                    val easedT = when (smoothness) {
                        GradientSmoothness.Linear -> linearT
                        GradientSmoothness.Hold -> {
                            if (linearT < 0.95) 0.0 else 1.0
                        }
                        GradientSmoothness.Release -> {
                            if (linearT > 0.05) 1.0 else 0.0
                        }
                        GradientSmoothness.Fast -> {
                            kotlin.math.sqrt(linearT)
                        }
                        GradientSmoothness.Slow -> {
                            1.0 - kotlin.math.sqrt(1.0 - linearT)
                        }
                        GradientSmoothness.Sharp -> {
                            if (linearT < 0.5) {
                                0.5 - kotlin.math.sqrt(0.5 - linearT) / kotlin.math.sqrt(2.0)
                            } else {
                                0.5 + kotlin.math.sqrt(linearT - 0.5) / kotlin.math.sqrt(2.0)
                            }
                        }
                        GradientSmoothness.Smooth -> {
                            if (linearT < 0.5) {
                                kotlin.math.sqrt(linearT / 2.0)
                            } else {
                                1.0 - kotlin.math.sqrt((1.0 - linearT) / 2.0)
                            }
                        }
                    }

                    val color = Color(
                        red = (startColor.r + (endColor.r - startColor.r) * easedT.toFloat()).coerceIn(0f, 1f),
                        green = (startColor.g + (endColor.g - startColor.g) * easedT.toFloat()).coerceIn(0f, 1f),
                        blue = (startColor.b + (endColor.b - startColor.b) * easedT.toFloat()).coerceIn(0f, 1f)
                    )

                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(step * stepWidth, 0f),
                        size = androidx.compose.ui.geometry.Size(stepWidth + 1f, size.height)
                    )
                }
            }

            colors.forEach { color ->
                var pos by positionStates.getValue(color.selectionUUID)

                LaunchedEffect(color.position) {
                    if (color.position != pos) {
                        pos = color.position
                    }
                }

                ContextMenu(
                    modifier = Modifier
                        .offset(x = -6.dp, y = -5.dp)
                        .offset(x = maxWidth * pos)
                        .scale(if (selectedColor == color.selectionUUID) 1.1f else 1f)
                        .shadow(
                            elevation = if (selectedColor == color.selectionUUID) 16.dp else 6.dp,
                            shape = CircleShape
                        ),
                    trigger = {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .height(38.dp)
                                .width(12.dp)
                                .background(Color(color.r, color.g, color.b))
                                .border(
                                    width = if (selectedColor == color.selectionUUID) 2.dp else 1.dp,
                                    color = if (selectedColor == color.selectionUUID) Theme[themeColors][themePrimary]
                                    else Theme[themeColors][themeBackground],
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (selectedColor == color.selectionUUID) {
                                        onSelectionChange(null)
                                    } else {
                                        onSelectionChange(color.selectionUUID)
                                    }
                                }
                                .pointerInput(color.selectionUUID) {
                                    detectDragGestures(
                                        onDragStart = {
                                            onGradientDragStart()
                                        },
                                        onDrag = { input, offset ->
                                            input.consume()
                                            val pct = (offset.x / density.density).dp / maxWidth
                                            val newPos = (pos + pct).coerceIn(0f, 1f)
                                            pos = newPos
                                        },
                                        onDragEnd = {
                                            val committed = colors.map { c ->
                                                val p = positionStates[c.selectionUUID]?.value ?: c.position
                                                GradientChainDeviceState.GradientColor(
                                                    position = p,
                                                    r = c.r,
                                                    g = c.g,
                                                    b = c.b,
                                                    smoothness = c.smoothness,
                                                    selectionUUID = c.selectionUUID
                                                )
                                            }
                                            onGradientDataEmit(committed)
                                            onGradientDragFinish()
                                        },
                                        onDragCancel = {
                                            val committed = colors.map { c ->
                                                val p = positionStates[c.selectionUUID]?.value ?: c.position
                                                GradientChainDeviceState.GradientColor(
                                                    position = p,
                                                    r = c.r,
                                                    g = c.g,
                                                    b = c.b,
                                                    smoothness = c.smoothness,
                                                    selectionUUID = c.selectionUUID
                                                )
                                            }
                                            onGradientDataEmit(committed)
                                            onGradientDragFinish()
                                        }
                                    )
                                }
                        )
                    },
                ) {
                    GradientSmoothness.entries.forEach { smoothness ->
                        ContextMenuRadioItem(
                            selected = smoothness == color.smoothness,
                            onClick = {
                                onSmoothnessChange(color.selectionUUID, smoothness)
                            },
                        ) {
                            Text(smoothness.name)
                        }
                    }
                }
            }
        }
    }
}

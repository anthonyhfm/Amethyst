package dev.anthonyhfm.amethyst.timeline.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientSmoothness
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuRadioItem
import dev.anthonyhfm.amethyst.ui.theme.background as themeBackground
import dev.anthonyhfm.amethyst.ui.theme.border as themeBorder
import dev.anthonyhfm.amethyst.ui.theme.colors as themeColors
import dev.anthonyhfm.amethyst.ui.theme.input as themeInput
import dev.anthonyhfm.amethyst.ui.theme.primary as themePrimary

@Composable
fun NoteGradientEditorBar(
    selectedStopUUID: String?,
    onSelectionChange: (String?) -> Unit,
    stops: List<NoteGradientStop>,
    onStopMoved: (uuid: String, newPosition: Float) -> Unit,
    onAddStop: (position: Float) -> Unit,
    onDeleteStop: (uuid: String) -> Unit,
    onSmoothnessChange: (uuid: String, smoothness: GradientSmoothness) -> Unit,
    onDragStart: () -> Unit,
    onDragFinish: () -> Unit,
) {
    val density = LocalDensity.current

    val positionStates = remember(stops.map { it.selectionUUID }) {
        stops.associate { it.selectionUUID to mutableStateOf(it.position) }
    }

    Box {
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
                    .pointerInput(onAddStop, constraints.maxWidth) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val position = offset.x / constraints.maxWidth.toFloat()
                                onAddStop(position.coerceIn(0f, 1f))
                            }
                        )
                    }
            ) {
                val sortedStops = stops.map { s ->
                    val p = positionStates[s.selectionUUID]?.value ?: s.position
                    s.copy(position = p)
                }.sortedBy { it.position }

                if (sortedStops.size < 2) return@Canvas

                val visualSteps = 200
                val stepWidth = size.width / visualSteps

                for (step in 0..visualSteps) {
                    val progress = step.toFloat() / visualSteps
                    val (r, g, b) = GradientInterpolator.interpolate(sortedStops, progress)
                    val color = Color(r, g, b)

                    drawRect(
                        color = color,
                        topLeft = Offset(step * stepWidth, 0f),
                        size = Size(stepWidth + 1f, size.height)
                    )
                }
            }

            stops.forEach { stop ->
                var pos by positionStates.getValue(stop.selectionUUID)

                LaunchedEffect(stop.position) {
                    if (stop.position != pos) {
                        pos = stop.position
                    }
                }

                ContextMenu(
                    modifier = Modifier
                        .offset(x = -6.dp, y = -5.dp)
                        .offset(x = maxWidth * pos)
                        .scale(if (selectedStopUUID == stop.selectionUUID) 1.1f else 1f)
                        .shadow(
                            elevation = if (selectedStopUUID == stop.selectionUUID) 16.dp else 6.dp,
                            shape = CircleShape
                        ),
                    trigger = {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .height(38.dp)
                                .width(12.dp)
                                .background(Color(stop.r, stop.g, stop.b))
                                .border(
                                    width = if (selectedStopUUID == stop.selectionUUID) 2.dp else 1.dp,
                                    color = if (selectedStopUUID == stop.selectionUUID) Theme[themeColors][themePrimary]
                                    else Theme[themeColors][themeBackground],
                                    shape = CircleShape
                                )
                                .pointerInput(stop.selectionUUID, selectedStopUUID) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val touchSlop = viewConfiguration.touchSlop
                                        var totalAbsDeltaX = 0f
                                        var isDragging = false

                                        loop@ while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break@loop
                                            if (!change.pressed) break@loop

                                            val deltaX = (change.position - change.previousPosition).x

                                            if (!isDragging) {
                                                totalAbsDeltaX += kotlin.math.abs(deltaX)
                                                if (totalAbsDeltaX > touchSlop) {
                                                    isDragging = true
                                                    onDragStart()
                                                }
                                            }

                                            if (isDragging) {
                                                change.consume()
                                                val pct = (deltaX / density.density).dp / maxWidth
                                                val newPos = (pos + pct).coerceIn(0f, 1f)
                                                pos = newPos
                                                onStopMoved(stop.selectionUUID, newPos)
                                            }
                                        }

                                        if (isDragging) {
                                            onDragFinish()
                                        } else {
                                            onSelectionChange(stop.selectionUUID)
                                        }
                                    }
                                }
                        )
                    },
                ) {
                    GradientSmoothness.entries.forEach { smoothness ->
                        ContextMenuRadioItem(
                            selected = smoothness == stop.smoothness,
                            onClick = {
                                onSmoothnessChange(stop.selectionUUID, smoothness)
                            },
                        ) {
                            Text(smoothness.name)
                        }
                    }
                    ContextMenuItem(
                        enabled = stops.size > 2,
                        variant = ContextMenuItemVariant.Destructive,
                        onClick = {
                            onDeleteStop(stop.selectionUUID)
                        },
                    ) {
                        Text(stringResource(Res.string.timeline_gradient_editor_action_delete))
                    }
                }
            }
        }
    }
}

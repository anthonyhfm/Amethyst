package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import kotlin.math.atan2
import kotlin.math.PI
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.LocalCompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.LocalAutomationHandler
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem as PrimitiveContextMenuItem
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.lane
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.automationParameters
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.LocalNodeChangeCallbacks
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial as PrimitiveDial
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min
import kotlin.math.pow
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.LinePoint
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.LabeledSlider as PrimitiveLabeledSlider
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.LabeledRangeSlider as PrimitiveLabeledRangeSlider

@Composable
fun <T> AutomatableDial(
    parameterId: String,
    type: DialType<T>,
    value: T,
    defaultValue: T,
    title: String,
    text: String,
    onValueChange: (T) -> Unit,
    onResolveTextValue: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val node = LocalCompositionNode.current
    val onAutomationAction = LocalAutomationHandler.current
    val nodeChangeCallbacks = LocalNodeChangeCallbacks.current
    val parameter = if (node != null) {
        node.automationParameters().firstOrNull { it.id == parameterId }
    } else {
        null
    }

    @Composable
    fun RenderDial() {
        PrimitiveDial(
            type = type,
            value = value,
            defaultValue = defaultValue,
            title = title,
            text = text,
            onStartValueChange = { nodeChangeCallbacks.onStart() },
            onValueChange = onValueChange,
            onFinishValueChange = { nodeChangeCallbacks.onFinish() },
            onResolveTextValue = onResolveTextValue,
            modifier = modifier,
        )
    }

    if (parameter != null && onAutomationAction != null) {
        val automated = node?.lane(parameter.id) != null
        ContextMenu(
            trigger = { RenderDial() }
        ) {
            AutomatableContextMenuItem(
                label = if (automated) "Edit ${parameter.label} Automation" else "Automate ${parameter.label}",
                icon = if (automated) Lucide.Pencil else Lucide.Plus,
                onClick = { onAutomationAction(parameter.id, automated, false) }
            )
            if (automated) {
                AutomatableContextMenuItem(
                    label = "Remove ${parameter.label} Automation",
                    icon = Lucide.Trash2,
                    onClick = { onAutomationAction(parameter.id, true, true) }
                )
            }
        }
    } else {
        RenderDial()
    }
}

@Composable
fun AutomatableSlider(
    parameterId: String,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val node = LocalCompositionNode.current
    val onAutomationAction = LocalAutomationHandler.current
    val parameter = if (node != null) {
        node.automationParameters().firstOrNull { it.id == parameterId }
    } else {
        null
    }

    @Composable
    fun RenderSlider() {
        PrimitiveLabeledSlider(
            label = label,
            value = value,
            range = range,
            onValueChange = onValueChange,
        )
    }

    if (parameter != null && onAutomationAction != null) {
        val automated = node?.lane(parameter.id) != null
        ContextMenu(
            modifier = modifier,
            trigger = { RenderSlider() }
        ) {
            AutomatableContextMenuItem(
                label = if (automated) "Edit ${parameter.label} Automation" else "Automate ${parameter.label}",
                icon = if (automated) Lucide.Pencil else Lucide.Plus,
                onClick = { onAutomationAction(parameter.id, automated, false) }
            )
            if (automated) {
                AutomatableContextMenuItem(
                    label = "Remove ${parameter.label} Automation",
                    icon = Lucide.Trash2,
                    onClick = { onAutomationAction(parameter.id, true, true) }
                )
            }
        }
    } else {
        RenderSlider()
    }
}

@Composable
fun AutomatableRangeSlider(
    startParameterId: String,
    endParameterId: String,
    label: String,
    start: Float,
    end: Float,
    onRangeChange: (start: Float, end: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val node = LocalCompositionNode.current
    val onAutomationAction = LocalAutomationHandler.current
    val rangeParameters = if (node != null) {
        node.automationParameters().filter {
            it.id == startParameterId || it.id == endParameterId
        }
    } else {
        emptyList()
    }

    @Composable
    fun RenderRangeSlider() {
        PrimitiveLabeledRangeSlider(
            label = label,
            start = start,
            end = end,
            onRangeChange = onRangeChange,
        )
    }

    if (rangeParameters.isNotEmpty() && onAutomationAction != null) {
        ContextMenu(
            modifier = modifier,
            trigger = { RenderRangeSlider() }
        ) {
            rangeParameters.forEach { parameter ->
                val automated = node?.lane(parameter.id) != null
                AutomatableContextMenuItem(
                    label = if (automated) "Edit ${parameter.label} Automation" else "Automate ${parameter.label}",
                    icon = if (automated) Lucide.Pencil else Lucide.Plus,
                    onClick = { onAutomationAction(parameter.id, automated, false) }
                )
                if (automated) {
                    AutomatableContextMenuItem(
                        label = "Remove ${parameter.label} Automation",
                        icon = Lucide.Trash2,
                        onClick = { onAutomationAction(parameter.id, true, true) }
                    )
                }
            }
        }
    } else {
        RenderRangeSlider()
    }
}

@Composable
fun AutomatableWorkspaceOriginSelector(
    originXParameterId: String,
    originYParameterId: String,
    originX: Float,
    originY: Float,
    bounds: Pair<IntOffset, IntSize>,
    onOriginChange: (Offset, IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val node = LocalCompositionNode.current
    val onAutomationAction = LocalAutomationHandler.current
    val originParameters = if (node != null) {
        node.automationParameters().filter {
            it.id == originXParameterId || it.id == originYParameterId
        }
    } else {
        emptyList()
    }

    @Composable
    fun RenderSelector(selectorModifier: Modifier) {
        WorkspaceOriginSelector(
            originX = originX,
            originY = originY,
            bounds = bounds,
            onOriginChange = onOriginChange,
            modifier = selectorModifier,
        )
    }

    if (originParameters.isNotEmpty() && onAutomationAction != null) {
        ContextMenu(
            modifier = modifier,
            trigger = { RenderSelector(Modifier.fillMaxSize()) }
        ) {
            originParameters.forEach { parameter ->
                val automated = node?.lane(parameter.id) != null
                AutomatableContextMenuItem(
                    label = if (automated) "Edit ${parameter.label} Automation" else "Automate ${parameter.label}",
                    icon = if (automated) Lucide.Pencil else Lucide.Plus,
                    onClick = { onAutomationAction(parameter.id, automated, false) }
                )
                if (automated) {
                    AutomatableContextMenuItem(
                        label = "Remove ${parameter.label} Automation",
                        icon = Lucide.Trash2,
                        onClick = { onAutomationAction(parameter.id, true, true) }
                    )
                }
            }
        }
    } else {
        RenderSelector(modifier)
    }
}

@Composable
fun AutomatableWorkspaceLineSelector(
    points: List<LinePoint>,
    selectedIndex: Int,
    bounds: Pair<IntOffset, IntSize>,
    onPointsChange: (newPoints: List<LinePoint>, newSelectedIndex: Int) -> Unit,
    onSelectPoint: (index: Int) -> Unit,
    onDeletePoint: (() -> Unit)? = null,
    startXParameterId: String = "start-x",
    startYParameterId: String = "start-y",
    endXParameterId: String = "end-x",
    endYParameterId: String = "end-y",
    modifier: Modifier = Modifier,
) {
    val node = LocalCompositionNode.current
    val onAutomationAction = LocalAutomationHandler.current
    val aspectRatio = bounds.second.width.toFloat() / bounds.second.height.coerceAtLeast(1).toFloat()

    val isStartPoint = selectedIndex == 0
    val isEndPoint = selectedIndex == points.size - 1

    val activeParameterIds = when {
        isStartPoint -> listOf(startXParameterId, startYParameterId)
        isEndPoint -> listOf(endXParameterId, endYParameterId)
        else -> emptyList()
    }

    val lineParameters = if (node != null && activeParameterIds.isNotEmpty()) {
        node.automationParameters().filter {
            it.id in activeParameterIds
        }
    } else {
        emptyList()
    }

    @Composable
    fun RenderSelector(selectorModifier: Modifier) {
        WorkspaceLineSelector(
            points = points,
            selectedIndex = selectedIndex,
            bounds = bounds,
            onPointsChange = onPointsChange,
            onSelectPoint = onSelectPoint,
            modifier = selectorModifier,
        )
    }

    fun handleRightClick(position: Offset, containerSize: IntSize) {
        if (points.isEmpty() || containerSize.width <= 0 || containerSize.height <= 0) return
        val maxWidthPx = containerSize.width.toFloat()
        val maxHeightPx = containerSize.height.toFloat()
        val widthPx = min(maxWidthPx, maxHeightPx * aspectRatio)
        val heightPx = widthPx / aspectRatio
        val offsetX = (maxWidthPx - widthPx) / 2f
        val offsetY = (maxHeightPx - heightPx) / 2f

        val localX = position.x - offsetX
        val localY = position.y - offsetY

        var minDistanceSq = Float.MAX_VALUE
        var closestIdx = 0
        points.forEachIndexed { index, point ->
            val px = Offset(point.x.coerceIn(0f, 1f) * widthPx, point.y.coerceIn(0f, 1f) * heightPx)
            val distSq = (localX - px.x).pow(2) + (localY - px.y).pow(2)
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestIdx = index
            }
        }
        onSelectPoint(closestIdx)
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val containerSize = IntSize(containerWidthPx.toInt(), containerHeightPx.toInt())

        ContextMenu(
            modifier = Modifier.fillMaxSize(),
            onRightClick = { position ->
                handleRightClick(position, containerSize)
            },
            trigger = { RenderSelector(Modifier.fillMaxSize()) }
        ) {
            if (onDeletePoint != null && points.size > 2) {
                AutomatableContextMenuItem(
                    label = "Delete Selected Point",
                    icon = Lucide.Trash2,
                    onClick = onDeletePoint,
                )
            }
            lineParameters.forEach { parameter ->
                val automated = node?.lane(parameter.id) != null
                AutomatableContextMenuItem(
                    label = if (automated) "Edit ${parameter.label} Automation" else "Automate ${parameter.label}",
                    icon = if (automated) Lucide.Pencil else Lucide.Plus,
                    onClick = { onAutomationAction?.invoke(parameter.id, automated, false) }
                )
                if (automated) {
                    AutomatableContextMenuItem(
                        label = "Remove ${parameter.label} Automation",
                        icon = Lucide.Trash2,
                        onClick = { onAutomationAction?.invoke(parameter.id, true, true) }
                    )
                }
            }
        }
    }
}



@Composable
fun AutomatableAngleControl(
    parameterId: String,
    angleDegrees: Float,
    onAngleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val node = LocalCompositionNode.current
    val onAutomationAction = LocalAutomationHandler.current
    val parameter = if (node != null) {
        node.automationParameters().firstOrNull { it.id == parameterId }
    } else {
        null
    }

    @Composable
    fun RenderControl(controlModifier: Modifier) {
        val onAngle = rememberUpdatedState { point: Offset, size: IntSize ->
            if (size.width > 0 && size.height > 0) {
                onAngleChange((atan2(point.y - size.height / 2f, point.x - size.width / 2f) * 180.0 / PI).toFloat())
            }
        }
        Box(
            modifier = controlModifier
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { position ->
                            onAngle.value(position, size)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            onAngle.value(position, size)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onAngle.value(change.position, size)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }

    if (parameter != null && onAutomationAction != null) {
        val automated = node?.lane(parameter.id) != null
        ContextMenu(
            modifier = modifier,
            trigger = { RenderControl(Modifier.fillMaxSize()) }
        ) {
            AutomatableContextMenuItem(
                label = if (automated) "Edit ${parameter.label} Automation" else "Automate ${parameter.label}",
                icon = if (automated) Lucide.Pencil else Lucide.Plus,
                onClick = { onAutomationAction(parameter.id, automated, false) }
            )
            if (automated) {
                AutomatableContextMenuItem(
                    label = "Remove ${parameter.label} Automation",
                    icon = Lucide.Trash2,
                    onClick = { onAutomationAction(parameter.id, true, true) }
                )
            }
        }
    } else {
        RenderControl(modifier)
    }
}

@Composable
fun AutomatableContextMenuItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    PrimitiveContextMenuItem(
        onClick = onClick,
        enabled = enabled,
        dismissOnClick = true,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled) Theme[colors][popoverForeground] else Theme[colors][mutedForeground],
            )
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun AutomatableSymmetryControl(
    modeParameterId: String = "mode",
    axisParameterId: String = "axis",
    anchorParameterId: String = "anchor",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val node = LocalCompositionNode.current
    val onAutomationAction = LocalAutomationHandler.current
    val symmetryParameters = if (node != null) {
        node.automationParameters().filter {
            it.id == modeParameterId || it.id == axisParameterId || it.id == anchorParameterId
        }
    } else {
        emptyList()
    }

    if (symmetryParameters.isNotEmpty() && onAutomationAction != null) {
        ContextMenu(
            modifier = modifier,
            trigger = { content() }
        ) {
            symmetryParameters.forEach { parameter ->
                val automated = node?.lane(parameter.id) != null
                AutomatableContextMenuItem(
                    label = if (automated) "Edit ${parameter.label} Automation" else "Automate ${parameter.label}",
                    icon = if (automated) Lucide.Pencil else Lucide.Plus,
                    onClick = { onAutomationAction(parameter.id, automated, false) }
                )
                if (automated) {
                    AutomatableContextMenuItem(
                        label = "Remove ${parameter.label} Automation",
                        icon = Lucide.Trash2,
                        onClick = { onAutomationAction(parameter.id, true, true) }
                    )
                }
            }
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

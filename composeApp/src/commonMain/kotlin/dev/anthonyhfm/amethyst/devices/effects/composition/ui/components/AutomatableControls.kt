package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
    fun RenderSelector() {
        WorkspaceOriginSelector(
            originX = originX,
            originY = originY,
            bounds = bounds,
            onOriginChange = onOriginChange,
            modifier = modifier,
        )
    }

    if (originParameters.isNotEmpty() && onAutomationAction != null) {
        ContextMenu(
            trigger = { RenderSelector() }
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
        RenderSelector()
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
    fun RenderControl() {
        val onAngle = rememberUpdatedState { point: Offset, size: IntSize ->
            if (size.width > 0 && size.height > 0) {
                onAngleChange((atan2(point.y - size.height / 2f, point.x - size.width / 2f) * 180.0 / PI).toFloat())
            }
        }
        Box(
            modifier = modifier
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
            trigger = { RenderControl() }
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
        RenderControl()
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

package dev.anthonyhfm.amethyst.ui.components.automation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationRetriggerMode
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationTimingUnit
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.CompositionAutomationPoint
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.segmentValueAt
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.chart2
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

private class DialAbovePositionProvider(
    private val density: Density,
    private val yOffsetDp: Dp = 10.dp,
    private val onPositionCalculated: (relativeArrowXPx: Int) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val yOffsetPx = with(density) { yOffsetDp.roundToPx() }
        val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
        val popupX = (anchorCenterX - popupContentSize.width / 2)
            .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))

        val preferredY = anchorBounds.top - popupContentSize.height + yOffsetPx
        val popupY = if (preferredY < 8) {
            anchorBounds.bottom - yOffsetPx
        } else {
            preferredY
        }

        val relativeArrowX = (anchorCenterX - popupX).coerceIn(16, popupContentSize.width - 16)
        onPositionCalculated(relativeArrowX)

        return IntOffset(popupX, popupY)
    }
}

private sealed interface AutomationDragTarget {
    data class Point(val id: String) : AutomationDragTarget
    data class Handle(val id: String, val incoming: Boolean) : AutomationDragTarget
}

@Composable
fun DialAutomationPopover(
    expanded: Boolean,
    parameter: dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter,
    lane: DialAutomationLane,
    onUpdateLane: (DialAutomationLane) -> Unit,
    onRemoveAutomation: () -> Unit,
    onDismissRequest: () -> Unit,
    trigger: @Composable () -> Unit,
) {
    var selectedPointId by remember { mutableStateOf<String?>(null) }
    var dragStartPoints by remember { mutableStateOf<List<CompositionAutomationPoint>?>(null) }
    var relativeArrowXPx by remember { mutableStateOf(247) }
    val density = LocalDensity.current

    Box {
        trigger()

        if (expanded) {
            Popup(
                popupPositionProvider = DialAbovePositionProvider(density = density, yOffsetDp = 10.dp, onPositionCalculated = { relativeArrowXPx = it }),
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                val rawPopoverBg = Theme[colors][popover]
                val borderCol = Theme[colors][border]
                val isDark = rawPopoverBg.red < 0.5f

                val popoverBg = if (isDark) {
                    Color(0xFF141822)
                } else {
                    rawPopoverBg
                }
                val popoverBorder = if (isDark) {
                    Color(0xFF3B475D)
                } else {
                    borderCol
                }
                val editorPanelBg = if (isDark) {
                    Color(0xFF0D111A)
                } else {
                    Theme[colors][secondary]
                }
                val editorPanelBorder = if (isDark) {
                    Color(0xFF263042)
                } else {
                    borderCol.copy(alpha = 0.6f)
                }
                val dividerColor = if (isDark) {
                    Color(0xFF1F2837)
                } else {
                    borderCol.copy(alpha = 0.3f)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(495.dp)
                ) {
                    // Main Content Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color.Black.copy(alpha = 0.6f),
                                spotColor = Color.Black.copy(alpha = 0.8f)
                            )
                            .background(popoverBg, RoundedCornerShape(12.dp))
                            .border(1.dp, popoverBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Theme[colors][primary], RoundedCornerShape(50))
                                    )
                                    Text(
                                        text = parameter.label,
                                        style = Theme[typography][small],
                                        fontWeight = FontWeight.SemiBold,
                                        color = Theme[colors][foreground],
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Automation",
                                        style = Theme[typography][small],
                                        color = Theme[colors][mutedForeground]
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    UnstyledButton(
                                        onClick = onRemoveAutomation,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(Theme[colors][secondary], RoundedCornerShape(6.dp))
                                    ) {
                                        Icon(
                                            imageVector = Lucide.Trash2,
                                            contentDescription = "Remove Automation",
                                            tint = Theme[colors][mutedForeground],
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    UnstyledButton(
                                        onClick = onDismissRequest,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(Theme[colors][secondary], RoundedCornerShape(6.dp))
                                    ) {
                                        Text(
                                            text = "✕",
                                            style = Theme[typography][small],
                                            color = Theme[colors][popoverForeground]
                                        )
                                    }
                                }
                            }

                            // Curve Editor Canvas with Left Legend Sidebar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(editorPanelBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, editorPanelBorder, RoundedCornerShape(8.dp))
                            ) {
                                AutomationValueLegend(
                                    parameter = parameter,
                                    editorPanelBg = editorPanelBg,
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .fillMaxHeight()
                                )

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(dividerColor)
                                )

                                 AutomationCanvas(
                                    points = lane.points,
                                    parameter = parameter,
                                    selectedPointId = selectedPointId,
                                    panelBgColor = editorPanelBg,
                                    onSelect = { id ->
                                        selectedPointId = id
                                        dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager.selectDialAutomationPoints(lane.parameterId, listOfNotNull(id))
                                    },
                                    onAdd = { p, value ->
                                        val before = lane.points
                                        val after = before + CompositionAutomationPoint(p, value)
                                        onUpdateLane(lane.copy(points = after))
                                        dev.anthonyhfm.amethyst.core.controls.undo.UndoManager.addAction(
                                            dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction.DialAutomationPointChange(
                                                parameterId = lane.parameterId,
                                                beforePoints = before,
                                                afterPoints = after,
                                                onUpdatePoints = { newPoints -> onUpdateLane(lane.copy(points = newPoints)) }
                                            )
                                        )
                                    },
                                    onMove = { id, p, value ->
                                        if (dragStartPoints == null) {
                                            dragStartPoints = lane.points
                                        }
                                        onUpdateLane(
                                            lane.copy(points = lane.points.map {
                                                if (it.pointId == id) it.copy(progress = p, value = value) else it
                                            })
                                        )
                                    },
                                    onMoveHandle = { id, incoming, time, value ->
                                        if (dragStartPoints == null) {
                                            dragStartPoints = lane.points
                                        }
                                        onUpdateLane(
                                            lane.copy(points = lane.points.map { point ->
                                                if (point.pointId != id) point
                                                else if (incoming) point.copy(
                                                    inHandleTime = time, inHandleValue = value,
                                                    outHandleTime = 1f - time, outHandleValue = (2f * point.value - value).coerceIn(-1f, 1f),
                                                ) else point.copy(
                                                    outHandleTime = time, outHandleValue = value,
                                                    inHandleTime = 1f - time, inHandleValue = (2f * point.value - value).coerceIn(-1f, 1f),
                                                )
                                            })
                                        )
                                    },
                                    onDragFinished = {
                                        val before = dragStartPoints
                                        val after = lane.points
                                        if (before != null && before != after) {
                                            dev.anthonyhfm.amethyst.core.controls.undo.UndoManager.addAction(
                                                dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction.DialAutomationPointChange(
                                                    parameterId = lane.parameterId,
                                                    beforePoints = before,
                                                    afterPoints = after,
                                                    onUpdatePoints = { newPoints -> onUpdateLane(lane.copy(points = newPoints)) }
                                                )
                                            )
                                        }
                                        dragStartPoints = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }

                            // Controls Row: TimingControl + GateControl + Retrigger Mode Tabs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentTiming = when (lane.settings.timingUnit) {
                                    DialAutomationTimingUnit.Milliseconds -> Timing.Duration(lane.settings.durationValue.toLong().milliseconds)
                                    DialAutomationTimingUnit.Beats -> {
                                        val beatsFactor = lane.settings.durationValue / 4.0f
                                        val rythm = Timing.Rythm.RythmTiming.entries.find { kotlin.math.abs(it.factor - beatsFactor) < 0.001f }
                                            ?: Timing.Rythm.RythmTiming._1_4
                                        Timing.Rythm(rythm)
                                    }
                                }

                                TimingControl(
                                    timing = currentTiming,
                                    onTimingChange = { newTiming ->
                                        val newSettings = when (newTiming) {
                                            is Timing.Duration -> lane.settings.copy(
                                                timingUnit = DialAutomationTimingUnit.Milliseconds,
                                                durationValue = newTiming.duration.inWholeMilliseconds.toFloat()
                                            )
                                            is Timing.Rythm -> lane.settings.copy(
                                                timingUnit = DialAutomationTimingUnit.Beats,
                                                durationValue = newTiming.timing.factor * 4.0f
                                            )
                                        }
                                        onUpdateLane(lane.copy(settings = newSettings))
                                    },
                                )

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(borderCol.copy(alpha = 0.6f)),
                                )

                                GateControl(
                                    gate = lane.settings.gate,
                                    onGateChange = { newGate ->
                                        onUpdateLane(lane.copy(settings = lane.settings.copy(gate = newGate)))
                                    },
                                )

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(borderCol.copy(alpha = 0.6f)),
                                )

                                // Retrigger Mode Tabs (shadcn style)
                                val isGated = lane.settings.retriggerMode == DialAutomationRetriggerMode.GatedOneShot
                                val selectedTabKey = if (isGated) "Gated" else "Trigger"
                                Tabs(
                                    selectedTab = selectedTabKey,
                                    tabs = listOf("Gated", "Trigger"),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    TabsList(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    ) {
                                        TabsTrigger(
                                            key = "Gated",
                                            selected = isGated,
                                            onSelected = {
                                                if (!isGated) onUpdateLane(lane.copy(settings = lane.settings.copy(retriggerMode = DialAutomationRetriggerMode.GatedOneShot)))
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                text = "Gated",
                                                style = Theme[typography][small],
                                                fontWeight = if (isGated) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                            )
                                        }

                                        TabsTrigger(
                                            key = "Trigger",
                                            selected = !isGated,
                                            onSelected = {
                                                if (isGated) onUpdateLane(lane.copy(settings = lane.settings.copy(retriggerMode = DialAutomationRetriggerMode.AlwaysRetrigger)))
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                text = "Trigger",
                                                style = Theme[typography][small],
                                                fontWeight = if (!isGated) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pointer Arrow pointing down to Dial (dynamically aligned to relativeArrowXPx)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .offset(y = (-2).dp)
                    ) {
                        val density = LocalDensity.current
                        val arrowXDp = with(density) { (relativeArrowXPx - 8).toDp() }
                        Canvas(
                            modifier = Modifier
                                .offset(x = arrowXDp)
                                .size(16.dp, 8.dp)
                        ) {
                            val fillPath = Path().apply {
                                moveTo(-1f, -6f)
                                lineTo(size.width + 1f, -6f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            drawPath(fillPath, popoverBg)
                            drawLine(popoverBorder, Offset(0f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
                            drawLine(popoverBorder, Offset(size.width / 2f, size.height), Offset(size.width, 0f), 1.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationCanvas(
    points: List<CompositionAutomationPoint>,
    parameter: dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter? = null,
    selectedPointId: String?,
    panelBgColor: Color = Theme[colors][secondary],
    onSelect: (String?) -> Unit,
    onAdd: (Float, Float) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onMoveHandle: (String, Boolean, Float, Float) -> Unit,
    onDragFinished: () -> Unit = {},
    modifier: Modifier,
) {
    var draggedTarget by remember { mutableStateOf<AutomationDragTarget?>(null) }
    val currentPoints = rememberUpdatedState(points)
    val currentSelectedPointId = rememberUpdatedState(selectedPointId)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val currentOnAdd = rememberUpdatedState(onAdd)
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnMoveHandle = rememberUpdatedState(onMoveHandle)
    val currentOnDragFinished = rememberUpdatedState(onDragFinished)
    val surfaceColor = panelBgColor
    val mutedColor = Theme[colors][mutedForeground]
    val curveAccentColor = Theme[colors][chart2]

    fun progressToX(p: Float, width: Float): Float = p.coerceIn(0f, 1f) * width

    fun valueToY(v: Float, height: Float): Float = (1f - (v + 1f) * 0.5f) * height

    fun xToProgress(x: Float, width: Float): Float = (x / width).coerceIn(0f, 1f)

    fun yToValue(y: Float, height: Float): Float = (1f - (y / height).coerceIn(0f, 1f) * 2f).coerceIn(-1f, 1f)

    fun pointAt(position: Offset, width: Float, height: Float): CompositionAutomationPoint? =
        currentPoints.value.minByOrNull {
            val x = progressToX(it.progress, width)
            val y = valueToY(it.value, height)
            (Offset(x, y) - position).getDistance()
        }?.takeIf {
            val x = progressToX(it.progress, width)
            val y = valueToY(it.value, height)
            (Offset(x, y) - position).getDistance() < 22f
        }

    fun handlePosition(point: CompositionAutomationPoint, incoming: Boolean, width: Float, height: Float): Offset? {
        val ordered = currentPoints.value.sortedBy(CompositionAutomationPoint::progress)
        val index = ordered.indexOfFirst { it.pointId == point.pointId }
        val neighbour = if (incoming) ordered.getOrNull(index - 1) else ordered.getOrNull(index + 1)
        if (neighbour == null) return null
        val fraction = if (incoming) point.inHandleTime ?: (2f / 3f) else point.outHandleTime ?: (1f / 3f)
        val p = if (incoming) neighbour.progress + (point.progress - neighbour.progress) * fraction else point.progress + (neighbour.progress - point.progress) * fraction
        val linearValue = if (incoming) neighbour.value + (point.value - neighbour.value) * (2f / 3f) else point.value + (neighbour.value - point.value) / 3f
        val v = if (incoming) point.inHandleValue ?: linearValue else point.outHandleValue ?: linearValue
        return Offset(progressToX(p, width), valueToY(v, height))
    }

    fun handleAt(position: Offset, width: Float, height: Float): AutomationDragTarget.Handle? {
        val selected = currentPoints.value.firstOrNull { it.pointId == currentSelectedPointId.value } ?: return null
        return listOf(true, false).firstNotNullOfOrNull { incoming ->
            handlePosition(selected, incoming, width, height)?.takeIf { (it - position).getDistance() < 22f }
                ?.let { AutomationDragTarget.Handle(selected.pointId, incoming) }
        }
    }

    Canvas(
        modifier = modifier
            .background(surfaceColor, DefaultShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { position ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val selectedId = handleAt(position, width, height)?.id
                            ?: pointAt(position, width, height)?.pointId
                        currentOnSelect.value(selectedId)
                        tryAwaitRelease()
                    },
                    onDoubleTap = { p ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        var v = yToValue(p.y, height)
                        parameter?.snapPoints?.let { snaps ->
                            val closest = snaps.minByOrNull { kotlin.math.abs(it.normalizedValue - v) }
                            if (closest != null && kotlin.math.abs(closest.normalizedValue - v) < parameter.snapThreshold * 2f) {
                                v = closest.normalizedValue
                            }
                        }
                        currentOnAdd.value(xToProgress(p.x, width), v)
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        draggedTarget = handleAt(p, width, height) ?: pointAt(p, width, height)?.let { AutomationDragTarget.Point(it.pointId) }
                        (draggedTarget as? AutomationDragTarget.Point)?.let { currentOnSelect.value(it.id) }
                    },
                    onDrag = { change, _ ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val time = xToProgress(change.position.x, width)
                        var value = yToValue(change.position.y, height)
                        parameter?.snapPoints?.let { snaps ->
                            val closest = snaps.minByOrNull { kotlin.math.abs(it.normalizedValue - value) }
                            if (closest != null && kotlin.math.abs(closest.normalizedValue - value) < parameter.snapThreshold * 2f) {
                                value = closest.normalizedValue
                            }
                        }
                        when (val target = draggedTarget) {
                            is AutomationDragTarget.Point -> currentOnMove.value(target.id, time, value)
                            is AutomationDragTarget.Handle -> {
                                val point = currentPoints.value.firstOrNull { it.pointId == target.id } ?: return@detectDragGestures
                                val ordered = currentPoints.value.sortedBy(CompositionAutomationPoint::progress)
                                val index = ordered.indexOfFirst { it.pointId == point.pointId }
                                val neighbour = (if (target.incoming) ordered.getOrNull(index - 1) else ordered.getOrNull(index + 1)) ?: return@detectDragGestures
                                val fraction = if (target.incoming) ((time - neighbour.progress) / (point.progress - neighbour.progress).coerceAtLeast(.0001f))
                                else ((time - point.progress) / (neighbour.progress - point.progress).coerceAtLeast(.0001f))
                                currentOnMoveHandle.value(target.id, target.incoming, fraction.coerceIn(.05f, .95f), value)
                            }
                            null -> Unit
                        }
                    },
                    onDragEnd = { draggedTarget = null; currentOnDragFinished.value() },
                    onDragCancel = { draggedTarget = null; currentOnDragFinished.value() }
                )
            },
    ) {
        val baselineY = when (parameter?.curveMode) {
            dev.anthonyhfm.amethyst.core.controls.automation.CurveMode.Bipolar -> valueToY(0f, size.height)
            dev.anthonyhfm.amethyst.core.controls.automation.CurveMode.Unipolar, null -> valueToY(-1f, size.height)
        }

        listOf(.25f, .5f, .75f).forEach { p ->
            val gridX = progressToX(p, size.width)
            drawLine(mutedColor.copy(alpha = .18f), Offset(gridX, 0f), Offset(gridX, size.height), 1.dp.toPx())
        }

        parameter?.snapPoints?.forEach { snap ->
            val snapY = valueToY(snap.normalizedValue, size.height)
            drawLine(
                color = mutedColor.copy(alpha = 0.35f),
                start = Offset(0f, snapY),
                end = Offset(size.width, snapY),
                strokeWidth = 1.dp.toPx()
            )
        }

        val sorted = points.sortedBy(CompositionAutomationPoint::progress)
        if (sorted.size > 1) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val curvePoints = buildList {
                sorted.zipWithNext().forEach { (start, end) ->
                    (0..24).forEach { step ->
                        if (isEmpty() || step > 0) {
                            val p = step / 24f
                            add(
                                Offset(
                                    progressToX(start.progress + (end.progress - start.progress) * p, canvasWidth),
                                    valueToY(start.segmentValueAt(end, p), canvasHeight)
                                )
                            )
                        }
                    }
                }
            }
            val strokePath = Path().apply {
                curvePoints.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(curvePoints.last().x, baselineY)
                lineTo(curvePoints.first().x, baselineY)
                close()
            }
            drawPath(fillPath, curveAccentColor.copy(alpha = .14f), style = Fill)
            drawPath(strokePath, curveAccentColor, style = Stroke(2.5.dp.toPx()))
        }

        val zeroY = if (parameter?.curveMode == dev.anthonyhfm.amethyst.core.controls.automation.CurveMode.Bipolar) valueToY(0f, size.height) else valueToY(-1f, size.height)
        val zeroAlpha = if (parameter?.curveMode == dev.anthonyhfm.amethyst.core.controls.automation.CurveMode.Bipolar) 0.5f else 0.2f
        drawLine(mutedColor.copy(alpha = zeroAlpha), Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())

        fun Offset.clampInside(radius: Float): Offset = Offset(
            x.coerceIn(radius, size.width - radius),
            y.coerceIn(radius, size.height - radius)
        )

        sorted.forEach { point ->
            val center = Offset(progressToX(point.progress, size.width), valueToY(point.value, size.height))
            if (point.pointId == selectedPointId) {
                listOf(true, false).forEach { incoming ->
                    handlePosition(point, incoming, size.width, size.height)?.let { handle ->
                        val clampedHandle = handle.clampInside(6.dp.toPx())
                        val clampedCenterForLine = center.clampInside(6.dp.toPx())
                        drawLine(mutedColor.copy(alpha = .8f), clampedCenterForLine, clampedHandle, 1.5.dp.toPx())
                        drawCircle(surfaceColor, 6.dp.toPx(), clampedHandle)
                        drawCircle(curveAccentColor, 6.dp.toPx(), clampedHandle, style = Stroke(2.dp.toPx()))
                    }
                }
                val clampedCenter = center.clampInside(8.dp.toPx())
                drawCircle(surfaceColor, 8.dp.toPx(), clampedCenter)
                drawCircle(Color.White, 6.dp.toPx(), clampedCenter)
                drawCircle(curveAccentColor, 6.dp.toPx(), clampedCenter, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(curveAccentColor, 5.dp.toPx(), center.clampInside(5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun TimingControl(
    timing: Timing,
    onTimingChange: (Timing) -> Unit,
) {
    val timingState = rememberUpdatedState(timing)

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Duration",
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )
        CompactStepControl(
            value = timing.displayText(),
            onDecrease = {
                val current = timingState.value
                val next = when (current) {
                    is Timing.Rythm -> {
                        val entries = Timing.Rythm.RythmTiming.entries
                        val index = entries.indexOf(current.timing).takeIf { it >= 0 } ?: entries.indexOf(Timing.Rythm.RythmTiming._1_4)
                        Timing.Rythm(entries[(index - 1).coerceIn(0, entries.lastIndex)])
                    }
                    is Timing.Duration -> {
                        val nextMs = (current.duration.inWholeMilliseconds - 25L).coerceIn(25L, 10_000L)
                        Timing.Duration(nextMs.milliseconds)
                    }
                }
                if (next != current) onTimingChange(next)
            },
            onIncrease = {
                val current = timingState.value
                val next = when (current) {
                    is Timing.Rythm -> {
                        val entries = Timing.Rythm.RythmTiming.entries
                        val index = entries.indexOf(current.timing).takeIf { it >= 0 } ?: entries.indexOf(Timing.Rythm.RythmTiming._1_4)
                        Timing.Rythm(entries[(index + 1).coerceIn(0, entries.lastIndex)])
                    }
                    is Timing.Duration -> Timing.Duration((current.duration.inWholeMilliseconds + 25L).coerceIn(25L, 10_000L).milliseconds)
                }
                if (next != current) onTimingChange(next)
            },
            onDragSteps = { steps ->
                val current = timingState.value
                val next = when (current) {
                    is Timing.Rythm -> {
                        val entries = Timing.Rythm.RythmTiming.entries
                        val index = entries.indexOf(current.timing).takeIf { it >= 0 } ?: entries.indexOf(Timing.Rythm.RythmTiming._1_4)
                        Timing.Rythm(entries[(index + steps).coerceIn(0, entries.lastIndex)])
                    }
                    is Timing.Duration -> Timing.Duration((current.duration.inWholeMilliseconds + steps * 25L).coerceIn(25L, 10_000L).milliseconds)
                }
                if (next != current) onTimingChange(next)
            },
            onToggleUnit = {
                val current = timingState.value
                val next = when (current) {
                    is Timing.Rythm -> Timing.Duration(500L.milliseconds)
                    is Timing.Duration -> Timing.Rythm(Timing.Rythm.RythmTiming._1_4)
                }
                onTimingChange(next)
            }
        )
    }
}

@Composable
private fun GateControl(
    gate: Float,
    onGateChange: (Float) -> Unit,
) {
    val gateState = rememberUpdatedState(gate)

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Gate",
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )
        CompactStepControl(
            value = "${(gate * 200).roundToInt()}%",
            onDecrease = {
                val nextPercent = ((gateState.value * 200).roundToInt() - 5).coerceIn(0, 200)
                val nextGate = nextPercent / 200f
                if (nextGate != gateState.value) onGateChange(nextGate)
            },
            onIncrease = {
                val nextPercent = ((gateState.value * 200).roundToInt() + 5).coerceIn(0, 200)
                val nextGate = nextPercent / 200f
                if (nextGate != gateState.value) onGateChange(nextGate)
            },
            onDragSteps = { steps ->
                val nextPercent = ((gateState.value * 200).roundToInt() + steps * 5).coerceIn(0, 200)
                val nextGate = nextPercent / 200f
                if (nextGate != gateState.value) onGateChange(nextGate)
            },
            onToggleUnit = {
                onGateChange(0.5f)
            }
        )
    }
}

private fun Timing.displayText(): String = when (this) {
    is Timing.Rythm -> timing.text
    is Timing.Duration -> "${duration.inWholeMilliseconds} ms"
}

@Composable
private fun CompactStepControl(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDragSteps: (Int) -> Unit,
    onToggleUnit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .width(88.dp)
            .clip(DefaultShape)
            .background(Theme[colors][secondary])
            .border(1.dp, Theme[colors][border].copy(alpha = 0.6f), DefaultShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepIconButton(Icons.Default.Remove, "Decrease Duration", onDecrease)
        Box(
            modifier = Modifier
                .weight(1f)
                .rightClickable { onToggleUnit() }
                .pointerInput(Unit) {
                    var accumulated = 0f
                    detectDragGestures(
                        onDragStart = { accumulated = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += -dragAmount.y / 8f
                            val steps = accumulated.toInt()
                            if (steps != 0) {
                                onDragSteps(steps)
                                accumulated -= steps
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = Theme[typography][small],
                color = Theme[colors][foreground],
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        StepIconButton(Icons.Default.Add, "Increase Duration", onIncrease)
    }
}

@Composable
private fun StepIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier
            .size(24.dp)
            .padding(2.dp),
    ) {
        MaterialIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Theme[colors][foreground],
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun AutomationValueLegend(
    parameter: dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter?,
    editorPanelBg: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val vertPaddingDp = 12.dp
    val vertPaddingPx = with(density) { vertPaddingDp.toPx() }
    val textColor = Theme[colors][mutedForeground]

    val snapPoints = parameter?.snapPoints
    val itemsToDraw: List<Pair<Float, String>> = if (!snapPoints.isNullOrEmpty()) {
        snapPoints.map { snap ->
            val label = snap.label ?: formatValueLabel(snap.normalizedValue, parameter)
            snap.normalizedValue to label
        }
    } else {
        val values = listOf(1f, 0f, -1f)
        values.map { v -> v to formatValueLabel(v, parameter) }
    }

    BoxWithConstraints(
        modifier = modifier
            .wrapContentWidth()
            .background(editorPanelBg.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp)
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        val usableHeightPx = (heightPx - 2 * vertPaddingPx).coerceAtLeast(1f)

        fun valueToY(v: Float): Dp {
            val yPx = vertPaddingPx + (1f - (v + 1f) * 0.5f) * usableHeightPx
            return with(density) { yPx.toDp() }
        }

        itemsToDraw.forEach { (v, labelText) ->
            val yDp = valueToY(v)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = yDp - 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelText,
                    style = Theme[typography][small],
                    color = textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatValueLabel(
    normalizedValue: Float,
    parameter: dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter?
): String {
    if (parameter == null) {
        val pct = ((normalizedValue + 1f) * 50f).roundToInt()
        return "$pct%"
    }
    val mode = parameter.curveMode
    val unit = parameter.unit ?: ""
    val range = parameter.displayRange
    val val0to1 = (normalizedValue + 1f) * 0.5f
    val actual = range.start + val0to1 * (range.endInclusive - range.start)

    return when (mode) {
        dev.anthonyhfm.amethyst.core.controls.automation.CurveMode.Bipolar -> {
            if (unit == "%" || (unit.isEmpty() && range == 0f..1f)) {
                val pct = (normalizedValue * 100f).roundToInt()
                if (pct > 0) "+$pct%" else "$pct%"
            } else {
                val rounded = (actual * 10f).roundToInt() / 10f
                val sign = if (rounded > 0f) "+" else ""
                "$sign$rounded$unit"
            }
        }
        dev.anthonyhfm.amethyst.core.controls.automation.CurveMode.Unipolar -> {
            if (unit == "%" || (unit.isEmpty() && range == 0f..1f)) {
                val pct = (val0to1 * 100f).roundToInt()
                "$pct%"
            } else {
                val rounded = (actual * 10f).roundToInt() / 10f
                "$rounded$unit"
            }
        }
    }
}

package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionGraphEditor
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.automationParameter
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.CompositionAutomationPoint
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.lane
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.segmentValueAt
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.node
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

private sealed interface AutomationDragTarget {
    data class Point(val id: String) : AutomationDragTarget
    data class Handle(val id: String, val incoming: Boolean) : AutomationDragTarget
}

@Composable
fun CompositionAutomationEditor(device: CompositionChainDevice, editor: CompositionGraphEditor) {
    val focus by editor.automationFocus.collectAsState()
    val state by device.state.collectAsState()
    val selections by SelectionManager.selections.collectAsState()
    val target = focus?.let { key -> state.graph.node(key.nodeId)?.let { it to key.parameterId } } ?: return
    val node = target.first
    val parameter = node.automationParameter(target.second) ?: return
    val lane = node.lane(parameter.id) ?: return
    val fallback = parameter.valueOf(node) ?: return
    val progress = device.playbackProgress()
    val deviceId = device.selectionUUID
    val selectedPointId = selections.filterIsInstance<dev.anthonyhfm.amethyst.core.controls.selection.Selectable.CompositionAutomationPoint>()
        .lastOrNull { it.deviceId == deviceId && it.nodeId == node.id && it.parameterId == parameter.id }
        ?.pointId
    val editorPoints = lane.points
    val selectedPoint = editorPoints.firstOrNull { it.pointId == selectedPointId }
    val pointLabel = selectedPoint?.let { point ->
        "Point · ${parameter.format(parameter.denormalise(point.value))} · ${((point.progress * 100).toInt())}%"
    } ?: "Point · —"

    Column(
        modifier = Modifier.fillMaxWidth().height(200.dp).background(Theme[chainColorTokens][chainSurface], DefaultShape)
            .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${node.label} · ${parameter.label}", style = Theme[typography][small], color = Theme[colors][foreground], maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(16.dp))
                Text(pointLabel, style = Theme[typography][small], color = Theme[colors][mutedForeground], maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
            }
            UnstyledButton(onClick = editor::closeAutomation, modifier = Modifier.size(32.dp).align(Alignment.CenterEnd)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("×", color = Theme[colors][foreground]) }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AutomationCanvas(
                points = editorPoints,
                playhead = progress,
                selectedPointId = selectedPointId,
                bipolar = parameter.bipolar,
                onSelect = { id -> SelectionManager.selectCompositionAutomationPoints(deviceId, node.id, parameter.id, listOfNotNull(id)) },
                onAdd = { p, value -> editor.updateAutomationLane(node.id, parameter.id) { current -> current.copy(points = current.points + CompositionAutomationPoint(p, value)) } },
                onMove = { id, p, value -> editor.previewAutomationPoints(node.id, parameter.id, editorPoints.map { if (it.pointId == id) it.copy(progress = p, value = value) else it }) },
                onMoveHandle = { id, incoming, time, value -> editor.previewAutomationPoints(node.id, parameter.id, editorPoints.map { point ->
                    if (point.pointId != id) point else if (incoming) point.copy(
                        inHandleTime = time, inHandleValue = value,
                        outHandleTime = 1f - time, outHandleValue = (2f * point.value - value).coerceIn(-1f, 1f),
                    ) else point.copy(
                        outHandleTime = time, outHandleValue = value,
                        inHandleTime = 1f - time, inHandleValue = (2f * point.value - value).coerceIn(-1f, 1f),
                    )
                    }) },
                onDragFinished = editor::commitAutomationPreview,
                modifier = Modifier.fillMaxSize(),
            )
            AutomationValueAxis(
                parameter = parameter,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

@Composable
private fun AutomationValueAxis(
    parameter: dev.anthonyhfm.amethyst.devices.effects.composition.nodes.CompositionAutomationParameter,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Text(parameter.format(parameter.maximum), style = Theme[typography][small], color = Theme[colors][mutedForeground], modifier = Modifier.align(Alignment.TopStart).background(Theme[colors][secondary], DefaultShape).padding(horizontal = 2.dp))
        if (parameter.bipolar) {
            Text(parameter.format(0f), style = Theme[typography][small], color = Theme[colors][mutedForeground], modifier = Modifier.align(Alignment.CenterStart).background(Theme[colors][secondary], DefaultShape).padding(horizontal = 2.dp))
        }
        Text(parameter.format(parameter.minimum), style = Theme[typography][small], color = Theme[colors][mutedForeground], modifier = Modifier.align(Alignment.BottomStart).background(Theme[colors][secondary], DefaultShape).padding(horizontal = 2.dp))
    }
}

@Composable
private fun AutomationCanvas(
    points: List<CompositionAutomationPoint>, playhead: Float, selectedPointId: String?, bipolar: Boolean,
    onSelect: (String?) -> Unit, onAdd: (Float, Float) -> Unit, onMove: (String, Float, Float) -> Unit,
    onMoveHandle: (String, Boolean, Float, Float) -> Unit, onDragFinished: () -> Unit, modifier: Modifier,
) {
    var draggedTarget by remember { mutableStateOf<AutomationDragTarget?>(null) }
    val currentPoints = rememberUpdatedState(points)
    val currentSelectedPointId = rememberUpdatedState(selectedPointId)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val currentOnAdd = rememberUpdatedState(onAdd)
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnMoveHandle = rememberUpdatedState(onMoveHandle)
    val surfaceColor = Theme[colors][secondary]
    val mutedColor = Theme[colors][mutedForeground]
    val primaryColor = Theme[colors][primary]
    fun toValue(y: Float, height: Float) = (1f - (y / height).coerceIn(0f, 1f) * 2f).coerceIn(-1f, 1f)
    fun pointAt(position: Offset, width: Float, height: Float): CompositionAutomationPoint? = currentPoints.value.minByOrNull {
        val x = it.progress * width; val y = (1f - (it.value + 1f) * .5f) * height
        (Offset(x, y) - position).getDistance()
    }?.takeIf { val x = it.progress * width; val y = (1f - (it.value + 1f) * .5f) * height; (Offset(x, y) - position).getDistance() < 18f }
    fun handlePosition(point: CompositionAutomationPoint, incoming: Boolean, width: Float, height: Float): Offset? {
        val ordered = currentPoints.value.sortedBy(CompositionAutomationPoint::progress)
        val index = ordered.indexOfFirst { it.pointId == point.pointId }
        val neighbour = if (incoming) ordered.getOrNull(index - 1) else ordered.getOrNull(index + 1)
        if (neighbour == null) return null
        val fraction = if (incoming) point.inHandleTime ?: (2f / 3f) else point.outHandleTime ?: (1f / 3f)
        val x = if (incoming) neighbour.progress + (point.progress - neighbour.progress) * fraction else point.progress + (neighbour.progress - point.progress) * fraction
        val linearValue = if (incoming) neighbour.value + (point.value - neighbour.value) * (2f / 3f) else point.value + (neighbour.value - point.value) / 3f
        val value = if (incoming) point.inHandleValue ?: linearValue else point.outHandleValue ?: linearValue
        return Offset(x * width, (1f - (value + 1f) * .5f) * height)
    }
    fun handleAt(position: Offset, width: Float, height: Float): AutomationDragTarget.Handle? {
        val selected = currentPoints.value.firstOrNull { it.pointId == currentSelectedPointId.value } ?: return null
        return listOf(true, false).firstNotNullOfOrNull { incoming ->
            handlePosition(selected, incoming, width, height)?.takeIf { (it - position).getDistance() < 18f }?.let { AutomationDragTarget.Handle(selected.pointId, incoming) }
        }
    }
    Canvas(
        modifier = modifier.background(surfaceColor, DefaultShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    // Selection happens on press, before the drag recogniser can cancel a tap.
                    onPress = { position ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        // A handle belongs to the already selected point. Do not clear that
                        // point's selection before the drag recogniser gets to claim it.
                        val selectedId = handleAt(position, width, height)?.id
                            ?: pointAt(position, width, height)?.pointId
                        currentOnSelect.value(selectedId)
                        tryAwaitRelease()
                    },
                    onDoubleTap = { p ->
                        currentOnAdd.value((p.x / size.width).coerceIn(0f, 1f), toValue(p.y, size.height.toFloat()))
                    },
                )
            }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { p ->
                val width = size.width.toFloat(); val height = size.height.toFloat()
                draggedTarget = handleAt(p, width, height) ?: pointAt(p, width, height)?.let { AutomationDragTarget.Point(it.pointId) }
                (draggedTarget as? AutomationDragTarget.Point)?.let { currentOnSelect.value(it.id) }
            }, onDrag = { change, _ ->
                val time = (change.position.x / size.width).coerceIn(0f, 1f); val value = toValue(change.position.y, size.height.toFloat())
                when (val target = draggedTarget) {
                    is AutomationDragTarget.Point -> currentOnMove.value(target.id, time, value)
                    is AutomationDragTarget.Handle -> {
                        val point = currentPoints.value.firstOrNull { it.pointId == target.id } ?: return@detectDragGestures
                        val ordered = currentPoints.value.sortedBy(CompositionAutomationPoint::progress); val index = ordered.indexOfFirst { it.pointId == point.pointId }
                        val neighbour = (if (target.incoming) ordered.getOrNull(index - 1) else ordered.getOrNull(index + 1)) ?: return@detectDragGestures
                        val fraction = if (target.incoming) ((time - neighbour.progress) / (point.progress - neighbour.progress).coerceAtLeast(.0001f)) else ((time - point.progress) / (neighbour.progress - point.progress).coerceAtLeast(.0001f))
                        currentOnMoveHandle.value(target.id, target.incoming, fraction.coerceIn(.05f, .95f), value)
                    }
                    null -> Unit
                }
            }, onDragEnd = { draggedTarget = null; onDragFinished() }, onDragCancel = { draggedTarget = null; onDragFinished() }) },
    ) {
        val zeroY = size.height / 2f
        listOf(.25f, .5f, .75f).forEach { x -> drawLine(mutedColor.copy(alpha = .18f), Offset(size.width * x, 0f), Offset(size.width * x, size.height), 1.dp.toPx()) }
        val sorted = points.sortedBy(CompositionAutomationPoint::progress)
        if (sorted.size > 1) {
            val canvasSize = size
            val curvePoints = buildList {
                sorted.zipWithNext().forEach { (start, end) ->
                    (0..24).forEach { step -> if (isEmpty() || step > 0) {
                        val p = step / 24f; add(Offset((start.progress + (end.progress - start.progress) * p) * canvasSize.width, (1f - (start.segmentValueAt(end, p) + 1f) * .5f) * canvasSize.height))
                    } }
                }
            }
            val strokePath = Path().apply { curvePoints.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) } }
            val fillPath = Path().apply { addPath(strokePath); lineTo(curvePoints.last().x, size.height); lineTo(curvePoints.first().x, size.height); close() }
            drawPath(fillPath, primaryColor.copy(alpha = .14f), style = Fill)
            drawPath(strokePath, primaryColor, style = Stroke(3.dp.toPx()))
        }
        val baselineY = if (bipolar) zeroY else size.height - .5.dp.toPx()
        drawLine(mutedColor.copy(alpha = .5f), Offset(0f, baselineY), Offset(size.width, baselineY), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = .65f), Offset(playhead * size.width, 0f), Offset(playhead * size.width, size.height), 1.dp.toPx())
        sorted.forEach { point ->
            val center = Offset(point.progress * size.width, (1f - (point.value + 1f) * .5f) * size.height)
            if (point.pointId == selectedPointId) {
                listOf(true, false).forEach { incoming -> handlePosition(point, incoming, size.width, size.height)?.let { handle ->
                    drawLine(mutedColor.copy(alpha = .8f), center, handle, 1.5.dp.toPx())
                    drawCircle(surfaceColor, 7.dp.toPx(), handle)
                    drawCircle(primaryColor, 7.dp.toPx(), handle, style = Stroke(2.dp.toPx()))
                } }
                drawCircle(surfaceColor, 9.dp.toPx(), center)
                drawCircle(Color.White, 7.dp.toPx(), center)
                drawCircle(primaryColor, 7.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            } else drawCircle(primaryColor, 6.dp.toPx(), center)
        }
    }
}

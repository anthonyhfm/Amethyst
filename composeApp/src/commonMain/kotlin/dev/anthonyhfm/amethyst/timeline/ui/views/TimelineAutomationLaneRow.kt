package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.TimelineCommandSurface
import dev.anthonyhfm.amethyst.timeline.TimelineEditedAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.applyAutomationCurve
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuContent
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.utils.computeSnappedTimeFromContentX
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

private enum class AutomationDragMode {
    Point,
    Curve
}

private data class AutomationPointDragState(
    val beforePoints: List<TimelineAutomationPoint>,
    val afterPoints: List<TimelineAutomationPoint>,
    val draggedPointId: String,
    val dragStartOffset: Offset,
    val mode: AutomationDragMode,
    val curveSegmentEndPoint: TimelineAutomationPoint? = null
)

private data class AutomationPointTapState(
    val pointId: String,
    val timeMillis: Long
)

private data class AutomationRangeDragState(
    val startTimeMs: Long,
    val endTimeMs: Long,
)

private data class AutomationSegmentHit(
    val startPoint: TimelineAutomationPoint,
    val endPoint: TimelineAutomationPoint
)

private sealed interface AutomationHoverTarget {
    data class Point(val pointId: String) : AutomationHoverTarget
    data class Segment(
        val startPointId: String,
        val endPointId: String
    ) : AutomationHoverTarget
}

private const val AutomationDoubleTapTimeoutMs = 300L
private const val AutomationTapSlopPx = 10f
private const val AutomationPointHitRadiusPx = 14f
private const val AutomationSegmentHitRadiusPx = 20f
private const val AutomationMaxCurve = 3f
private const val AutomationCurvePathSteps = 16

@Composable
internal fun TimelineAutomationLaneRow(
    trackIndex: Int,
    track: TimelineTrack<*>,
    lane: TimelineAutomationLane,
    viewport: EditorViewportState,
    modifier: Modifier = Modifier
) {
    val zoomLevel = viewport.zoomX
    val scrollOffsetPx = viewport.scrollX
    val timelinePalette = TimelineTheme.palette
    val selections by SelectionManager.selections.collectAsState()
    val activeAutomationLane by SelectionManager.activeTimelineAutomationLane.collectAsState()
    val normalizedLane = lane.normalized()
    val laneKey = normalizedLane.key
    val isSelected = activeAutomationLane?.trackIndex == trackIndex &&
        activeAutomationLane?.laneKey == laneKey
    val currentIsAltPressed by rememberUpdatedState(ModifierKeysState.isAltPressed)
    val currentIsMetaSelectionPressed by rememberUpdatedState(
        ModifierKeysState.isMetaPressed || ModifierKeysState.isCtrlPressed
    )
    // Always-fresh state refs for pointer-input closures keyed on Unit.
    val currentViewport = rememberUpdatedState(viewport)
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()
    val currentBpm = rememberUpdatedState(bpm)
    val currentGridType = rememberUpdatedState(gridType)
    val selectedRange = selections
        .filterIsInstance<Selectable.TimelineRange>()
        .firstOrNull { it.trackIndex == trackIndex }
    val explicitlySelectedPointIds = SelectionManager.selectedTimelineAutomationPointIds(
        trackIndex = trackIndex,
        lane = laneKey,
        currentSelections = selections
    )

    var dragState by remember {
        mutableStateOf<AutomationPointDragState?>(null)
    }
    var rangeDragState by remember {
        mutableStateOf<AutomationRangeDragState?>(null)
    }
    var lastTapState by remember {
        mutableStateOf<AutomationPointTapState?>(null)
    }
    var lastPointerPosition by remember {
        mutableStateOf<Offset?>(null)
    }
    var contextMenuState by remember {
        mutableStateOf<AutomationSegmentHit?>(null)
    }
    var contextMenuPosition by remember {
        mutableStateOf(Offset.Zero)
    }
    val laneHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        TimelineAutomationLaneRowHeight.toPx()
    }

    val renderedPoints = remember(normalizedLane.points, dragState) {
        val pointOverrides = dragState
            ?.afterPoints
            ?.associateByTo(mutableMapOf(), TimelineAutomationPoint::pointId)
            ?: return@remember normalizedLane.points

        normalizedLane.points.map { point ->
            pointOverrides[point.pointId] ?: point
        }
    }
    val renderedLane = remember(normalizedLane, renderedPoints) {
        normalizedLane.copy(points = renderedPoints).normalized()
    }
    val hoverTarget = remember(
        lastPointerPosition,
        renderedLane.points,
        viewport.scrollX,
        viewport.zoomX,
        ModifierKeysState.isAltPressed,
    ) {
        val pos = lastPointerPosition ?: return@remember null
        val hitPt = hitAutomationPoint(
            points = renderedLane.points,
            tapOffset = pos,
            zoomLevel = viewport.zoomX,
            scrollOffsetPx = viewport.scrollX,
            laneHeightPx = laneHeightPx,
            target = normalizedLane.target
        )
        if (hitPt != null) return@remember AutomationHoverTarget.Point(hitPt.pointId)

        if (ModifierKeysState.isAltPressed) {
            val hitSeg = hitAutomationSegment(
                points = renderedLane.points,
                tapOffset = pos,
                zoomLevel = viewport.zoomX,
                scrollOffsetPx = viewport.scrollX,
                laneHeightPx = laneHeightPx,
                target = normalizedLane.target
            )
            if (hitSeg != null) return@remember AutomationHoverTarget.Segment(
                startPointId = hitSeg.startPoint.pointId,
                endPointId = hitSeg.endPoint.pointId
            )
        }
        null
    }
    val rangeSelectedPointIds = remember(renderedLane.points, selectedRange) {
        if (selectedRange == null) {
            emptySet()
        } else {
            renderedLane.points
                .filter { point ->
                    point.timeMs in selectedRange.startMs..selectedRange.endMs
                }
                .mapTo(mutableSetOf(), TimelineAutomationPoint::pointId)
        }
    }
    val effectiveSelectedPointIds = when {
        explicitlySelectedPointIds.isNotEmpty() -> explicitlySelectedPointIds
        rangeSelectedPointIds.isNotEmpty() -> rangeSelectedPointIds
        else -> emptySet()
    }
    val currentRenderedLane = rememberUpdatedState(renderedLane)
    val currentNormalizedLane = rememberUpdatedState(normalizedLane)
    val currentEffectiveSelectedPointIds = rememberUpdatedState(effectiveSelectedPointIds)
    val baseValue = track.automationLaneBaseValue(normalizedLane)
    val neutralValue = normalizedLane.target.defaultValue

    fun selectLane() {
        SelectionManager.selectTimelineAutomationLane(
            trackIndex = trackIndex,
            target = laneKey.target,
            bindingId = laneKey.bindingId
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TimelineAutomationLaneRowHeight)
            .background(
                color = if (isSelected) {
                    timelinePalette.automationLaneSurface.copy(alpha = 0.94f)
                } else {
                    timelinePalette.automationLaneSurface.copy(alpha = 0.72f)
                },
                shape = SmallShape
            )
            .border(
                width = 1.dp,
                color = if (isSelected) {
                    timelinePalette.automationLaneAccent
                } else {
                    timelinePalette.shellBorder.copy(alpha = 0.84f)
                },
                shape = SmallShape
            )
            .clipToBounds()
            .rightClickable { position ->
                val vp = currentViewport.value
                val pts = currentRenderedLane.value.points
                val tgt = currentNormalizedLane.value.target
                val hitSeg = hitAutomationSegment(
                    points = pts,
                    tapOffset = position,
                    zoomLevel = vp.zoomX,
                    scrollOffsetPx = vp.scrollX,
                    laneHeightPx = laneHeightPx,
                    target = tgt
                )
                if (hitSeg != null) {
                    contextMenuState = hitSeg
                    contextMenuPosition = position
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Main
                    )

                    // Snapshot state at press time via rememberUpdatedState refs
                    val vp = currentViewport.value
                    val points = currentRenderedLane.value.points
                    val target = currentNormalizedLane.value.target
                    val lk = currentNormalizedLane.value.key
                    val isAlt = currentIsAltPressed
                    val isMeta = currentIsMetaSelectionPressed
                    val selectedIds = currentEffectiveSelectedPointIds.value
                    val laneHeight = size.height.toFloat()

                    // Hit tests
                    val hitPoint = hitAutomationPoint(
                        points = points,
                        tapOffset = down.position,
                        zoomLevel = vp.zoomX,
                        scrollOffsetPx = vp.scrollX,
                        laneHeightPx = laneHeight,
                        target = target
                    )
                    val hitSegment = if (hitPoint == null && isAlt) {
                        hitAutomationSegment(
                            points = points,
                            tapOffset = down.position,
                            zoomLevel = vp.zoomX,
                            scrollOffsetPx = vp.scrollX,
                            laneHeightPx = laneHeight,
                            target = target
                        )
                    } else null

                    // === POINT HIT ===
                    if (hitPoint != null) {
                        val affectedPointIds = if (
                            selectedIds.isNotEmpty() &&
                            hitPoint.pointId in selectedIds
                        ) {
                            selectedIds
                        } else {
                            if (!isMeta) {
                                SelectionManager.selectTimelineAutomationPoints(
                                    trackIndex = trackIndex,
                                    lane = lk,
                                    pointIds = listOf(hitPoint.pointId)
                                )
                            }
                            setOf(hitPoint.pointId)
                        }

                        val affectedPoints = points.filter { it.pointId in affectedPointIds }
                        dragState = AutomationPointDragState(
                            beforePoints = affectedPoints,
                            afterPoints = affectedPoints,
                            draggedPointId = hitPoint.pointId,
                            dragStartOffset = down.position,
                            mode = AutomationDragMode.Point
                        )
                        rangeDragState = null

                        var totalMovement = 0f
                        var lastPos = down.position
                        var upPosition: Offset? = null
                        var upTimeMillis = 0L

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) {
                                dragState = null
                                return@awaitEachGesture
                            }

                            if (!change.pressed) {
                                upPosition = change.position
                                upTimeMillis = change.uptimeMillis
                                break
                            }

                            val delta = change.position - lastPos
                            totalMovement += sqrt(delta.x * delta.x + delta.y * delta.y)
                            lastPos = change.position

                            // Live-update point positions (no slop — immediate response)
                            val vp2 = currentViewport.value
                            val currentDrag = dragState ?: break
                            val anchorBefore = currentDrag.beforePoints
                                .firstOrNull { it.pointId == currentDrag.draggedPointId }
                                ?: break
                            val snapTime = computeSnappedTimeFromContentX(
                                vp2.screenToContentX(change.position.x),
                                vp2.zoomX,
                                currentBpm.value,
                                currentGridType.value
                            )
                            val minDelta = -(
                                currentDrag.beforePoints.minOfOrNull(
                                    TimelineAutomationPoint::timeMs
                                ) ?: 0L
                            )
                            val deltaTime = (snapTime - anchorBefore.timeMs)
                                .coerceAtLeast(minDelta)
                            val anchorValue = pointerOffsetToValue(
                                y = change.position.y,
                                laneHeightPx = laneHeight,
                                target = target
                            )
                            val anchorDisplayValue =
                                target.valueToDisplayValue(anchorValue)
                            val deltaDisplayValue = anchorDisplayValue -
                                target.valueToDisplayValue(anchorBefore.value)

                            val updatedPoints = currentDrag.beforePoints.map { point ->
                                val updatedDisplayValue =
                                    target.valueToDisplayValue(point.value) +
                                        deltaDisplayValue
                                point.copy(
                                    timeMs = (point.timeMs + deltaTime)
                                        .coerceAtLeast(0L),
                                    value = target.displayValueToValue(
                                        updatedDisplayValue
                                    )
                                )
                            }
                            if (updatedPoints != currentDrag.afterPoints) {
                                dragState = currentDrag.copy(
                                    afterPoints = updatedPoints
                                )
                            }
                            change.consume()
                        }

                        // Handle release
                        val finalDrag = dragState
                        dragState = null

                        if (upPosition != null && totalMovement < AutomationTapSlopPx) {
                            // It was a tap, not a drag — handle selection
                            selectLane()
                            val isDoubleTap =
                                lastTapState?.pointId == hitPoint.pointId &&
                                    upTimeMillis - (lastTapState?.timeMillis ?: 0L) <=
                                    AutomationDoubleTapTimeoutMs

                            if (isDoubleTap) {
                                TimelineCommandSurface.deleteAutomationPoints(
                                    trackIndex = trackIndex,
                                    lane = lk,
                                    pointIds = listOf(hitPoint.pointId)
                                )
                                lastTapState = null
                            } else {
                                if (isMeta) {
                                    SelectionManager.toggleTimelineAutomationPoint(
                                        trackIndex = trackIndex,
                                        lane = lk,
                                        pointId = hitPoint.pointId
                                    )
                                } else {
                                    SelectionManager.selectTimelineAutomationPoints(
                                        trackIndex = trackIndex,
                                        lane = lk,
                                        pointIds = listOf(hitPoint.pointId)
                                    )
                                }
                                lastTapState = AutomationPointTapState(
                                    pointId = hitPoint.pointId,
                                    timeMillis = upTimeMillis
                                )
                            }
                        } else if (
                            finalDrag != null &&
                            finalDrag.afterPoints != finalDrag.beforePoints
                        ) {
                            TimelineCommandSurface.moveAutomationPoints(
                                trackIndex = trackIndex,
                                lane = lk,
                                changes = finalDrag.beforePoints
                                    .zip(finalDrag.afterPoints)
                                    .map { (before, after) ->
                                        TimelineEditedAutomationPoint(
                                            before = before,
                                            after = after
                                        )
                                    }
                            )
                        }
                        return@awaitEachGesture
                    }

                    // === CURVE SEGMENT HIT (Alt held) ===
                    if (hitSegment != null) {
                        selectLane()
                        dragState = AutomationPointDragState(
                            beforePoints = listOf(hitSegment.startPoint),
                            afterPoints = listOf(hitSegment.startPoint),
                            draggedPointId = hitSegment.startPoint.pointId,
                            dragStartOffset = down.position,
                            mode = AutomationDragMode.Curve,
                            curveSegmentEndPoint = hitSegment.endPoint
                        )
                        rangeDragState = null

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (change.isConsumed) {
                                dragState = null
                                return@awaitEachGesture
                            }

                            if (!change.pressed) break

                            // Compute curve so the line passes through the cursor
                            val currentDrag = dragState ?: break
                            val segEnd = currentDrag.curveSegmentEndPoint ?: break
                            val vp2 = currentViewport.value
                            val newCurve = computeCurveFromCursor(
                                cursorScreenX = change.position.x,
                                cursorScreenY = change.position.y,
                                startPoint = currentDrag.beforePoints.first(),
                                endPoint = segEnd,
                                zoomLevel = vp2.zoomX,
                                scrollOffsetPx = vp2.scrollX,
                                laneHeightPx = laneHeight,
                                target = target
                            )

                            dragState = currentDrag.copy(
                                afterPoints = listOf(
                                    currentDrag.beforePoints.first().copy(
                                        curve = newCurve
                                    )
                                )
                            )
                            change.consume()
                        }

                        val finalDrag = dragState
                        dragState = null
                        if (
                            finalDrag != null &&
                            finalDrag.afterPoints != finalDrag.beforePoints
                        ) {
                            TimelineCommandSurface.moveAutomationPoints(
                                trackIndex = trackIndex,
                                lane = lk,
                                changes = finalDrag.beforePoints
                                    .zip(finalDrag.afterPoints)
                                    .map { (before, after) ->
                                        TimelineEditedAutomationPoint(
                                            before = before,
                                            after = after
                                        )
                                    }
                            )
                        }
                        return@awaitEachGesture
                    }

                    // === EMPTY SPACE ===
                    val initTime = computeSnappedTimeFromContentX(
                        vp.screenToContentX(down.position.x),
                        vp.zoomX,
                        currentBpm.value,
                        currentGridType.value
                    )
                    rangeDragState = AutomationRangeDragState(
                        startTimeMs = initTime,
                        endTimeMs = initTime
                    )
                    dragState = null

                    var totalMovement = 0f
                    var lastPos = down.position
                    var upPosition: Offset? = null
                    var upTimeMillis = 0L

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            rangeDragState = null
                            return@awaitEachGesture
                        }

                        if (!change.pressed) {
                            upPosition = change.position
                            upTimeMillis = change.uptimeMillis
                            break
                        }

                        val delta = change.position - lastPos
                        totalMovement += sqrt(delta.x * delta.x + delta.y * delta.y)
                        lastPos = change.position

                        if (totalMovement >= AutomationTapSlopPx) {
                            val vp2 = currentViewport.value
                            val currentRange = rangeDragState ?: break
                            rangeDragState = currentRange.copy(
                                endTimeMs = computeSnappedTimeFromContentX(
                                    vp2.screenToContentX(change.position.x),
                                    vp2.zoomX,
                                    currentBpm.value,
                                    currentGridType.value
                                )
                            )
                            change.consume()
                        }
                    }

                    val currentRange = rangeDragState
                    rangeDragState = null

                    if (upPosition != null && totalMovement < AutomationTapSlopPx) {
                        // Tap on empty space — create a new automation point
                        lastTapState = null
                        val vp2 = currentViewport.value
                        TimelineCommandSurface.createAutomationPoints(
                            trackIndex = trackIndex,
                            lane = lk,
                            points = listOf(
                                TimelineAutomationPoint(
                                    timeMs = computeSnappedTimeFromContentX(
                                        vp2.screenToContentX(upPosition.x),
                                        vp2.zoomX,
                                        currentBpm.value,
                                        currentGridType.value
                                    ),
                                    value = pointerOffsetToValue(
                                        y = upPosition.y,
                                        laneHeightPx = laneHeight,
                                        target = target
                                    )
                                )
                            )
                        )
                    } else if (currentRange != null) {
                        val normalizedStart = minOf(
                            currentRange.startTimeMs,
                            currentRange.endTimeMs
                        )
                        val normalizedEnd = maxOf(
                            currentRange.startTimeMs,
                            currentRange.endTimeMs
                        )
                        if (normalizedEnd > normalizedStart) {
                            SelectionManager.replaceSelections(
                                listOf(
                                    Selectable.TimelineAutomationLane(
                                        trackIndex = trackIndex,
                                        target = lk.target,
                                        bindingId = lk.bindingId
                                    ),
                                    Selectable.TimelineRange(
                                        trackIndex = trackIndex,
                                        startMs = normalizedStart,
                                        endMs = normalizedEnd
                                    )
                                )
                            )
                        } else {
                            selectLane()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                while (true) {
                    val event = awaitPointerEventScope {
                        awaitPointerEvent(pass = PointerEventPass.Main)
                    }
                    when (event.type) {
                        PointerEventType.Move -> {
                            lastPointerPosition = event.changes.firstOrNull()?.position
                        }
                        PointerEventType.Exit -> {
                            lastPointerPosition = null
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TimelineAutomationLaneRowHeight)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TimelineAutomationLaneRowHeight)
            ) {
                val overlayStart = when {
                    rangeDragState != null -> minOf(rangeDragState!!.startTimeMs, rangeDragState!!.endTimeMs)
                    selectedRange != null -> selectedRange.startMs
                    else -> null
                }
                val overlayEnd = when {
                    rangeDragState != null -> maxOf(rangeDragState!!.startTimeMs, rangeDragState!!.endTimeMs)
                    selectedRange != null -> selectedRange.endMs
                    else -> null
                }

                if (overlayStart != null && overlayEnd != null && overlayEnd > overlayStart) {
                    drawRect(
                        color = timelinePalette.selectionFill.copy(alpha = 0.22f),
                        topLeft = Offset(
                            x = overlayStart.toFloat() * zoomLevel - scrollOffsetPx,
                            y = 0f
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            width = (overlayEnd - overlayStart).toFloat() * zoomLevel,
                            height = size.height
                        )
                    )
                }

                val neutralBaselineY = valueToY(
                    value = neutralValue,
                    laneHeightPx = size.height,
                    target = normalizedLane.target
                )
                val baseValueY = valueToY(
                    value = baseValue,
                    laneHeightPx = size.height,
                    target = normalizedLane.target
                )

                drawLine(
                    color = timelinePalette.automationLaneAccent.copy(alpha = 0.12f),
                    start = Offset(0f, neutralBaselineY),
                    end = Offset(size.width, neutralBaselineY),
                    strokeWidth = 1.dp.toPx()
                )
                if (abs(baseValueY - neutralBaselineY) > 0.5f) {
                    drawLine(
                        color = timelinePalette.automationLaneAccent.copy(alpha = 0.2f),
                        start = Offset(0f, baseValueY),
                        end = Offset(size.width, baseValueY),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val laneAlpha = if (normalizedLane.enabled) 1f else 0.42f
                val accentColor = timelinePalette.automationLaneAccent.copy(alpha = laneAlpha)
                val lanePath = Path().apply {
                    moveTo(0f, baseValueY)

                    if (renderedLane.points.isEmpty()) {
                        lineTo(size.width, baseValueY)
                    } else {
                        renderedLane.points.forEachIndexed { index, point ->
                            // Screen-space x: subtract scroll so coordinates are viewport-relative.
                            val pointX = point.timeMs.toFloat() * zoomLevel - scrollOffsetPx
                            val pointY = valueToY(
                                value = point.value,
                                laneHeightPx = size.height,
                                target = normalizedLane.target
                            )

                            if (index == 0) {
                                lineTo(pointX, baseValueY)
                                lineTo(pointX, pointY)
                            } else {
                                appendAutomationSegmentToPath(
                                    path = this,
                                    startPoint = renderedLane.points[index - 1],
                                    endPoint = point,
                                    zoomLevel = zoomLevel,
                                    scrollOffsetPx = scrollOffsetPx,
                                    laneHeightPx = size.height,
                                    target = normalizedLane.target
                                )
                            }
                        }

                        val finalValue = renderedLane.points.last().value
                        lineTo(
                            size.width,
                            valueToY(
                                value = finalValue,
                                laneHeightPx = size.height,
                                target = normalizedLane.target
                            )
                        )
                    }
                }

                drawPath(
                    path = lanePath,
                    color = accentColor,
                    style = Stroke(
                        width = if (isSelected) 2.5.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                renderedLane.points.forEach { point ->
                    // Screen-space x: subtract scroll so coordinates are viewport-relative.
                    val pointX = point.timeMs.toFloat() * zoomLevel - scrollOffsetPx
                    val pointY = valueToY(
                        value = point.value,
                        laneHeightPx = size.height,
                        target = normalizedLane.target
                    )
                    val isDraggedPoint = dragState?.beforePoints?.any { it.pointId == point.pointId } == true
                    val isExplicitlySelected = point.pointId in explicitlySelectedPointIds
                    val isRangeSelected = point.pointId in rangeSelectedPointIds
                    val isSelectedPoint = point.pointId in effectiveSelectedPointIds
                    val isHoveredPoint = hoverTarget is AutomationHoverTarget.Point &&
                        (hoverTarget as AutomationHoverTarget.Point).pointId == point.pointId

                    if (isHoveredPoint && !isDraggedPoint) {
                        drawCircle(
                            color = accentColor.copy(alpha = 0.18f),
                            radius = 10.dp.toPx(),
                            center = Offset(pointX, pointY)
                        )
                    }

                    if (isRangeSelected || isSelectedPoint) {
                        drawCircle(
                            color = timelinePalette.selectionStroke.copy(alpha = if (isExplicitlySelected) 0.55f else 0.32f),
                            radius = if (isExplicitlySelected) 9.dp.toPx() else 7.5.dp.toPx(),
                            center = Offset(pointX, pointY)
                        )
                    }

                    drawCircle(
                        color = accentColor,
                        radius = when {
                            isDraggedPoint -> 5.5.dp.toPx()
                            isHoveredPoint -> 5.5.dp.toPx()
                            isSelectedPoint -> 5.dp.toPx()
                            else -> 4.dp.toPx()
                        },
                        center = Offset(pointX, pointY)
                    )
                    drawCircle(
                        color = when {
                            isExplicitlySelected -> timelinePalette.selectionStroke
                            else -> timelinePalette.automationLaneSurface
                        },
                        radius = when {
                            isDraggedPoint -> 2.4.dp.toPx()
                            isHoveredPoint -> 2.4.dp.toPx()
                            isSelectedPoint -> 2.2.dp.toPx()
                            else -> 1.8.dp.toPx()
                        },
                        center = Offset(pointX, pointY)
                    )
                }

                // Highlight hovered segment (Alt+hover feedback)
                val currentHover = hoverTarget
                if (currentHover is AutomationHoverTarget.Segment) {
                    val startPt = renderedLane.points.firstOrNull {
                        it.pointId == currentHover.startPointId
                    }
                    val endPt = renderedLane.points.firstOrNull {
                        it.pointId == currentHover.endPointId
                    }
                    if (startPt != null && endPt != null) {
                        val hoverSegPath = Path().apply {
                            moveTo(
                                startPt.timeMs.toFloat() * zoomLevel - scrollOffsetPx,
                                valueToY(startPt.value, size.height, normalizedLane.target)
                            )
                            appendAutomationSegmentToPath(
                                path = this,
                                startPoint = startPt,
                                endPoint = endPt,
                                zoomLevel = zoomLevel,
                                scrollOffsetPx = scrollOffsetPx,
                                laneHeightPx = size.height,
                                target = normalizedLane.target
                            )
                        }
                        drawPath(
                            path = hoverSegPath,
                            color = accentColor.copy(alpha = 0.3f),
                            style = Stroke(
                                width = 6.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                // Highlight active segment during curve drag
                val activeCurveDrag = dragState
                if (activeCurveDrag != null &&
                    activeCurveDrag.mode == AutomationDragMode.Curve
                ) {
                    val draggedId = activeCurveDrag.draggedPointId
                    val startIdx = renderedLane.points.indexOfFirst {
                        it.pointId == draggedId
                    }
                    if (startIdx >= 0 && startIdx + 1 < renderedLane.points.size) {
                        val segStartPt = renderedLane.points[startIdx]
                        val segEndPt = renderedLane.points[startIdx + 1]
                        val segPath = Path().apply {
                            moveTo(
                                segStartPt.timeMs.toFloat() * zoomLevel - scrollOffsetPx,
                                valueToY(segStartPt.value, size.height, normalizedLane.target)
                            )
                            appendAutomationSegmentToPath(
                                path = this,
                                startPoint = segStartPt,
                                endPoint = segEndPt,
                                zoomLevel = zoomLevel,
                                scrollOffsetPx = scrollOffsetPx,
                                laneHeightPx = size.height,
                                target = normalizedLane.target
                            )
                        }
                        drawPath(
                            path = segPath,
                            color = accentColor.copy(alpha = 0.4f),
                            style = Stroke(
                                width = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }

        if (contextMenuState != null) {
            androidx.compose.ui.window.Popup(
                popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect,
                        windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        popupContentSize: androidx.compose.ui.unit.IntSize,
                    ): IntOffset {
                        val x = anchorBounds.left + contextMenuPosition.x.toInt()
                        val y = anchorBounds.top + contextMenuPosition.y.toInt()
                        return IntOffset(
                            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
                        )
                    }
                },
                onDismissRequest = { contextMenuState = null },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                ContextMenuContent {
                    ContextMenuItem(
                        onClick = {
                            val seg = contextMenuState
                            if (seg != null) {
                                TimelineCommandSurface.moveAutomationPoints(
                                    trackIndex = trackIndex,
                                    lane = laneKey,
                                    changes = listOf(
                                        TimelineEditedAutomationPoint(
                                            before = seg.startPoint,
                                            after = seg.startPoint.copy(curve = 0f)
                                        )
                                    )
                                )
                            }
                            contextMenuState = null
                        }
                    ) {
                        com.composeunstyled.Text("Reset Curve to Linear")
                    }
                }
            }
        }
    }
}

private fun appendAutomationSegmentToPath(
    path: Path,
    startPoint: TimelineAutomationPoint,
    endPoint: TimelineAutomationPoint,
    zoomLevel: Float,
    scrollOffsetPx: Float,
    laneHeightPx: Float,
    target: TimelineTrackAutomationTarget
) {
    if (abs(startPoint.curve) < 0.001f) {
        path.lineTo(
            endPoint.timeMs.toFloat() * zoomLevel - scrollOffsetPx,
            valueToY(
                value = endPoint.value,
                laneHeightPx = laneHeightPx,
                target = target
            )
        )
        return
    }

    val startX = startPoint.timeMs.toFloat() * zoomLevel - scrollOffsetPx
    val endX = endPoint.timeMs.toFloat() * zoomLevel - scrollOffsetPx
    // More steps for extreme curves to keep the visual smooth
    val steps = if (abs(startPoint.curve) > 1f) {
        (AutomationCurvePathSteps * 2).coerceAtMost(48)
    } else {
        AutomationCurvePathSteps
    }
    for (step in 1..steps) {
        val progress = step.toFloat() / steps.toFloat()
        path.lineTo(
            lerp(startX, endX, progress),
            valueToY(
                value = automationSegmentValueAtProgress(
                    startPoint = startPoint,
                    endPoint = endPoint,
                    progress = progress
                ),
                laneHeightPx = laneHeightPx,
                target = target
            )
        )
    }
}

private fun hitAutomationPoint(
    points: List<TimelineAutomationPoint>,
    tapOffset: Offset,
    zoomLevel: Float,
    scrollOffsetPx: Float,
    laneHeightPx: Float,
    target: TimelineTrackAutomationTarget
): TimelineAutomationPoint? {
    if (points.isEmpty()) return null

    return points.firstOrNull { point ->
        val pointOffset = Offset(
            x = point.timeMs.toFloat() * zoomLevel - scrollOffsetPx,
            y = valueToY(
                value = point.value,
                laneHeightPx = laneHeightPx,
                target = target
            )
        )
        pointOffset.distanceSquaredTo(tapOffset) <=
            AutomationPointHitRadiusPx * AutomationPointHitRadiusPx
    }
}

private fun hitAutomationSegment(
    points: List<TimelineAutomationPoint>,
    tapOffset: Offset,
    zoomLevel: Float,
    scrollOffsetPx: Float,
    laneHeightPx: Float,
    target: TimelineTrackAutomationTarget
): AutomationSegmentHit? {
    if (points.size < 2) return null

    var closestHit: AutomationSegmentHit? = null
    var closestDistance = Float.MAX_VALUE
    points.zipWithNext().forEach { (startPoint, endPoint) ->
        val startX = startPoint.timeMs.toFloat() * zoomLevel - scrollOffsetPx
        val endX = endPoint.timeMs.toFloat() * zoomLevel - scrollOffsetPx
        if (tapOffset.x < minOf(startX, endX) - AutomationSegmentHitRadiusPx ||
            tapOffset.x > maxOf(startX, endX) + AutomationSegmentHitRadiusPx
        ) {
            return@forEach
        }

        var previousSample = Offset(
            x = startX,
            y = valueToY(
                value = startPoint.value,
                laneHeightPx = laneHeightPx,
                target = target
            )
        )
        val sampleCount = AutomationCurvePathSteps * 2
        for (step in 1..sampleCount) {
            val progress = step.toFloat() / sampleCount.toFloat()
            val sample = Offset(
                x = lerp(startX, endX, progress),
                y = valueToY(
                    value = automationSegmentValueAtProgress(
                        startPoint = startPoint,
                        endPoint = endPoint,
                        progress = progress
                    ),
                    laneHeightPx = laneHeightPx,
                    target = target
                )
            )
            val distance = distanceToSegment(
                point = tapOffset,
                start = previousSample,
                end = sample
            )
            if (distance < closestDistance) {
                closestDistance = distance
                closestHit = AutomationSegmentHit(
                    startPoint = startPoint,
                    endPoint = endPoint
                )
            }
            previousSample = sample
        }
    }

    return closestHit?.takeIf { closestDistance <= AutomationSegmentHitRadiusPx }
}

private fun valueToY(
    value: Float,
    laneHeightPx: Float,
    target: TimelineTrackAutomationTarget
): Float {
    val normalizedValue = target.valueToDisplayProgress(value)
    return laneHeightPx - (normalizedValue * laneHeightPx)
}

private fun pointerOffsetToValue(
    y: Float,
    laneHeightPx: Float,
    target: TimelineTrackAutomationTarget
): Float {
    if (laneHeightPx <= 0f) return target.defaultValue
    val normalizedValue = (1f - (y / laneHeightPx)).coerceIn(0f, 1f)
    return target.displayProgressToValue(normalizedValue)
}

private fun automationSegmentValueAtProgress(
    startPoint: TimelineAutomationPoint,
    endPoint: TimelineAutomationPoint,
    progress: Float
): Float {
    val curvedProgress = applyAutomationCurve(
        progress = progress,
        curve = startPoint.curve
    )
    return lerp(startPoint.value, endPoint.value, curvedProgress)
}

private fun isWithinTapSlop(start: Offset, end: Offset): Boolean {
    return start.distanceSquaredTo(end) <= AutomationTapSlopPx * AutomationTapSlopPx
}

private fun distanceToSegment(
    point: Offset,
    start: Offset,
    end: Offset
): Float {
    val segment = end - start
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared <= 0.0001f) {
        return sqrt(start.distanceSquaredTo(point))
    }

    val t = (((point.x - start.x) * segment.x) + ((point.y - start.y) * segment.y)) / lengthSquared
    val projection = Offset(
        x = start.x + (segment.x * t.coerceIn(0f, 1f)),
        y = start.y + (segment.y * t.coerceIn(0f, 1f))
    )
    return sqrt(projection.distanceSquaredTo(point))
}

private fun Offset.distanceSquaredTo(other: Offset): Float {
    val deltaX = x - other.x
    val deltaY = y - other.y
    return (deltaX * deltaX) + (deltaY * deltaY)
}

private fun lerp(start: Float, end: Float, progress: Float): Float {
    return start + ((end - start) * progress.coerceIn(0f, 1f))
}

/**
 * Compute the automation curve value that makes the segment pass through the
 * cursor position. This inverts [applyAutomationCurve] so the line literally
 * follows the mouse.
 *
 * For positive curve (ease-out): `curvedProgress = 1 - (1-t)^exponent`
 * For negative curve (ease-in):  `curvedProgress = t^exponent`
 *
 * Given cursor position, we solve for the exponent that produces the target
 * progress at time t, then convert back to a curve value.
 *
 * Uses Double precision internally to avoid Float artifacts near extreme values.
 */
private fun computeCurveFromCursor(
    cursorScreenX: Float,
    cursorScreenY: Float,
    startPoint: TimelineAutomationPoint,
    endPoint: TimelineAutomationPoint,
    zoomLevel: Float,
    scrollOffsetPx: Float,
    laneHeightPx: Float,
    target: TimelineTrackAutomationTarget
): Float {
    val startX = startPoint.timeMs.toFloat() * zoomLevel - scrollOffsetPx
    val endX = endPoint.timeMs.toFloat() * zoomLevel - scrollOffsetPx
    val segmentWidth = endX - startX
    if (segmentWidth < 1f) return 0f

    // Use Double for all math to avoid precision loss near extremes
    val t = ((cursorScreenX - startX) / segmentWidth).toDouble().coerceIn(0.005, 0.995)
    val cursorValue = pointerOffsetToValue(cursorScreenY, laneHeightPx, target)

    val valueDelta = (endPoint.value - startPoint.value).toDouble()
    if (abs(valueDelta) < 0.0001) return 0f

    val targetProgress = ((cursorValue - startPoint.value) / valueDelta)
        .toDouble().coerceIn(0.0005, 0.9995)

    // Near-linear — avoid expensive log computation
    if (abs(targetProgress - t) < 0.005) return 0f

    val maxCurve = AutomationMaxCurve.toDouble()
    return if (targetProgress > t) {
        // Ease-out (positive curve): 1 - (1-t)^exponent = targetProgress
        val base = 1.0 - t
        val result = 1.0 - targetProgress
        if (base <= 0.0005 || result <= 0.0005) return AutomationMaxCurve
        val lnBase = ln(base)
        if (abs(lnBase) < 0.00001) return 0f
        val exponent = ln(result) / lnBase
        ((exponent - 1.0) / 3.0).coerceIn(0.0, maxCurve).toFloat()
    } else {
        // Ease-in (negative curve): t^exponent = targetProgress
        val lnT = ln(t)
        if (abs(lnT) < 0.00001) return 0f
        val lnTarget = ln(targetProgress)
        val exponent = lnTarget / lnT
        -(((exponent - 1.0) / 3.0).coerceIn(0.0, maxCurve)).toFloat()
    }
}

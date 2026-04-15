package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.utils.computeSnappedTimeFromContentX
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.abs
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
    val mode: AutomationDragMode
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

private const val AutomationDoubleTapTimeoutMs = 300L
private const val AutomationTapSlopPx = 10f
private const val AutomationPointHitRadiusPx = 14f
private const val AutomationSegmentHitRadiusPx = 12f
private const val AutomationCurveDragSensitivityPx = 96f
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
    // Always-fresh viewport for pointer-input closures that are not keyed on scroll changes.
    val currentViewport = rememberUpdatedState(viewport)
    val bpm by WorkspaceRepository.bpm.collectAsState()
    val gridType by WorkspaceRepository.gridType.collectAsState()
    val selectedRange = selections
        .filterIsInstance<Selectable.TimelineRange>()
        .firstOrNull { it.trackIndex == trackIndex }
    val explicitlySelectedPointIds = SelectionManager.selectedTimelineAutomationPointIds(
        trackIndex = trackIndex,
        lane = laneKey,
        currentSelections = selections
    )

    var dragState by remember(trackIndex, laneKey) {
        mutableStateOf<AutomationPointDragState?>(null)
    }
    var rangeDragState by remember(trackIndex, laneKey) {
        mutableStateOf<AutomationRangeDragState?>(null)
    }
    var lastTapState by remember(trackIndex, laneKey) {
        mutableStateOf<AutomationPointTapState?>(null)
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
            .pointerInput(
                laneKey,
                zoomLevel,
                renderedLane.points,
                currentIsMetaSelectionPressed
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Main
                    )
                    var cancelled = false
                    var upPosition: Offset? = null
                    var upTimeMillis = 0L

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if (change.isConsumed) {
                            cancelled = true
                            break
                        }
                        if (!change.pressed) {
                            upPosition = change.position
                            upTimeMillis = change.uptimeMillis
                            break
                        }
                    }

                    val releasePosition = upPosition ?: return@awaitEachGesture
                    if (cancelled || !isWithinTapSlop(down.position, releasePosition)) {
                        return@awaitEachGesture
                    }

                    val hitPoint = hitAutomationPoint(
                        points = renderedLane.points,
                        tapOffset = releasePosition,
                        zoomLevel = zoomLevel,
                        scrollOffsetPx = currentViewport.value.scrollX,
                        laneHeightPx = size.height.toFloat(),
                        target = normalizedLane.target
                    )

                    if (hitPoint != null) {
                        selectLane()
                        val isDoubleTap = lastTapState?.pointId == hitPoint.pointId &&
                            upTimeMillis - (lastTapState?.timeMillis ?: 0L) <= AutomationDoubleTapTimeoutMs

                        if (isDoubleTap) {
                            TimelineCommandSurface.deleteAutomationPoints(
                                trackIndex = trackIndex,
                                lane = laneKey,
                                pointIds = listOf(hitPoint.pointId)
                            )
                            lastTapState = null
                        } else {
                            if (currentIsMetaSelectionPressed) {
                                SelectionManager.toggleTimelineAutomationPoint(
                                    trackIndex = trackIndex,
                                    lane = laneKey,
                                    pointId = hitPoint.pointId
                                )
                            } else {
                                SelectionManager.selectTimelineAutomationPoints(
                                    trackIndex = trackIndex,
                                    lane = laneKey,
                                    pointIds = listOf(hitPoint.pointId)
                                )
                            }
                            lastTapState = AutomationPointTapState(
                                pointId = hitPoint.pointId,
                                timeMillis = upTimeMillis
                            )
                        }
                        return@awaitEachGesture
                    }

                    lastTapState = null
                    TimelineCommandSurface.createAutomationPoints(
                        trackIndex = trackIndex,
                        lane = laneKey,
                        points = listOf(
                            TimelineAutomationPoint(
                                timeMs = computeSnappedTimeFromContentX(
                                    currentViewport.value.screenToContentX(releasePosition.x), currentViewport.value.zoomX, bpm, gridType
                                ),
                                value = pointerOffsetToValue(
                                    y = releasePosition.y,
                                    laneHeightPx = size.height.toFloat(),
                                    target = normalizedLane.target
                                )
                            )
                        )
                    )
                }
            }
            .pointerInput(
                laneKey,
                zoomLevel,
                renderedLane.points,
                effectiveSelectedPointIds,
                currentIsAltPressed
            ) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val laneSelection = Selectable.TimelineAutomationLane(
                            trackIndex = trackIndex,
                            target = laneKey.target,
                            bindingId = laneKey.bindingId
                        )
                        val hitPoint = hitAutomationPoint(
                            points = renderedLane.points,
                            tapOffset = offset,
                            zoomLevel = zoomLevel,
                            scrollOffsetPx = currentViewport.value.scrollX,
                            laneHeightPx = size.height.toFloat(),
                            target = normalizedLane.target
                        )

                        if (hitPoint != null) {
                            val affectedPointIds = if (
                                effectiveSelectedPointIds.isNotEmpty() &&
                                hitPoint.pointId in effectiveSelectedPointIds
                            ) {
                                effectiveSelectedPointIds
                            } else {
                                SelectionManager.selectTimelineAutomationPoints(
                                    trackIndex = trackIndex,
                                    lane = laneKey,
                                    pointIds = listOf(hitPoint.pointId)
                                )
                                setOf(hitPoint.pointId)
                            }

                            val affectedPoints = renderedLane.points.filter { point ->
                                point.pointId in affectedPointIds
                            }

                            dragState = AutomationPointDragState(
                                beforePoints = affectedPoints,
                                afterPoints = affectedPoints,
                                draggedPointId = hitPoint.pointId,
                                dragStartOffset = offset,
                                mode = AutomationDragMode.Point
                            )
                            rangeDragState = null
                            return@detectDragGestures
                        }

                        if (currentIsAltPressed) {
                            val hitSegment = hitAutomationSegment(
                                points = renderedLane.points,
                                tapOffset = offset,
                                zoomLevel = zoomLevel,
                                scrollOffsetPx = currentViewport.value.scrollX,
                                laneHeightPx = size.height.toFloat(),
                                target = normalizedLane.target
                            )
                            if (hitSegment != null) {
                                SelectionManager.replaceSelections(listOf(laneSelection))
                                dragState = AutomationPointDragState(
                                    beforePoints = listOf(hitSegment.startPoint),
                                    afterPoints = listOf(hitSegment.startPoint),
                                    draggedPointId = hitSegment.startPoint.pointId,
                                    dragStartOffset = offset,
                                    mode = AutomationDragMode.Curve
                                )
                                rangeDragState = null
                                return@detectDragGestures
                            }
                        }

                        dragState = null
                        rangeDragState = AutomationRangeDragState(
                            startTimeMs = computeSnappedTimeFromContentX(currentViewport.value.screenToContentX(offset.x), currentViewport.value.zoomX, bpm, gridType),
                            endTimeMs = computeSnappedTimeFromContentX(currentViewport.value.screenToContentX(offset.x), currentViewport.value.zoomX, bpm, gridType)
                        )
                    },
                    onDrag = { change, dragAmount ->
                        val currentDragState = dragState
                        if (currentDragState != null) {
                            val updatedPoints = when (currentDragState.mode) {
                                AutomationDragMode.Point -> {
                                    val anchorBefore = currentDragState.beforePoints
                                        .firstOrNull { point -> point.pointId == currentDragState.draggedPointId }
                                        ?: return@detectDragGestures
                                    val anchorTime = computeSnappedTimeFromContentX(
                                        currentViewport.value.screenToContentX(change.position.x), currentViewport.value.zoomX, bpm, gridType
                                    )
                                    val minimumDeltaTime = -(
                                        currentDragState.beforePoints.minOfOrNull(TimelineAutomationPoint::timeMs)
                                            ?: 0L
                                    )
                                    val deltaTime = (anchorTime - anchorBefore.timeMs).coerceAtLeast(minimumDeltaTime)

                                    val anchorValue = pointerOffsetToValue(
                                        y = change.position.y,
                                        laneHeightPx = size.height.toFloat(),
                                        target = normalizedLane.target
                                    )
                                    val anchorDisplayValue = normalizedLane.target.valueToDisplayValue(anchorValue)
                                    val deltaDisplayValue = anchorDisplayValue -
                                        normalizedLane.target.valueToDisplayValue(anchorBefore.value)

                                    currentDragState.beforePoints.map { point ->
                                        val updatedDisplayValue =
                                            normalizedLane.target.valueToDisplayValue(point.value) + deltaDisplayValue
                                        point.copy(
                                            timeMs = (point.timeMs + deltaTime).coerceAtLeast(0L),
                                            value = normalizedLane.target.displayValueToValue(updatedDisplayValue)
                                        )
                                    }
                                }

                                AutomationDragMode.Curve -> {
                                    val currentPoint = currentDragState.afterPoints.first()
                                    listOf(
                                        currentPoint.copy(
                                            curve = (currentPoint.curve - (dragAmount.y / AutomationCurveDragSensitivityPx))
                                                .coerceIn(-1f, 1f)
                                        )
                                    )
                                }
                            }

                            if (updatedPoints != currentDragState.afterPoints) {
                                dragState = currentDragState.copy(afterPoints = updatedPoints)
                            }
                            change.consume()
                            return@detectDragGestures
                        }

                        val currentRangeDragState = rangeDragState ?: return@detectDragGestures
                        rangeDragState = currentRangeDragState.copy(
                            endTimeMs = computeSnappedTimeFromContentX(currentViewport.value.screenToContentX(change.position.x), currentViewport.value.zoomX, bpm, gridType)
                        )
                        change.consume()
                    },
                    onDragEnd = {
                        val currentDragState = dragState
                        val currentRangeDragState = rangeDragState
                        dragState = null
                        rangeDragState = null
                        lastTapState = null

                        if (currentDragState != null && currentDragState.afterPoints != currentDragState.beforePoints) {
                            TimelineCommandSurface.moveAutomationPoints(
                                trackIndex = trackIndex,
                                lane = laneKey,
                                changes = currentDragState.beforePoints.zip(currentDragState.afterPoints).map { (before, after) ->
                                    TimelineEditedAutomationPoint(
                                        before = before,
                                        after = after
                                    )
                                }
                            )
                            return@detectDragGestures
                        }

                        if (currentRangeDragState != null) {
                            val normalizedStart = minOf(
                                currentRangeDragState.startTimeMs,
                                currentRangeDragState.endTimeMs
                            )
                            val normalizedEnd = maxOf(
                                currentRangeDragState.startTimeMs,
                                currentRangeDragState.endTimeMs
                            )
                            if (normalizedEnd > normalizedStart) {
                                SelectionManager.replaceSelections(
                                    listOf(
                                        Selectable.TimelineAutomationLane(
                                            trackIndex = trackIndex,
                                            target = laneKey.target,
                                            bindingId = laneKey.bindingId
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
                    },
                    onDragCancel = {
                        dragState = null
                        rangeDragState = null
                    }
                )
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
                            isSelectedPoint -> 2.2.dp.toPx()
                            else -> 1.8.dp.toPx()
                        },
                        center = Offset(pointX, pointY)
                    )
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
    for (step in 1..AutomationCurvePathSteps) {
        val progress = step.toFloat() / AutomationCurvePathSteps.toFloat()
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

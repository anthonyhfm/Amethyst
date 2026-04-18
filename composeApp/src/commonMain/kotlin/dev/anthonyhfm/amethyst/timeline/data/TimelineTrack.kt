package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import kotlin.math.abs

abstract class TimelineTrack<E : TimelineEntry> {
    open var name: String = ""
    abstract var trackId: String
    abstract val kind: TimelineTrackKind
    abstract var volume: Float
    abstract var isMuted: Boolean
    abstract var isSoloed: Boolean
    abstract val automationLanes: MutableList<TimelineAutomationLane>
    open val entries = mutableMapOf<Long, E>()

    fun automationLane(key: TimelineAutomationLaneKey): TimelineAutomationLane? {
        val normalizedKey = key.normalized()
        return automationLanes.firstOrNull { it.key == normalizedKey }
    }

    fun automationLane(
        target: TimelineTrackAutomationTarget,
        bindingId: String? = null
    ): TimelineAutomationLane? {
        return automationLane(
            key = TimelineAutomationLaneKey(
                target = target,
                bindingId = bindingId
            )
        )
    }

    fun automationValueAt(
        target: TimelineTrackAutomationTarget,
        timeMs: Long,
        defaultValue: Float = baseAutomationValue(target)
    ): Float {
        return automationLane(target)?.valueAt(timeMs, defaultValue)
            ?: target.normalizeValue(defaultValue)
    }

    fun baseAutomationValue(
        target: TimelineTrackAutomationTarget,
        bindingId: String? = null
    ): Float = when (target) {
        TimelineTrackAutomationTarget.VOLUME -> volume
    }

    fun setBaseAutomationValue(
        target: TimelineTrackAutomationTarget,
        value: Float,
        bindingId: String? = null
    ) {
        when (target) {
            TimelineTrackAutomationTarget.VOLUME -> volume = target.normalizeValue(value)
        }
    }

    fun volumeAt(timeMs: Long): Float {
        return automationValueAt(
            target = TimelineTrackAutomationTarget.VOLUME,
            timeMs = timeMs
        )
    }

    fun isPlaybackEnabled(anySoloedTrack: Boolean): Boolean {
        return !isMuted && (!anySoloedTrack || isSoloed)
    }

    fun isAutomationBindingValid(key: TimelineAutomationLaneKey): Boolean {
        return key.normalized().bindingId == null
    }

    private fun mutateAutomationLane(
        key: TimelineAutomationLaneKey,
        createIfMissing: Boolean = false,
        mutate: (TimelineAutomationLane?) -> TimelineAutomationLane?
    ): Boolean {
        val normalizedKey = key.normalized()
        if (!isAutomationBindingValid(normalizedKey)) return false

        val existingIndex = automationLanes.indexOfFirst { it.key == normalizedKey }
        val existingLane = automationLanes.getOrNull(existingIndex)
        if (existingLane == null && !createIfMissing) return false

        val updatedLane = mutate(existingLane)
            ?.normalized()
            ?.takeIf { isAutomationBindingValid(it.key) }

        if (existingLane == updatedLane) return false

        when {
            updatedLane == null -> {
                if (existingIndex < 0) return false
                automationLanes.removeAt(existingIndex)
            }

            existingIndex >= 0 -> automationLanes[existingIndex] = updatedLane
            else -> automationLanes.add(updatedLane)
        }

        normalizeAutomationState()
        return true
    }

    fun upsertAutomationLane(lane: TimelineAutomationLane) {
        val normalizedLane = lane.normalized()
        if (!isAutomationBindingValid(normalizedLane.key)) return

        val existingIndex = automationLanes.indexOfFirst { it.key == normalizedLane.key }
        if (existingIndex >= 0) {
            automationLanes[existingIndex] = normalizedLane
        } else {
            automationLanes.add(normalizedLane)
        }
        normalizeAutomationState()
    }

    fun removeAutomationLane(key: TimelineAutomationLaneKey) {
        val normalizedKey = key.normalized()
        automationLanes.removeAll { it.key == normalizedKey }
    }

    fun removeAutomationLane(
        target: TimelineTrackAutomationTarget,
        bindingId: String? = null
    ) {
        removeAutomationLane(
            key = TimelineAutomationLaneKey(
                target = target,
                bindingId = bindingId
            )
        )
    }

    fun setAutomationLaneVisibility(
        key: TimelineAutomationLaneKey,
        visible: Boolean
    ): Boolean {
        val normalizedKey = key.normalized()
        return mutateAutomationLane(
            key = normalizedKey,
            createIfMissing = visible
        ) { existingLane ->
            val resolvedLane = existingLane ?: TimelineAutomationLane(
                target = normalizedKey.target,
                bindingId = normalizedKey.bindingId,
                visible = visible
            )

            when {
                existingLane == null -> resolvedLane
                existingLane.visible == visible -> existingLane
                else -> existingLane.copy(visible = visible)
            }
        }
    }

    fun setAutomationLaneEnabled(
        key: TimelineAutomationLaneKey,
        enabled: Boolean
    ): Boolean {
        return mutateAutomationLane(key) { existingLane ->
            if (existingLane == null || existingLane.enabled == enabled) {
                existingLane
            } else {
                existingLane.copy(enabled = enabled)
            }
        }
    }

    fun createAutomationPoints(
        key: TimelineAutomationLaneKey,
        points: Collection<TimelineAutomationPoint>
    ): Boolean {
        if (points.isEmpty()) return false

        val normalizedKey = key.normalized()
        return mutateAutomationLane(
            key = normalizedKey,
            createIfMissing = true
        ) { existingLane ->
            val baseLane = existingLane ?: TimelineAutomationLane(
                target = normalizedKey.target,
                bindingId = normalizedKey.bindingId,
                visible = true,
                enabled = true
            )
            val updatedLane = baseLane
                .withPointUpdates(points)
                .copy(visible = true)

            if (updatedLane == baseLane) existingLane else updatedLane
        }
    }

    fun moveAutomationPoints(
        key: TimelineAutomationLaneKey,
        points: Collection<TimelineAutomationPoint>
    ): Boolean {
        if (points.isEmpty()) return false

        return mutateAutomationLane(key) { existingLane ->
            existingLane ?: return@mutateAutomationLane null
            val existingPointIds = existingLane.points.mapTo(mutableSetOf(), TimelineAutomationPoint::pointId)
            val movedPoints = points.filter { it.pointId in existingPointIds }
            if (movedPoints.isEmpty()) {
                existingLane
            } else {
                existingLane.withPointUpdates(movedPoints)
            }
        }
    }

    fun deleteAutomationPoints(
        key: TimelineAutomationLaneKey,
        pointIds: Collection<String>
    ): Boolean {
        if (pointIds.isEmpty()) return false

        return mutateAutomationLane(key) { existingLane ->
            existingLane ?: return@mutateAutomationLane null
            existingLane.withoutPoints(pointIds)
        }
    }

    fun normalizeAutomationState() {
        TimelineTrackAutomationTarget.globalEntries.forEach { target ->
            setBaseAutomationValue(
                target = target,
                value = baseAutomationValue(target)
            )
        }

        val normalizedLanesByKey = linkedMapOf<TimelineAutomationLaneKey, TimelineAutomationLane>()
        automationLanes.forEach { lane ->
            val normalizedLane = lane.normalized()
            val normalizedKey = normalizedLane.key
            if (normalizedKey.bindingId == null) {
                normalizedLanesByKey[normalizedKey] = normalizedLane
            }
        }

        automationLanes.clear()
        automationLanes.addAll(normalizedLanesByKey.values)
    }

    fun copyAutomationStateFrom(other: TimelineTrack<*>) {
        TimelineTrackAutomationTarget.globalEntries.forEach { target ->
            setBaseAutomationValue(
                target = target,
                value = other.baseAutomationValue(target)
            )
        }

        automationLanes.clear()
        automationLanes.addAll(other.automationLanes.map { it.deepCopy() })
        normalizeAutomationState()
    }

    fun copyMixerStateFrom(other: TimelineTrack<*>) {
        isMuted = other.isMuted
        isSoloed = other.isSoloed
        copyAutomationStateFrom(other)
    }

    fun automationLaneInRange(
        key: TimelineAutomationLaneKey,
        startMs: Long,
        endMs: Long
    ): TimelineAutomationLane? {
        if (endMs <= startMs) return null

        val normalizedKey = key.normalized()
        val lane = automationLane(normalizedKey) ?: return null
        return lane.clippedToRange(
            startMs = startMs,
            endMs = endMs,
            baseValue = baseAutomationValue(
                target = normalizedKey.target,
                bindingId = normalizedKey.bindingId
            )
        )
    }

    fun automationLanesInRange(
        startMs: Long,
        endMs: Long
    ): List<TimelineAutomationLane> {
        if (endMs <= startMs) return emptyList()

        return TimelineTrackAutomationTarget.globalEntries.mapNotNull { target ->
            val baseValue = baseAutomationValue(target)
            automationLane(target)?.clippedToRange(
                startMs = startMs,
                endMs = endMs,
                baseValue = baseValue
            ) ?: if (abs(baseValue - target.defaultValue) > 0.0005f) {
                TimelineAutomationLane(
                    target = target,
                    points = listOf(
                        TimelineAutomationPoint(timeMs = 0L, value = baseValue),
                        TimelineAutomationPoint(timeMs = endMs - startMs, value = baseValue)
                    )
                )
            } else {
                null
            }
        }
    }

    fun pasteAutomationLanes(
        startMs: Long,
        lanes: Collection<TimelineAutomationLane>
    ): Boolean {
        if (lanes.isEmpty()) return false

        var didChange = false
        lanes.forEach { pastedLane ->
            val normalizedLane = pastedLane.normalized()
            val shiftedPoints = normalizedLane.points.map { point ->
                point.copy(
                    timeMs = startMs + point.timeMs,
                    pointId = UUID.randomUUID()
                )
            }

            didChange = createAutomationPoints(
                key = normalizedLane.key,
                points = shiftedPoints
            ) || didChange
            didChange = setAutomationLaneVisibility(
                key = normalizedLane.key,
                visible = true
            ) || didChange
            didChange = setAutomationLaneEnabled(
                key = normalizedLane.key,
                enabled = normalizedLane.enabled
            ) || didChange
        }

        return didChange
    }

    fun deleteAutomationRange(
        key: TimelineAutomationLaneKey,
        startMs: Long,
        endMs: Long
    ): Boolean {
        if (endMs <= startMs) return false

        return mutateAutomationLane(key) { existingLane ->
            val lane = existingLane?.normalized() ?: return@mutateAutomationLane null
            val baseValue = baseAutomationValue(
                target = lane.target,
                bindingId = lane.bindingId
            )
            val pointsToDelete = lane.points
                .filter { point -> point.timeMs in startMs..endMs }
                .map(TimelineAutomationPoint::pointId)

            if (pointsToDelete.isEmpty()) return@mutateAutomationLane existingLane

            val leftBoundaryPoint = lane.boundaryPointAt(
                timeMs = startMs,
                baseValue = baseValue
            )?.clearCurveHandle()
            val rightBoundaryPoint = lane.boundaryPointAt(
                timeMs = endMs,
                baseValue = baseValue
            )?.clearCurveHandle()
            val remainingPoints = lane.points.filterNot { point ->
                point.pointId in pointsToDelete
            }

            val boundaryPoints = buildList {
                val hasPointBeforeRange = remainingPoints.any { it.timeMs < startMs }
                val hasPointAfterOrAtEnd = remainingPoints.any { it.timeMs >= endMs }
                if (hasPointBeforeRange && hasPointAfterOrAtEnd && leftBoundaryPoint != null) {
                    add(leftBoundaryPoint)
                }

                val hasPointAtOrBeforeStart = remainingPoints.any { it.timeMs <= startMs }
                val hasPointAfterRange = remainingPoints.any { it.timeMs > endMs }
                if (hasPointAtOrBeforeStart && hasPointAfterRange && rightBoundaryPoint != null) {
                    add(rightBoundaryPoint)
                }
            }

            lane.copy(points = remainingPoints + boundaryPoints)
                .normalized()
                .rebuildSegmentHandlesFromSource(
                    sourceLane = lane,
                    sourceBaseValue = baseValue
                )
        }
    }

    fun duplicateAutomationRange(
        key: TimelineAutomationLaneKey,
        startMs: Long,
        endMs: Long
    ): Boolean {
        if (endMs <= startMs) return false
        val clippedLane = automationLaneInRange(
            key = key,
            startMs = startMs,
            endMs = endMs
        ) ?: return false

        return pasteAutomationLanes(
            startMs = endMs,
            lanes = listOf(clippedLane)
        )
    }

    fun deleteAutomationRange(
        startMs: Long,
        endMs: Long
    ): Boolean {
        if (endMs <= startMs) return false

        var didChange = false
        automationLanes
            .map(TimelineAutomationLane::key)
            .forEach { laneKey ->
                didChange = deleteAutomationRange(
                    key = laneKey,
                    startMs = startMs,
                    endMs = endMs
                ) || didChange
            }

        return didChange
    }

    fun duplicateAutomationRange(
        startMs: Long,
        endMs: Long
    ): Boolean {
        if (endMs <= startMs) return false
        val rangeLanes = automationLanesInRange(startMs, endMs)
        var didChange = false
        // Clear all automation points that lie strictly after endMs so the
        // pasted copy starts on a clean slate and does not merge with existing data.
        TimelineTrackAutomationTarget.globalEntries.forEach { target ->
            val lane = automationLane(target)?.normalized() ?: return@forEach
            val pointsToDelete = lane.points
                .filter { point -> point.timeMs > endMs }
                .map(TimelineAutomationPoint::pointId)
            if (pointsToDelete.isNotEmpty()) {
                didChange = deleteAutomationPoints(key = lane.key, pointIds = pointsToDelete) || didChange
            }
        }
        didChange = pasteAutomationLanes(startMs = endMs, lanes = rangeLanes) || didChange
        return didChange
    }
}

fun TimelineTrack<*>.deepCopy(
    preserveTrackIdentity: Boolean = true
): TimelineTrack<*> =
    when (this) {
        is AudioTimelineTrack -> copyWithEntries(
            entriesToCopy = entries.mapValues { (_, entry) ->
                entry.copy()
            },
            preserveTrackIdentity = preserveTrackIdentity
        )

        is MidiTimelineTrack -> copyWithEntries(
            entriesToCopy = entries.mapValues { (_, entry) ->
                entry.copy(
                    notes = entry.notes.map { note ->
                        note.copy(led = note.led.copy())
                    }
                )
            },
            preserveTrackIdentity = preserveTrackIdentity
        )

        else -> error("Unsupported timeline track type")
    }

internal fun TimelineAutomationLane.clippedToRange(
    startMs: Long,
    endMs: Long,
    baseValue: Float
): TimelineAutomationLane? {
    if (endMs <= startMs) return null

    val normalizedLane = normalized()
    val durationMs = endMs - startMs
    val pointsWithinRange = normalizedLane.points
        .filter { point -> point.timeMs in startMs..endMs }
        .map { point ->
            point.copy(timeMs = point.timeMs - startMs)
        }

    if (pointsWithinRange.isEmpty() && abs(baseValue - target.defaultValue) <= 0.0005f && normalizedLane.points.isEmpty()) {
        return null
    }

    val startBoundaryPoint = normalizedLane.boundaryPointAt(
        timeMs = startMs,
        baseValue = baseValue
    )
    val endBoundaryPoint = normalizedLane.boundaryPointAt(
        timeMs = endMs,
        baseValue = baseValue
    )?.clearCurveHandle()

    val clippedPoints = buildList {
        if (pointsWithinRange.firstOrNull()?.timeMs != 0L && startBoundaryPoint != null) {
            add(startBoundaryPoint.copy(timeMs = 0L))
        }
        addAll(pointsWithinRange)
        if (lastOrNull()?.timeMs != durationMs && endBoundaryPoint != null) {
            add(endBoundaryPoint.copy(timeMs = durationMs))
        }
    }

    return copy(
        points = clippedPoints,
        visible = true
    ).normalized().rebuildSegmentHandlesFromSource(
        sourceLane = normalizedLane,
        sourceBaseValue = baseValue,
        sourceTimeOffset = startMs
    )
}

internal fun TimelineAutomationLane.boundaryPointAt(
    timeMs: Long,
    baseValue: Float
): TimelineAutomationPoint? {
    val normalizedLane = normalized()
    normalizedLane.points.firstOrNull { point -> point.timeMs == timeMs }?.let { point ->
        return point.copy(pointId = UUID.randomUUID())
    }

    if (normalizedLane.points.isEmpty() && abs(baseValue - target.defaultValue) <= 0.0005f) {
        return null
    }

    val previousPoint = normalizedLane.points.lastOrNull { point -> point.timeMs < timeMs }
    val nextPoint = normalizedLane.points.firstOrNull { point -> point.timeMs > timeMs }

    return TimelineAutomationPoint(
        timeMs = timeMs,
        value = normalizedLane.valueAt(timeMs, baseValue),
        curve = if (previousPoint != null && nextPoint != null) previousPoint.curve else 0f
    )
}

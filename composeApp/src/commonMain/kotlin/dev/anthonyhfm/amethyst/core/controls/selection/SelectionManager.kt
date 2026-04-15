package dev.anthonyhfm.amethyst.core.controls.selection

import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


object SelectionManager {
    val selections: MutableStateFlow<List<Selectable>> = MutableStateFlow(emptyList())

    private val _activeTimelineAutomationLane = MutableStateFlow<Selectable.TimelineAutomationLane?>(null)
    val activeTimelineAutomationLane: StateFlow<Selectable.TimelineAutomationLane?> =
        _activeTimelineAutomationLane.asStateFlow()

    private var _lastSelectedTimelineTrackIndex: Int? = null
    val lastSelectedTimelineTrackIndex: Int?
        get() = _lastSelectedTimelineTrackIndex

    // Track last selected device for range selection
    private var lastSelectedChainDevice: Selectable.ChainDevice? = null

    // Fire-and-forget request to trigger renaming on a specific list item.
    // Use parentUUID to avoid direct dependency on device classes.
    sealed class RenameTarget @OptIn(ExperimentalTime::class) constructor(
        open val token: Long = Clock.System.now().toEpochMilliseconds()
    ) {
        data class GroupItem @OptIn(ExperimentalTime::class) constructor(
            val parentUUID: String,
            val groupIndex: Int,
            override val token: Long = Clock.System.now().toEpochMilliseconds()
        ) : RenameTarget(token)

        data class Track @OptIn(ExperimentalTime::class) constructor(
            val trackIndex: Int,
            override val token: Long = Clock.System.now().toEpochMilliseconds()
        ) : RenameTarget(token)

        data class TimelineEntry @OptIn(ExperimentalTime::class) constructor(
            val trackIndex: Int,
            val entryStartMs: Long,
            override val token: Long = Clock.System.now().toEpochMilliseconds()
        ) : RenameTarget(token)
    }

    // Fire-and-forget request to trigger renaming on a specific list item.
    // SharedFlow with buffer=1 ensures the signal is delivered even if the
    // observer recomposes between emit and collect, preventing lost signals.
    val renameRequest: MutableSharedFlow<RenameTarget> = MutableSharedFlow(extraBufferCapacity = 1)

    private fun syncSelectionState() {
        _activeTimelineAutomationLane.value =
            selections.value.filterIsInstance<Selectable.TimelineAutomationLane>().lastOrNull()
        lastSelectedChainDevice =
            selections.value.filterIsInstance<Selectable.ChainDevice>().lastOrNull()
        _lastSelectedTimelineTrackIndex =
            selections.value.filterIsInstance<Selectable.TimelineTrack>().lastOrNull()?.trackIndex
    }

    fun replaceSelections(updatedSelections: List<Selectable>) {
        selections.value = updatedSelections
        syncSelectionState()
    }

    fun select(element: Selectable, single: Boolean = true) {
        if (single) {
            selections.value = emptyList()
        } else if (selections.value.find { it::class == element::class } == null) {
            selections.value = emptyList()
        }

        if (selections.value.find { it.selectionUUID == element.selectionUUID } == null) {
            selections.value += element
        }

        syncSelectionState()
    }

    fun selectTimelineAutomationLane(
        trackIndex: Int,
        target: TimelineTrackAutomationTarget,
        bindingId: String? = null,
        single: Boolean = true
    ) {
        select(
            element = Selectable.TimelineAutomationLane(
                trackIndex = trackIndex,
                target = target,
                bindingId = bindingId
            ),
            single = single
        )
    }

    fun clearTimelineAutomationSelections() {
        replaceSelections(
            selections.value.filterNot {
                it is Selectable.TimelineAutomationLane || it is Selectable.TimelineAutomationPoint
            }
        )
    }

    fun selectedTimelineAutomationPointIds(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        currentSelections: List<Selectable> = selections.value
    ): Set<String> {
        val normalizedLane = lane.normalized()
        return currentSelections
            .filterIsInstance<Selectable.TimelineAutomationPoint>()
            .filter { selection ->
                selection.trackIndex == trackIndex && selection.laneKey == normalizedLane
            }
            .mapTo(mutableSetOf(), Selectable.TimelineAutomationPoint::pointId)
    }

    fun selectTimelineAutomationPoints(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        pointIds: Collection<String>
    ) {
        val normalizedLane = lane.normalized()
        val normalizedPointIds = pointIds
            .filter(String::isNotBlank)
            .distinct()

        replaceSelections(
            listOf(
                Selectable.TimelineAutomationLane(
                    trackIndex = trackIndex,
                    target = normalizedLane.target,
                    bindingId = normalizedLane.bindingId
                )
            ) + normalizedPointIds.map { pointId ->
                Selectable.TimelineAutomationPoint(
                    trackIndex = trackIndex,
                    target = normalizedLane.target,
                    bindingId = normalizedLane.bindingId,
                    pointId = pointId
                )
            }
        )
    }

    fun toggleTimelineAutomationPoint(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        pointId: String
    ) {
        val normalizedLane = lane.normalized()
        val existingPointIds = selectedTimelineAutomationPointIds(
            trackIndex = trackIndex,
            lane = normalizedLane
        ).toMutableSet()

        if (!existingPointIds.add(pointId)) {
            existingPointIds.remove(pointId)
        }

        selectTimelineAutomationPoints(
            trackIndex = trackIndex,
            lane = normalizedLane,
            pointIds = existingPointIds
        )
    }

    fun selectedTimelineTrackIndices(
        currentSelections: List<Selectable> = selections.value
    ): List<Int> {
        return currentSelections
            .filterIsInstance<Selectable.TimelineTrack>()
            .map(Selectable.TimelineTrack::trackIndex)
            .distinct()
    }

    fun selectTimelineTracks(
        trackIndices: Collection<Int>,
        anchorTrackIndex: Int? = trackIndices.lastOrNull()
    ) {
        val normalizedTrackIndices = trackIndices
            .filter { it >= 0 }
            .distinct()
            .toList()

        if (normalizedTrackIndices.isEmpty()) {
            clear()
            return
        }

        replaceSelections(
            normalizedTrackIndices.map { trackIndex ->
                Selectable.TimelineTrack(trackIndex = trackIndex)
            }
        )
        _lastSelectedTimelineTrackIndex = anchorTrackIndex
    }

    fun toggleTimelineTrack(trackIndex: Int) {
        val currentSelections = selections.value
        if (currentSelections.any { it !is Selectable.TimelineTrack }) {
            select(Selectable.TimelineTrack(trackIndex = trackIndex))
            return
        }

        val updatedTrackIndices = currentSelections
            .filterIsInstance<Selectable.TimelineTrack>()
            .map(Selectable.TimelineTrack::trackIndex)
            .toMutableList()

        if (trackIndex in updatedTrackIndices) {
            updatedTrackIndices.remove(trackIndex)
        } else {
            updatedTrackIndices += trackIndex
        }

        if (updatedTrackIndices.isEmpty()) {
            clear()
        } else {
            selectTimelineTracks(
                trackIndices = updatedTrackIndices,
                anchorTrackIndex = if (trackIndex in updatedTrackIndices) {
                    trackIndex
                } else {
                    updatedTrackIndices.lastOrNull()
                }
            )
        }
    }

    /**
     * Perform range selection for chain devices from the last selected device to the target device.
     * @param targetDevice The device to select up to
     * @param devicesInChain The complete list of devices in the chain in order
     */
    fun selectRangeInChain(targetDevice: Selectable.ChainDevice, devicesInChain: List<dev.anthonyhfm.amethyst.devices.GenericChainDevice<*>>) {
        val lastDevice = lastSelectedChainDevice
        
        // If no last device or different parent chain, just select the target
        if (lastDevice == null || lastDevice.parent != targetDevice.parent) {
            select(targetDevice, single = true)
            return
        }
        
        // Find indices of start and end devices
        val startIndex = devicesInChain.indexOfFirst { it.selectionUUID == lastDevice.device.selectionUUID }
        val endIndex = devicesInChain.indexOfFirst { it.selectionUUID == targetDevice.device.selectionUUID }
        
        if (startIndex == -1 || endIndex == -1) {
            select(targetDevice, single = true)
            return
        }
        
        // Clear and select range
        clear()
        
        val range = if (startIndex <= endIndex) {
            startIndex..endIndex
        } else {
            endIndex..startIndex
        }
        
        range.forEach { index ->
            val device = devicesInChain[index]
            select(
                Selectable.ChainDevice(
                    parent = targetDevice.parent,
                    device = device
                ),
                single = false
            )
        }
        
        // Update last selected to the target
        lastSelectedChainDevice = targetDevice
    }

    fun clear() {
        replaceSelections(emptyList())
    }
}

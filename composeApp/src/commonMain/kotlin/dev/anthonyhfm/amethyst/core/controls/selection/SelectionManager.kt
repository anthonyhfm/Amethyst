package dev.anthonyhfm.amethyst.core.controls.selection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


object SelectionManager {
    val selections: MutableStateFlow<List<Selectable>> = MutableStateFlow(emptyList())
    
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

    // Observers (e.g., Group/Multi group items) can listen to this to toggle rename mode.
    val renameRequest: MutableStateFlow<RenameTarget?> = MutableStateFlow(null)

    fun select(element: Selectable, single: Boolean = true) {
        if (single) {
            selections.value = emptyList()
        } else if (selections.value.find { it::class.simpleName == element::class.simpleName } == null) {
            selections.value = emptyList()
        }

        if (selections.value.find { it.selectionUUID == element.selectionUUID } == null) {
            selections.value += element
        }
        
        // Track last selected chain device for range selection
        // Update for both single and multi-selection to maintain proper anchor
        if (element is Selectable.ChainDevice) {
            lastSelectedChainDevice = element
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
        selections.value = emptyList()
        lastSelectedChainDevice = null
    }
}
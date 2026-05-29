package dev.anthonyhfm.amethyst.core.controls.automapping

import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDevice
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.flow.update

internal object AutomappingChainMutation {
    
    fun toggleClipOnPad(
        targetGroup: Group,
        launchpadId: String,
        localX: Int,
        localY: Int,
        clip: AutomappingSelectedClip,
    ): Boolean {
        val targetDevice = buildChainDeviceFromAutomappingClip(clip) ?: return false
        val targetState = StateChain.packDevice(targetDevice)
        val padFilter = LaunchpadPadFilter(launchpadId, localX, localY)

        // 1. Locate all CoordinateFilterChainDevices and get the first one (or create)
        val allCoordFilters = targetGroup.chain.devices.value.filterIsInstance<CoordinateFilterChainDevice>()
        val coordFilter = allCoordFilters.firstOrNull() ?: CoordinateFilterChainDevice()
        
        // 2. Locate all clip devices
        val allClipDevices = targetGroup.chain.devices.value.filter { it is SampleChainDevice || it is PianoRollChainDevice }
        val existingClipDevice = allClipDevices.firstOrNull()
        
        // 3. Determine final clip device
        val finalClipDevice = if (existingClipDevice != null && StateChain.packDevice(existingClipDevice) == targetState) {
            existingClipDevice
        } else {
            targetDevice
        }
        
        // 4. Forcefully remove ALL of them to clean up any duplicates from older bugs
        allCoordFilters.forEach { 
            targetGroup.chain.remove(it.selectionUUID, fromUser = false) 
        }
        allClipDevices.forEach { 
            targetGroup.chain.remove(it.selectionUUID, fromUser = false) 
        }
        
        // 5. Re-insert them in the EXACT order: [CoordinateFilter, ClipDevice]
        targetGroup.chain.add(coordFilter, 0, fromUser = false)
        targetGroup.chain.add(finalClipDevice, 1, fromUser = false)
        
        // 6. Toggle the pad in the CoordinateFilterChainDevice
        val currentFilters = coordFilter.state.value.padFilters
        if (currentFilters.contains(padFilter)) {
            // Remove
            coordFilter.state.update { it.copy(padFilters = currentFilters - padFilter) }
        } else {
            // Add
            coordFilter.state.update { it.copy(padFilters = currentFilters + padFilter) }
        }
        
        return true
    }
}

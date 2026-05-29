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

        val devices = targetGroup.chain.devices.value
        
        // 1. Ensure CoordinateFilterChainDevice exists
        val coordFilter = devices.filterIsInstance<CoordinateFilterChainDevice>().firstOrNull() ?: run {
            val newFilter = CoordinateFilterChainDevice()
            targetGroup.chain.add(newFilter, 0, fromUser = false)
            newFilter
        }
        
        // 2. Locate existing clip device
        val existingClipDevice = targetGroup.chain.devices.value.find { it is SampleChainDevice || it is PianoRollChainDevice }
        
        if (existingClipDevice != null) {
            val existingState = StateChain.packDevice(existingClipDevice)
            if (existingState != targetState) {
                // The user selected a DIFFERENT clip but is mapping to the SAME group.
                // Replace the existing clip device with the new one.
                val idx = targetGroup.chain.devices.value.indexOf(existingClipDevice)
                targetGroup.chain.remove(existingClipDevice.selectionUUID)
                targetGroup.chain.add(targetDevice, idx.coerceAtLeast(1), fromUser = false)
            }
        } else {
            // No clip device found. Add it right after the CoordinateFilterChainDevice.
            val coordIdx = targetGroup.chain.devices.value.indexOf(coordFilter)
            targetGroup.chain.add(targetDevice, coordIdx + 1, fromUser = false)
        }
        
        // 3. Toggle the pad in the CoordinateFilterChainDevice
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

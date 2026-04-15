package dev.anthonyhfm.amethyst.core.controls.automapping

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import kotlinx.coroutines.flow.update

internal object AutomappingChainMutation {
    fun ensurePadContainer(
        targetGroup: Group,
        launchpadId: String,
        localX: Int,
        localY: Int,
    ): MultiGroupChainDevice {
        findPadContainer(
            chain = targetGroup.chain,
            launchpadId = launchpadId,
            localX = localX,
            localY = localY,
        )?.let { return it }

        val coordinateFilter = CoordinateFilterChainDevice().apply {
            state.update { currentState ->
                currentState.copy(
                    padFilters = listOf(
                        LaunchpadPadFilter(
                            launchpadId = launchpadId,
                            localX = localX,
                            localY = localY,
                        )
                    )
                )
            }
        }

        val multiGroup = MultiGroupChainDevice()
        targetGroup.chain.add(coordinateFilter)
        targetGroup.chain.add(multiGroup)
        return multiGroup
    }

    fun findPadContainer(
        chain: Chain,
        launchpadId: String,
        localX: Int,
        localY: Int,
    ): MultiGroupChainDevice? {
        val expectedFilter = LaunchpadPadFilter(
            launchpadId = launchpadId,
            localX = localX,
            localY = localY,
        )

        val devices = chain.devices.value
        for (index in 0 until devices.lastIndex) {
            val coordinateFilter = devices[index] as? CoordinateFilterChainDevice ?: continue
            val multiGroup = devices[index + 1] as? MultiGroupChainDevice ?: continue
            val padFilters = coordinateFilter.state.value.padFilters

            if (padFilters.size == 1 && padFilters.single() == expectedFilter) {
                return multiGroup
            }
        }

        return null
    }

    fun appendClipToMultiGroup(
        multiGroup: MultiGroupChainDevice,
        clip: AutomappingSelectedClip,
    ): Boolean {
        val targetDevice = buildChainDeviceFromAutomappingClip(clip) ?: return false
        val existingGroups = multiGroup.state.value.groups
        val initialGroup = existingGroups.singleOrNull()
            ?.takeIf { group -> group.chain.devices.value.isEmpty() }

        if (initialGroup != null) {
            initialGroup.chain.add(targetDevice, fromUser = false)
            return true
        }

        val newGroup = Group(
            name = clip.displayName.ifBlank { "Chain #" },
            chain = Chain().apply {
                signalExit = { signals ->
                    multiGroup.signalExit?.invoke(signals)
                }
                add(targetDevice, fromUser = false)
            }
        )

        multiGroup.insertGroupWithUndo(
            group = newGroup,
            selectInsertedGroup = false,
        )
        return true
    }
}

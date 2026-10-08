package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

internal fun StateChain.bindApolloLaunchpads(launchpadId: String, launchpadIds: List<String>): StateChain = copy(
    devices = devices.map { device ->
        val bound = when (device) {
            is CoordinateFilterChainDeviceState -> device.copy(
                followSignalLaunchpad = true,
            )
            is KeyframesChainDeviceState -> device.copy(
                apolloLaunchpadId = launchpadId,
                rootKeyLaunchpadId = launchpadId,
                frames = device.frames.map { frame ->
                    frame.copy(entries = frame.entries.map { entry ->
                        entry.copy(launchpadId = launchpadId, localX = entry.x, localY = entry.y)
                    })
                },
            )
            is OffsetChainDeviceState -> device.copy(
                targetLaunchpadId = device.targetLaunchpadIndex?.let { launchpadIds.getOrNull(it) },
            )
            is GroupChainDeviceState -> device.copy(
                groups = device.groups.map { group ->
                    group.copy(stateChain = group.stateChain.bindApolloLaunchpads(launchpadId = launchpadId, launchpadIds = launchpadIds))
                },
            )
            is MultiGroupChainDeviceState -> device.copy(
                groups = device.groups.map { group ->
                    group.copy(stateChain = group.stateChain.bindApolloLaunchpads(launchpadId = launchpadId, launchpadIds = launchpadIds))
                },
                preprocessChain = device.preprocessChain.bindApolloLaunchpads(launchpadId = launchpadId, launchpadIds = launchpadIds),
            )
            is ChokeChainDeviceState -> device.copy(
                stateChain = device.stateChain.bindApolloLaunchpads(launchpadId = launchpadId, launchpadIds = launchpadIds),
            )
            else -> device
        }
        bound.isMuted = device.isMuted
        bound
    },
)

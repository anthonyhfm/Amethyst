package dev.anthonyhfm.amethyst.workspace

import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.devicesDepthFirst
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.mask.MaskChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.rotatePadCoordinates

internal class LaunchpadBindingRemapper(
    private val launchpadId: String,
    private val position: Offset,
    private val oldLayout: LaunchpadLayout,
    newLayout: LaunchpadLayout,
    rotationDegrees: Float = 0f,
) {
    private val oldMainOrigin = oldLayout.rotatedMainOrigin(rotationDegrees = rotationDegrees)
    private val newMainOrigin = newLayout.rotatedMainOrigin(rotationDegrees = rotationDegrees)
    val deltaX: Int = newMainOrigin.first - oldMainOrigin.first
    val deltaY: Int = newMainOrigin.second - oldMainOrigin.second

    private fun LaunchpadLayout.rotatedMainOrigin(rotationDegrees: Float): Pair<Int, Int> {
        val corners = listOf(mainOffsetX, mainGridMaxX).flatMap { x ->
            listOf(mainOffsetY, mainGridMaxY).map { y ->
                val (rotatedX, rotatedY) = rotatePadCoordinates(
                    x = x,
                    y = rows - 1 - y,
                    layout = this,
                    rotationDegrees = rotationDegrees,
                )
                rotatedX to rows - 1 - rotatedY
            }
        }
        return corners.minOf { it.first } to corners.minOf { it.second }
    }

    private fun ownsGlobal(x: Int, y: Int): Boolean =
        x - position.x.toInt() in 0 until oldLayout.cols &&
            y - position.y.toInt() in 0 until oldLayout.rows

    fun remap(state: CoordinateFilterChainDeviceState): CoordinateFilterChainDeviceState {
        val globalFilters = state.globalFilters + if (state.padFilters.isEmpty()) state.filters else emptyList()
        return state.copy(
            filters = if (state.padFilters.isEmpty()) emptyList() else state.filters,
            globalFilters = globalFilters.filterNot { (x, y) -> ownsGlobal(x = x, y = y) },
            padFilters = state.padFilters.map { filter ->
                if (filter.launchpadId == launchpadId) {
                    filter.copy(localX = filter.localX + deltaX, localY = filter.localY + deltaY)
                } else {
                    filter
                }
            } + globalFilters.filter { (x, y) -> ownsGlobal(x = x, y = y) }.map { (x, y) ->
                LaunchpadPadFilter(
                    launchpadId = launchpadId,
                    localX = x - position.x.toInt() + deltaX,
                    localY = y - position.y.toInt() + deltaY,
                )
            },
        ).preserveFlags(original = state)
    }

    fun remap(state: KeyframesChainDeviceState): KeyframesChainDeviceState {
        val rootX = state.rootKeyLocalX ?: state.rootKey?.rem(10)
        val rootY = state.rootKeyLocalY ?: state.rootKey?.div(10)
        val anchoredRoot = state.rootKeyLaunchpadId == launchpadId && rootX != null && rootY != null
        val legacyRoot = state.rootKeyLaunchpadId == null && rootX != null && rootY != null &&
            ownsGlobal(x = rootX, y = rootY)
        val movedRootX = rootX?.minus(if (legacyRoot) position.x.toInt() else 0)?.plus(deltaX)
        val movedRootY = rootY?.minus(if (legacyRoot) position.y.toInt() else 0)?.plus(deltaY)

        return state.copy(
            frames = state.frames.map { frame ->
                frame.copy(entries = frame.entries.map { entry ->
                    if (entry.isAbletonVirtualNote) {
                        entry
                    } else if (entry.launchpadId == launchpadId && entry.isDeviceAnchored) {
                        entry.copy(
                            x = entry.x + deltaX,
                            y = entry.y + deltaY,
                            localX = entry.localX!! + deltaX,
                            localY = entry.localY!! + deltaY,
                        )
                    } else if (!entry.isDeviceAnchored && ownsGlobal(x = entry.x, y = entry.y)) {
                        entry.copy(
                            x = entry.x + deltaX,
                            y = entry.y + deltaY,
                            launchpadId = launchpadId,
                            localX = entry.x - position.x.toInt() + deltaX,
                            localY = entry.y - position.y.toInt() + deltaY,
                        )
                    } else {
                        entry
                    }
                })
            },
            rootKey = if (anchoredRoot || legacyRoot) movedRootX!! + movedRootY!! * 10 else state.rootKey,
            rootKeyLaunchpadId = if (legacyRoot) launchpadId else state.rootKeyLaunchpadId,
            rootKeyLocalX = if (anchoredRoot || legacyRoot) movedRootX else state.rootKeyLocalX,
            rootKeyLocalY = if (anchoredRoot || legacyRoot) movedRootY else state.rootKeyLocalY,
            renderedAnimation = emptyList(),
        ).preserveFlags(original = state)
    }

    fun remap(data: AutoPlayData): AutoPlayData = data.copy(
        actions = data.actions.mapValues { (_, actions) ->
            actions.map { action ->
                if (action.launchpadId == launchpadId ||
                    (action.launchpadId == null && ownsGlobal(x = action.x, y = action.y))) {
                    action.copy(x = action.x + deltaX, y = action.y + deltaY, launchpadId = launchpadId)
                } else {
                    action
                }
            }
        },
    )

    fun remap(device: GenericChainDevice<*>) {
        when (device) {
            is CoordinateFilterChainDevice -> device.state.value = remap(state = device.state.value)
            is KeyframesChainDevice -> {
                device.state.value = remap(state = device.state.value)
                device.onStateRestored()
            }
        }
    }

    fun remap(chain: Chain) {
        chain.devicesDepthFirst().forEach { device -> remap(device = device) }
    }

    fun remap(chain: StateChain): StateChain = chain.copy(devices = chain.devices.map(::remap))

    fun remap(state: DeviceState): DeviceState = when (state) {
        is CoordinateFilterChainDeviceState -> remap(state = state)
        is KeyframesChainDeviceState -> remap(state = state)
        is GroupChainDeviceState -> state.copy(
            groups = state.groups.map { it.copy(stateChain = remap(chain = it.stateChain)) },
        )
        is MultiGroupChainDeviceState -> state.copy(
            groups = state.groups.map { it.copy(stateChain = remap(chain = it.stateChain)) },
            preprocessChain = remap(chain = state.preprocessChain),
        )
        is ChokeChainDeviceState -> state.copy(stateChain = remap(chain = state.stateChain))
        is MaskChainDeviceState -> state.copy(
            colorStateChain = remap(chain = state.colorStateChain),
            shapeStateChain = remap(chain = state.shapeStateChain),
        )
        else -> state
    }.preserveFlags(original = state)
}

private fun <T : DeviceState> T.preserveFlags(original: DeviceState): T = apply {
    isMuted = original.isMuted
    isCollapsed = original.isCollapsed
}

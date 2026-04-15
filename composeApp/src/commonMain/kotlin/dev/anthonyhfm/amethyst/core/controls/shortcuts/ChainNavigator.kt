package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.update

/**
 * Utility for hierarchical chain navigation.
 * Handles compound devices (GroupChainDevice, MultiGroupChainDevice, ChokeChainDevice)
 * so that keyboard navigation follows the visual left-to-right traversal order.
 */
object ChainNavigator {

    data class OuterContext(
        val outerChain: Chain,
        val compoundDevice: GenericChainDevice<*>,
        val compoundIndex: Int
    )

    fun isGroupDevice(device: GenericChainDevice<*>) =
        device is GroupChainDevice || device is MultiGroupChainDevice

    fun isCompound(device: GenericChainDevice<*>) =
        device is GroupChainDevice || device is MultiGroupChainDevice || device is ChokeChainDevice

    /** Returns (openedGroupIndex, groups) for GroupChainDevice/MultiGroupChainDevice. */
    fun getGroupsInfo(device: GenericChainDevice<*>): Pair<Int, List<Group>>? = when (device) {
        is GroupChainDevice -> device.state.value.openedGroupIndex to device.state.value.groups
        is MultiGroupChainDevice -> device.state.value.openedGroupIndex to device.state.value.groups
        else -> null
    }

    /**
     * Returns the currently-visible inner chain of a compound device:
     * - Group/Multi: the opened group's chain
     * - Choke: the single child chain
     */
    fun getInnerChainOf(device: GenericChainDevice<*>): Chain? = when (device) {
        is GroupChainDevice -> {
            val s = device.state.value
            s.groups.getOrNull(s.openedGroupIndex)?.chain
        }
        is MultiGroupChainDevice -> {
            val s = device.state.value
            s.groups.getOrNull(s.openedGroupIndex)?.chain
        }
        is ChokeChainDevice -> device.state.value.chain
        else -> null
    }

    /** Updates the openedGroupIndex in a Group or MultiGroup compound device. */
    fun openGroupInDevice(device: GenericChainDevice<*>, index: Int) {
        when (device) {
            is GroupChainDevice -> device.state.update { it.copy(openedGroupIndex = index) }
            is MultiGroupChainDevice -> device.state.update { it.copy(openedGroupIndex = index) }
            else -> {}
        }
    }

    /**
     * Finds the OuterContext for an inner chain — i.e. which compound device directly owns it,
     * and in which outer chain does that compound device live.
     */
    fun findOuterContextOf(innerChain: Chain): OuterContext? =
        findOuterContextInChain(WorkspaceRepository.lightsChain, innerChain)
            ?: findOuterContextInChain(WorkspaceRepository.samplingChain, innerChain)

    private fun findOuterContextInChain(chain: Chain, target: Chain): OuterContext? {
        chain.devices.value.forEachIndexed { index, device ->
            when (device) {
                is GroupChainDevice -> {
                    for (group in device.state.value.groups) {
                        if (group.chain === target) return OuterContext(chain, device, index)
                        findOuterContextInChain(group.chain, target)?.let { return it }
                    }
                }
                is MultiGroupChainDevice -> {
                    for (group in device.state.value.groups) {
                        if (group.chain === target) return OuterContext(chain, device, index)
                        findOuterContextInChain(group.chain, target)?.let { return it }
                    }
                }
                is ChokeChainDevice -> {
                    val inner = device.state.value.chain
                    if (inner === target) return OuterContext(chain, device, index)
                    findOuterContextInChain(inner, target)?.let { return it }
                }
            }
        }
        return null
    }

    /** Finds which chain directly contains the given device (searching both root chains). */
    fun findOuterChainOf(device: GenericChainDevice<*>): Chain? =
        findChainContaining(WorkspaceRepository.lightsChain, device)
            ?: findChainContaining(WorkspaceRepository.samplingChain, device)

    private fun findChainContaining(chain: Chain, target: GenericChainDevice<*>): Chain? {
        if (chain.devices.value.any { it.selectionUUID == target.selectionUUID }) return chain
        for (device in chain.devices.value) {
            when (device) {
                is GroupChainDevice -> {
                    for (group in device.state.value.groups) {
                        findChainContaining(group.chain, target)?.let { return it }
                    }
                }
                is MultiGroupChainDevice -> {
                    for (group in device.state.value.groups) {
                        findChainContaining(group.chain, target)?.let { return it }
                    }
                }
                is ChokeChainDevice -> {
                    findChainContaining(device.state.value.chain, target)?.let { return it }
                }
            }
        }
        return null
    }
}

@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.workspace.chain.data

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class StateChain(
    @ProtoNumber(1)
    val devices: List<@Polymorphic DeviceState> = emptyList(),
    @ProtoNumber(2)
    val mutedDeviceIndices: List<Int> = emptyList(),
    @ProtoNumber(3)
    val deviceIds: List<String> = emptyList(),
) {
    fun unpack(): Chain = unpackInto(Chain())

    fun unpackAudio(): AudioChain = unpackInto(AudioChain())

    /**
     * Creates a logically new copy of this chain.
     *
     * Unlike [unpack], every device in the complete nested subtree receives a
     * new identity. Copy/paste and duplicate actions must use this path so a
     * parameter or sidechain address can never resolve to two devices.
     */
    fun unpackCopy(): Chain = withoutDeviceIdsRecursively().unpackInto(Chain())

    private fun <T : Chain> unpackInto(chain: T): T {
        val restoredIds = mutableSetOf<String>()
        devices.forEachIndexed { index, deviceState ->
            val device = DeviceRegistry.unpack(deviceState)
            device.selectionUUID = restoredDeviceId(
                savedId = deviceIds.getOrNull(index),
                generatedId = device.selectionUUID,
                reservedIds = restoredIds,
            )
            if (index in mutedDeviceIndices || deviceState.isMuted) {
                device.state.value.isMuted = true
            }
            chain.add(device, fromUser = false)
        }

        chain.reroute()
        ensureUniqueDeviceIds(chain)

        return chain
    }

    companion object {
        fun pack(chain: Chain): StateChain {
            val devList = chain.devices.value
            val mutedIndices = devList.mapIndexedNotNull { index, device ->
                if (device.isMuted) index else null
            }
            return StateChain(
                devices = devList.map { packDevice(it) },
                mutedDeviceIndices = mutedIndices,
                deviceIds = devList.map(GenericChainDevice<*>::selectionUUID),
            )
        }

        fun packDevice(device: GenericChainDevice<*>): DeviceState {
            val state = DeviceRegistry.pack(device)
            state.isMuted = device.isMuted
            return state
        }

        fun unpackDevice(deviceState: DeviceState): GenericChainDevice<*> {
            val device = DeviceRegistry.unpack(deviceState.withoutNestedDeviceIds())
            device.state.value.isMuted = deviceState.isMuted
            return device
        }

        private fun restoredDeviceId(
            savedId: String?,
            generatedId: String,
            reservedIds: MutableSet<String>,
        ): String {
            val candidate = savedId?.takeIf(String::isNotBlank)
            if (candidate != null && reservedIds.add(candidate)) return candidate

            var generated = generatedId
            while (generated.isBlank() || !reservedIds.add(generated)) {
                generated = UUID.randomUUID()
            }
            return generated
        }

        private fun ensureUniqueDeviceIds(root: Chain) {
            val reservedIds = mutableSetOf<String>()

            fun visit(chain: Chain) {
                chain.devices.value.forEach { device ->
                    val currentId = device.selectionUUID
                    val uniqueId = restoredDeviceId(
                        savedId = device.selectionUUID,
                        generatedId = UUID.randomUUID(),
                        reservedIds = reservedIds,
                    )
                    if (uniqueId != currentId) {
                        // A few devices register runtime resources under their
                        // identity in onAddedToChain(). Re-run that lifecycle
                        // when repairing a duplicate from persisted data.
                        device.onRemovedFromChain()
                        device.selectionUUID = uniqueId
                        device.onAddedToChain(chain)
                    }
                    if (device is NestedChainDevice) {
                        device.nestedChains().forEach(::visit)
                    }
                }
            }

            visit(root)
        }
    }
}

private fun StateChain.withoutDeviceIdsRecursively(): StateChain = copy(
    devices = devices.map(DeviceState::withoutNestedDeviceIds),
    deviceIds = emptyList(),
)

private fun DeviceState.withoutNestedDeviceIds(): DeviceState = when (this) {
    is GroupChainDeviceState -> copy(
        groups = groups.map { group ->
            group.copy(stateChain = group.stateChain.withoutDeviceIdsRecursively())
        },
    )

    is MultiGroupChainDeviceState -> copy(
        groups = groups.map { group ->
            group.copy(stateChain = group.stateChain.withoutDeviceIdsRecursively())
        },
        preprocessChain = preprocessChain.withoutDeviceIdsRecursively(),
    )

    is ChokeChainDeviceState -> copy(
        stateChain = stateChain.withoutDeviceIdsRecursively(),
    )

    else -> this
}

fun StateChain.findMaxMacroIndex(): Int {
    var maxIndex = -1
    fun traverse(devices: List<DeviceState>) {
        for (device in devices) {
            when (device) {
                is dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState -> maxIndex = maxOf(maxIndex, device.macro)
                is dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState -> maxIndex = maxOf(maxIndex, device.macro)
                is dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState -> device.groups.forEach { traverse(it.stateChain.devices) }
                is dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState -> device.groups.forEach { traverse(it.stateChain.devices) }
                is dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState -> traverse(device.stateChain.devices)
                is dev.anthonyhfm.amethyst.devices.effects.mask.MaskChainDeviceState -> {
                    traverse(device.colorStateChain.devices)
                    traverse(device.shapeStateChain.devices)
                }
                else -> {}
            }
        }
    }
    traverse(this.devices)
    return maxIndex
}

package dev.anthonyhfm.amethyst.workspace.chain.data

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
data class StateChain(
    val devices: List<@Polymorphic DeviceState> = emptyList(),
    val mutedDeviceIndices: List<Int> = emptyList()
) {
    fun unpack(): Chain = unpackInto(Chain())

    fun unpackAudio(): AudioChain = unpackInto(AudioChain())

    private fun <T : Chain> unpackInto(chain: T): T {

        devices.forEachIndexed { index, deviceState ->
            val device = unpackDevice(deviceState)
            if (index in mutedDeviceIndices || deviceState.isMuted) {
                device.state.value.isMuted = true
            }
            chain.add(device, fromUser = false)
        }

        chain.reroute()

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
                mutedDeviceIndices = mutedIndices
            )
        }

        fun packDevice(device: GenericChainDevice<*>): DeviceState {
            val state = DeviceRegistry.pack(device)
            state.isMuted = device.isMuted
            return state
        }

        fun unpackDevice(deviceState: DeviceState): GenericChainDevice<*> {
            val device = DeviceRegistry.unpack(deviceState)
            device.state.value.isMuted = deviceState.isMuted
            return device
        }
    }
}

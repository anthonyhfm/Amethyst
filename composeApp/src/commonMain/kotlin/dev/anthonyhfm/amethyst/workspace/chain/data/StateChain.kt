package dev.anthonyhfm.amethyst.workspace.chain.data

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
data class StateChain(
    val devices: List<@Polymorphic DeviceState> = emptyList()
) {
    fun unpack(): Chain {
        val chain = Chain()

        devices.forEach { device ->
            chain.add(unpackDevice(device))
        }

        chain.reroute()

        return chain
    }

    companion object {
        fun pack(chain: Chain): StateChain {
            return StateChain(
                devices = chain.devices.value.map { packDevice(it) }
            )
        }

        fun packDevice(device: GenericChainDevice<*>): DeviceState = DeviceRegistry.pack(device)

        fun unpackDevice(device: DeviceState): GenericChainDevice<*> = DeviceRegistry.unpack(device)
    }
}


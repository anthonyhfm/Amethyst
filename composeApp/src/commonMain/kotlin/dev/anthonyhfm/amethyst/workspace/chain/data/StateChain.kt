package dev.anthonyhfm.amethyst.workspace.chain.data

import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDevice
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDevice
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDevice
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDevice
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
data class StateChain(
    val devices: List<DeviceState> = emptyList()
) {
    fun unpack(): Chain {
        val chain = Chain()

        devices.forEach { device ->
            when (device) {
                is ColorChainDeviceState -> {
                    ColorChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is GradientChainDeviceState -> {
                    GradientChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is DelayChainDeviceState -> {
                    DelayChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is OffsetChainDeviceState -> {
                    OffsetChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }
            }
        }

        chain.reroute()

        return chain
    }

    companion object {
        fun pack(chain: Chain): StateChain {
            return StateChain(
                devices = chain.devices.value.map {
                    when (it) {
                        is GroupChainDevice -> {
                            println(it.state.value.groups.map { it.stateChain })

                            it.state.value
                        }

                        else -> it.state.value
                    }
                }
            )
        }
    }
}
package dev.anthonyhfm.amethyst.workspace.chain.data

import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDevice
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDevice
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDevice
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDevice
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDevice
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDevice
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDevice
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.layer_filter.LayerFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.layer_filter.LayerFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDevice
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDevice
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDevice
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDevice
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlin.collections.forEach

@Serializable
data class StateChain(
    val devices: List<@Polymorphic DeviceState> = emptyList()
) {
    fun unpack(): Chain {
        val chain = Chain()

        devices.forEach { device ->
            when (device) {
                is CoordinateFilterChainDeviceState -> {
                    CoordinateFilterChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is LayerFilterChainDeviceState -> {
                    LayerFilterChainDevice().let {
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

                is HoldChainDeviceState -> {
                    HoldChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is LoopChainDeviceState -> {
                    LoopChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

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

                is GroupChainDeviceState -> {
                    GroupChainDevice().let {
                        it.state.update { device }
                        it.loadFromState(device)

                        chain.add(it)
                    }
                }

                is KeyframesChainDeviceContract.KeyframesChainDeviceState -> {
                    KeyframesChainDevice().let {
                        it.state.update { device }

                        chain.add(it)
                    }
                }

                is LayerChainDeviceState -> {
                    LayerChainDevice().let {
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

                is FlipChainDeviceState -> {
                    FlipChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is RotateChainDeviceState -> {
                    RotateChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is ClipChainDeviceState -> {
                    ClipChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is MacroFilterChainDeviceState -> {
                    MacroFilterChainDevice().let {
                        it.state.update { device }
                        chain.add(it)
                    }
                }

                is SwitchChainDeviceState -> {
                    SwitchChainDevice().let {
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
            val stateChain = StateChain(
                devices = chain.devices.value.map {
                    if (it is GroupChainDevice) {
                        it.state.value.copy(
                            groups = it.state.value.groups.map { group ->
                                group.copy(
                                    stateChain = pack(group.chain)
                                )
                            }
                        )
                    } else {
                        it.state.value
                    }
                }
            )

            return stateChain
        }
    }
}
package dev.anthonyhfm.amethyst.workspace.chain.data

import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.devices.ChainDevice
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
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
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
            chain.add(unpackDevice(device))
        }

        chain.reroute()

        return chain
    }

    companion object {
        fun pack(chain: Chain): StateChain {
            val stateChain = StateChain(
                devices = chain.devices.value.map {
                    if (it is GroupChainDevice) {
                        it.packState()
                    } else if (it is MultiGroupChainDevice) {
                        it.packState()
                    } else {
                        it.state.value
                    }
                }
            )

            return stateChain
        }

        fun unpackDevice(device: DeviceState): ChainDevice<*> {
            return when (device) {
                is CoordinateFilterChainDeviceState -> {
                    CoordinateFilterChainDevice().apply {
                        state.update { device }
                    }
                }

                is LayerFilterChainDeviceState -> {
                    LayerFilterChainDevice().apply {
                        state.update { device }
                    }
                }

                is DelayChainDeviceState -> {
                    DelayChainDevice().apply {
                        state.update { device }
                    }
                }

                is HoldChainDeviceState -> {
                    HoldChainDevice().apply {
                        state.update { device }
                    }
                }

                is LoopChainDeviceState -> {
                    LoopChainDevice().apply {
                        state.update { device }
                    }
                }

                is ColorChainDeviceState -> {
                    ColorChainDevice().apply {
                        state.update { device }
                    }
                }

                is GradientChainDeviceState -> {
                    GradientChainDevice().apply {
                        state.update { device }
                    }
                }

                is GroupChainDeviceState -> {
                    GroupChainDevice().apply {
                        loadFromState(device)
                    }
                }

                is MultiGroupChainDeviceState -> {
                    MultiGroupChainDevice().apply {
                        loadFromState(device)
                    }
                }

                is KeyframesChainDeviceContract.KeyframesChainDeviceState -> {
                    KeyframesChainDevice().apply {
                        state.update { device }
                    }
                }

                is LayerChainDeviceState -> {
                    LayerChainDevice().apply {
                        state.update { device }
                    }
                }

                is OffsetChainDeviceState -> {
                    OffsetChainDevice().apply {
                        state.update { device }
                    }
                }

                is FlipChainDeviceState -> {
                    FlipChainDevice().apply {
                        state.update { device }
                    }
                }

                is RotateChainDeviceState -> {
                    RotateChainDevice().apply {
                        state.update { device }
                    }
                }

                is ClipChainDeviceState -> {
                    ClipChainDevice().apply {
                        state.update { device }
                    }
                }

                is MacroFilterChainDeviceState -> {
                    MacroFilterChainDevice().apply {
                        state.update { device }
                    }
                }

                is SwitchChainDeviceState -> {
                    SwitchChainDevice().apply {
                        state.update { device }
                    }
                }

                else -> { throw IllegalArgumentException("Unknown device state: $device") }
            }
        }
    }
}
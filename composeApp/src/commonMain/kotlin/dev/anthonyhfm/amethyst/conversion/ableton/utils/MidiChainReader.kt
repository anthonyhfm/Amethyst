package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlin.reflect.KClass
import kotlin.reflect.cast

class MidiChainReader(
    private val offset: IntOffset = IntOffset.Zero,
    private val outputOffset: IntOffset = IntOffset.Zero,
) {
    fun readMidiChain(midiTrack: MidiTrack): StateChain {
        val chainDevices = midiTrack.deviceChain.devices

        val adapters = chainDevices.map {
            AbletonAdapter.resolveAdapter(
                device = it,
                offset = offset,
                outputOffset = outputOffset
            )
        }

        return StateChain(
            devices = adapters.filter { it != null }.map {
                it!!.toDeviceStates()
            }.flatten().toMutableList().apply {
                if (outputOffset != IntOffset.Zero) {
                    add(
                        OffsetChainDeviceState(offsetX = outputOffset.x, offsetY = outputOffset.y)
                    )
                }
            }
        )
    }

    companion object {
        fun getChainWeight(track: MidiTrack): Int {
            var chainDevices = 0

            track.deviceChain.devices.forEach {
                chainDevices++

                if (it is MidiEffectGroupDevice) {
                    chainDevices += getMidiGroupWeight(it)
                } else if (it is InstrumentGroupDevice) {
                    chainDevices += getInstrumentGroupWeight(it)
                }
            }

            return chainDevices
        }

        fun getMidiGroupWeight(group: MidiEffectGroupDevice): Int {
            var chainDevices = 0

            group.branches.branches.forEach { branch ->
                chainDevices++

                branch.deviceChain.deviceChain.devices.devices.forEach {
                    chainDevices++

                    if (it is MidiEffectGroupDevice) {
                        chainDevices += getMidiGroupWeight(it)
                    } else if (it is InstrumentGroupDevice) {
                        chainDevices += getInstrumentGroupWeight(it)
                    } else if (it is DrumGroupDevice) {
                        chainDevices += getDrumGroupWeight(it)
                    }
                }
            }

            return chainDevices
        }

        fun getInstrumentGroupWeight(group: InstrumentGroupDevice): Int {
            var chainDevices = 0

            group.branches.branches.forEach { branch ->
                branch.deviceChain.deviceChain.devices.devices.forEach {
                    chainDevices++

                    if (it is MidiEffectGroupDevice) {
                        chainDevices += getMidiGroupWeight(it)
                    } else if (it is InstrumentGroupDevice) {
                        chainDevices += getInstrumentGroupWeight(it)
                    } else if (it is DrumGroupDevice) {
                        chainDevices += getDrumGroupWeight(it)
                    }
                }
            }

            return chainDevices
        }

        fun getDrumGroupWeight(group: DrumGroupDevice): Int {
            var chainDevices = 0

            group.branches.branches.forEach { branch ->
                branch.deviceChain.deviceChain.devices.devices.forEach {
                    chainDevices++

                    if (it is MidiEffectGroupDevice) {
                        chainDevices += getMidiGroupWeight(it)
                    } else if (it is InstrumentGroupDevice) {
                        chainDevices += getInstrumentGroupWeight(it)
                    } else if (it is DrumGroupDevice) {
                        chainDevices += getDrumGroupWeight(it)
                    }
                }
            }

            return chainDevices
        }

        inline fun <reified T : AbletonDevice> getAllDevicesOfType(track: MidiTrack): List<T> {
            return getAllDevicesOfType(track, T::class)
        }

        fun <T : AbletonDevice> getAllDevicesOfType(track: MidiTrack, clazz: KClass<T>): List<T> {
            val result = LinkedHashSet<T>()
            val stack = ArrayDeque<AbletonDevice>()

            track.deviceChain.devices.forEach { stack.add(it) }

            while (stack.isNotEmpty()) {
                val device = stack.removeLast()

                if (clazz.isInstance(device)) {
                    result.add(clazz.cast(device))
                }

                getChildren(device).forEach { child ->
                    stack.add(child)
                }
            }

            return result.toList()
        }

        private fun getChildren(device: AbletonDevice): List<AbletonDevice> {
            return when (device) {
                is MidiEffectGroupDevice ->
                    device.branches.branches.flatMap { branch ->
                        branch.deviceChain.deviceChain.devices.devices
                    }
                is InstrumentGroupDevice ->
                    device.branches.branches.flatMap { branch ->
                        branch.deviceChain.deviceChain.devices.devices
                    }
                is DrumGroupDevice ->
                    device.branches.branches.flatMap { branch ->
                        branch.deviceChain.deviceChain.devices.devices
                    }
                else -> emptyList()
            }
        }
    }
}
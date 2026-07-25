package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiArpeggiator
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiChord
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.ableton.AbletonPitcherChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class MidiChordAdapter(
    private val device: MidiChord
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        return listOf(
            GroupChainDeviceState(
                groups = listOf(
                    device.shift1,
                    device.shift2,
                    device.shift3,
                    device.shift4,
                    device.shift5,
                    device.shift6
                ).mapIndexed { index, shift ->
                    Group(
                        name = "Shift ${index + 1}",
                        stateChain = StateChain(
                            devices = listOf(
                                AbletonPitcherChainDeviceState(
                                    pitch = shift.manual.value
                                )
                            )
                        )
                    )
                }
            )
        ).withMuteState(device.on.manual.value)
    }
}
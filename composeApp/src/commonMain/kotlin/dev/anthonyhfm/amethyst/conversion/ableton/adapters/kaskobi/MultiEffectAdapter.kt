package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState.TYPE
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class MultiEffectAdapter (
    private val device: MxDeviceMidiEffect,
    private val midiContainer: MidiEffectGroupDevice?,
    private val instrumentContainer: InstrumentGroupDevice?
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val parameter: MxParameter.MxDIntParameter? = device.parameterList.parameterList.parameters.find {
            it is MxParameter.MxDIntParameter
        } as? MxParameter.MxDIntParameter

        val steps = parameter?.timeable?.manual?.value ?: 1

        if (midiContainer?.branches?.branches?.isEmpty() ?: false || instrumentContainer?.branches?.branches?.isEmpty() ?: false) {
            println("No branches found in Multi container!")
            return listOf()
        }

        var instrumentBranches: List<InstrumentGroupDevice.Branches.InstrumentBranch> = emptyList()
        var midiBranches: List<MidiEffectGroupDevice.Branches.MidiEffectBranch> = emptyList()

        if (midiContainer != null) {
            midiBranches = midiContainer.branches.branches.map {
                val min = it.zoneSettings.keyRange.min.value
                val max = it.zoneSettings.keyRange.max.value

                List(max - min + 1) { note ->
                    it.copy()
                }
            }.flatten()
        } else if (instrumentContainer != null) {
            instrumentBranches = instrumentContainer.branches.branches.map {
                val min = it.zoneSettings.keyRange.min.value
                val max = it.zoneSettings.keyRange.max.value

                List(max - min + 1) { note ->
                    it.copy()
                }
            }.flatten()
        }

        return listOf(
            MultiGroupChainDeviceState(
                type = TYPE.FORWARD,
                groups = List(steps) { step ->
                    if (instrumentContainer != null) {
                        Group(
                            name = instrumentBranches[step].name.effectiveName?.value ?: "Chain #",
                            stateChain = StateChain(
                                devices = mutableListOf<DeviceState>().apply {
                                    instrumentBranches[step].let {
                                        addAll(
                                            elements = it.deviceChain.deviceChain.devices.devices.mapNotNull { child ->
                                                resolveAdapter(child)
                                                    ?.toDeviceStates()
                                                    ?.firstOrNull()
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    } else if (midiContainer != null) {
                        Group(
                            name = midiBranches[step].name.effectiveName?.value ?: "Chain #",
                            stateChain = StateChain(
                                devices = mutableListOf<DeviceState>().apply {
                                    midiBranches[step].let {
                                        addAll(
                                            elements = it.deviceChain.deviceChain.devices.devices.mapNotNull { child ->
                                                resolveAdapter(child)
                                                    ?.toDeviceStates()
                                                    ?.firstOrNull()
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    } else Group("Empty")
                }
            )
        )
    }
}
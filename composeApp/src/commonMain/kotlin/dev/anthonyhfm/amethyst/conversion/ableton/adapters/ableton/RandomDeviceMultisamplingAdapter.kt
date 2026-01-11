package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiRandom
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState.TYPE
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class RandomDeviceMultisamplingAdapter (
    private val random: MidiRandom,
    private val midiContainer: MidiEffectGroupDevice?,
    private val instrumentContainer: InstrumentGroupDevice?
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val randomChance = random.chance.manual.value
        val multiSteps = random.choices.manual.value
        val altModeEnabled = random.alternate.manual.value

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

        if (randomChance == 1.0 && altModeEnabled) {
            return listOf(
                MultiGroupChainDeviceState(
                    type = TYPE.FORWARD,
                    groups = List(multiSteps.toInt()) { step ->
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

        println("Random device does not have Chance 100% and Alt mode, skipping...; Values: chance=$randomChance, steps=$multiSteps, alt=$altModeEnabled")
        return listOf()
    }
}
package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
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
    private val instrumentContainer: InstrumentGroupDevice?,
    private val drumContainer: DrumGroupDevice?
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val randomChance = random.chance.manual.value
        val multiSteps = random.choices.manual.value
        val altModeEnabled = random.alternate.manual.value

        var instrumentBranches: List<InstrumentGroupDevice.Branches.InstrumentBranch> = emptyList()
        var midiBranches: List<MidiEffectGroupDevice.Branches.MidiEffectBranch> = emptyList()
        var drumBranches: List<DrumGroupDevice.Branches.DrumBranch> = emptyList()

        if (midiContainer != null) {
            midiBranches = midiContainer.branches.branches.map {
                val min = it.zoneSettings.keyRange.min.value
                val max = it.zoneSettings.keyRange.max.value

                List(max - min + 1) { _ ->
                    it.copy()
                }
            }.flatten()
        } else if (instrumentContainer != null) {
            instrumentBranches = instrumentContainer.branches.branches.map {
                val min = it.zoneSettings.keyRange.min.value
                val max = it.zoneSettings.keyRange.max.value

                List(max - min + 1) { _ ->
                    it.copy()
                }
            }.flatten()
        } else if (drumContainer != null) {
            drumBranches = drumContainer.branches.branches.map {
                listOf(it.copy())
            }.flatten()
        }

        if (randomChance == 1.0 && altModeEnabled) {
            return listOf(
                MultiGroupChainDeviceState(
                    type = TYPE.FORWARD,
                    groups = List(multiSteps.toInt()) { step ->
                        when {
                            instrumentContainer != null -> {
                                Group(
                                    name = instrumentBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Chain #",
                                    stateChain = StateChain(
                                        devices = mutableListOf<DeviceState>().apply {
                                            instrumentBranches.getOrNull(step)?.let { br ->
                                                addAll(
                                                    elements = br.deviceChain.deviceChain.devices.devices.mapNotNull { child ->
                                                        resolveAdapter(child)
                                                            ?.toDeviceStates()
                                                            ?.firstOrNull()
                                                    }
                                                )
                                            }
                                        }
                                    )
                                )
                            }
                            midiContainer != null -> {
                                Group(
                                    name = midiBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Chain #",
                                    stateChain = StateChain(
                                        devices = mutableListOf<DeviceState>().apply {
                                            midiBranches.getOrNull(step)?.let { br ->
                                                addAll(
                                                    elements = br.deviceChain.deviceChain.devices.devices.mapNotNull { child ->
                                                        resolveAdapter(child)
                                                            ?.toDeviceStates()
                                                            ?.firstOrNull()
                                                    }
                                                )
                                            }
                                        }
                                    )
                                )
                            }
                            drumContainer != null -> {
                                Group(
                                    name = drumBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Chain #",
                                    stateChain = StateChain(
                                        devices = mutableListOf<DeviceState>().apply {
                                            drumBranches.getOrNull(step)?.let { br ->
                                                addAll(
                                                    elements = br.deviceChain.deviceChain.devices.devices.mapNotNull { child ->
                                                        resolveAdapter(child)
                                                            ?.toDeviceStates()
                                                            ?.firstOrNull()
                                                    }
                                                )
                                            }
                                        }
                                    )
                                )
                            }
                            else -> Group("Empty")
                        }
                    }
                )
            ).withMuteState(random.on.manual.value)
        }

        println("Random device does not have Chance 100% and Alt mode, skipping...; Values: chance=$randomChance, steps=$multiSteps, alt=$altModeEnabled")
        return listOf()
    }
}
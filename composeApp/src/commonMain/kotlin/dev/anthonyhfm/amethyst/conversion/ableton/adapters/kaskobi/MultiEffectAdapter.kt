package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState.TYPE
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class MultiEffectAdapter(
    private val device: MxDevice,
    private val midiContainer: MidiEffectGroupDevice?,
    private val instrumentContainer: InstrumentGroupDevice?,
    private val drumContainer: DrumGroupDevice?,
    private val offset: IntOffset,
    private val outputOffset: IntOffset,
    private val chainDepth: Int,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val parameter: MxParameter.MxDIntParameter? = device.parameterList.parameterList.parameters.find {
            it is MxParameter.MxDIntParameter
        } as? MxParameter.MxDIntParameter

        val steps = parameter?.timeable?.manual?.value ?: 1
        val resetGroupId = Regex("\\\"MIDI Extension Choke\\\"\\s*:\\s*\\[\\s*(\\d+)")
            .find(device.decodeBlob())
            ?.groupValues?.get(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

        println("Multi Effect with $steps steps found.")

        if (midiContainer?.branches?.branches?.isEmpty() ?: false || instrumentContainer?.branches?.branches?.isEmpty() ?: false || drumContainer?.branches?.branches?.isEmpty() ?: false) {
            println("No branches found in Multi container!")
            return listOf()
        }

        var instrumentBranches: List<InstrumentGroupDevice.Branches.InstrumentBranch> = emptyList()
        var midiBranches: List<MidiEffectGroupDevice.Branches.MidiEffectBranch> = emptyList()
        var drumBranches: List<DrumGroupDevice.Branches.DrumBranch> = emptyList()

        if (midiContainer != null) {
            midiBranches = midiContainer.branches.branches.map {
                var min = it.zoneSettings.keyRange.min.value
                var max = it.zoneSettings.keyRange.max.value

                if (min == 0 && max == 127) {
                    min = it.branchSelectorRange.min.value
                    max = it.branchSelectorRange.max.value
                }

                List(max - min + 1) { _ ->
                    it.copy()
                }
            }.flatten()
        } else if (instrumentContainer != null) {
            instrumentBranches = instrumentContainer.branches.branches.map {
                var min = it.zoneSettings.keyRange.min.value
                var max = it.zoneSettings.keyRange.max.value

                if (min == 0 && max == 127) {
                    min = it.branchSelectorRange.min.value
                    max = it.branchSelectorRange.max.value
                }

                List(max - min + 1) { _ ->
                    it.copy()
                }
            }.flatten()
        } else if (drumContainer != null) {
            drumBranches = drumContainer.branches.branches.map {
                listOf(it.copy())
            }.flatten()
        }

        val isKeyRangeOrDrum = drumContainer != null || (instrumentContainer?.branches?.branches?.any {
            it.zoneSettings.keyRange.min.value != 0 || it.zoneSettings.keyRange.max.value != 127
        } == true)

        val containerOnState = instrumentContainer?.on?.manual?.value
            ?: midiContainer?.on?.manual?.value
            ?: drumContainer?.on?.manual?.value
            ?: true

        return listOf(
            MultiGroupChainDeviceState(
                type = TYPE.FORWARD,
                resetGroupId = resetGroupId,
                groups = List(steps) { step ->
                    val pitchCompensation = if (isKeyRangeOrDrum) step.toFloat() else 0f
                    when {
                        instrumentContainer != null -> {
                            Group(
                                name = instrumentBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Multi Item #",
                                stateChain = StateChain(
                                    devices = mutableListOf<DeviceState>().apply {
                                        instrumentBranches.getOrNull(step)?.let { br ->
                                            addAll(
                                                resolveChildren(br.deviceChain.deviceChain.devices.devices)
                                                    .withPitchCompensation(pitchCompensation)
                                            )
                                        }
                                    }
                                )
                            )
                        }
                        midiContainer != null -> {
                            Group(
                                name = midiBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Multi Item #",
                                stateChain = StateChain(
                                    devices = mutableListOf<DeviceState>().apply {
                                        midiBranches.getOrNull(step)?.let { br ->
                                            addAll(resolveChildren(br.deviceChain.deviceChain.devices.devices))
                                        }
                                    }
                                )
                            )
                        }
                        drumContainer != null -> {
                            Group(
                                name = drumBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Multi Item #",
                                stateChain = StateChain(
                                    devices = mutableListOf<DeviceState>().apply {
                                        drumBranches.getOrNull(step)?.let { br ->
                                            addAll(
                                                resolveChildren(br.deviceChain.deviceChain.devices.devices)
                                                    .withPitchCompensation(pitchCompensation)
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
        ).withMuteState(containerOnState)
    }

    private fun resolveChildren(devices: List<AbletonDevice>) =
        devices.flatMap { child ->
            resolveAdapter(child, offset, outputOffset, chainDepth + 1)
                ?.toDeviceStates()
                .orEmpty()
        }
}

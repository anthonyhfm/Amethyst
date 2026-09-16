package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState.TYPE
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MultiAdapter(
    private val device: MxDeviceMidiEffect,
    private val midiContainer: MidiEffectGroupDevice?,
    private val instrumentContainer: InstrumentGroupDevice?,
    private val drumContainer: DrumGroupDevice?,
    private val offset: IntOffset,
    private val outputOffset: IntOffset,
    private val chainDepth: Int,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val dataObj: MultiData = jsonDecoder.decodeFromString(device.decodeBlob())

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

        val steps = dataObj.steps.first().toInt()

        val isKeyRangeOrDrum = drumContainer != null || (instrumentContainer?.branches?.branches?.any {
            it.zoneSettings.keyRange.min.value != 0 || it.zoneSettings.keyRange.max.value != 127
        } == true)
        val isNoteMode = dataObj.mode.firstOrNull() != 1.0 && isKeyRangeOrDrum

        val containerOnState = instrumentContainer?.on?.manual?.value
            ?: midiContainer?.on?.manual?.value
            ?: drumContainer?.on?.manual?.value
            ?: true

        return listOf(
            MultiGroupChainDeviceState(
                type = TYPE.FORWARD,
                groups = List(steps) { step ->
                    val pitchCompensation = if (isNoteMode) step.toFloat() else 0f
                    when {
                        instrumentContainer != null -> {
                            Group(
                                name = instrumentBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Chain #",
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
                                name = midiBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Chain #",
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
                                name = drumBranches.getOrNull(step)?.name?.effectiveName?.value ?: "Chain #",
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

    @Serializable
    data class MultiData(
        @SerialName("live.numbox")
        val steps: List<Double>,
        @SerialName("live.text")
        val mode: List<Double> = emptyList(),
    )
}

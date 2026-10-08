package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.AbletonRackVelocityRange
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.ableton.AbletonPitcherChainDeviceState
import dev.anthonyhfm.amethyst.devices.ableton.AbletonPitchRangeChainDeviceState
import dev.anthonyhfm.amethyst.devices.ableton.AbletonVelocityChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

internal class AbletonMultisamplingAdapter(
    private val midiContainer: MidiEffectGroupDevice?,
    private val instrumentContainer: InstrumentGroupDevice?,
    private val drumContainer: DrumGroupDevice?,
    private val steps: Int,
    private val noteMode: Boolean,
    private val inputNote: Int? = null,
    private val stepPitch: Int = 1,
    private val offset: IntOffset = IntOffset.Zero,
    private val outputOffset: IntOffset = IntOffset.Zero,
    private val chainDepth: Int = 0,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val branches = containerBranches()
        if (branches.isEmpty() || steps !in 1..128) {
            return emptyList()
        }

        val baseNote = inputNote ?: 0
        val selector = instrumentContainer?.chainSelector?.manual?.value
            ?: midiContainer?.chainSelector?.manual?.value
            ?: drumContainer?.chainSelector?.manual?.value
            ?: 0
        val groups = List(size = steps) { step ->
            val pitch = baseNote + step * stepPitch
            val selectorValue = if (noteMode) {
                selector
            } else {
                step
            }
            val matching = branches.filter { branch ->
                selectorValue in branch.selectorMinimum..branch.selectorMaximum &&
                    (!noteMode || inputNote == null || pitch in branch.keyMinimum..branch.keyMaximum)
            }
            val branchGroups = matching.map { branch ->
                val devices = mutableListOf<DeviceState>()
                if (!branch.enabled) {
                    devices.add(
                        element = AbletonPitchRangeChainDeviceState(minimum = 1, maximum = 0),
                    )
                }
                if (noteMode && inputNote == null) {
                    devices.add(
                        element = AbletonPitcherChainDeviceState(pitch = step * stepPitch),
                    )
                    devices.add(
                        element = AbletonPitchRangeChainDeviceState(
                            minimum = branch.keyMinimum,
                            maximum = branch.keyMaximum,
                        ),
                    )
                } else if (noteMode) {
                    devices.add(
                        element = AbletonPitcherChainDeviceState(absolutePitch = pitch),
                    )
                } else if (!noteMode && (branch.keyMinimum != 0 || branch.keyMaximum != 127)) {
                    devices.add(
                        element = AbletonPitchRangeChainDeviceState(
                            minimum = branch.keyMinimum,
                            maximum = branch.keyMaximum,
                        ),
                    )
                }
                branch.outputNote?.let { outputNote ->
                    devices.add(
                        element = AbletonPitcherChainDeviceState(absolutePitch = outputNote),
                    )
                }
                devices.appendRackVelocityRange(range = branch.velocityRange)
                devices.addAll(
                    elements = branch.devices.flatMap { child ->
                        resolveAdapter(
                            device = child,
                            offset = offset,
                            outputOffset = outputOffset,
                            chainDepth = chainDepth + 1,
                        )?.toDeviceStates().orEmpty()
                    },
                )
                devices.appendMixerVolume(
                    linearVolume = branch.volume,
                    isOn = branch.enabled,
                )
                Group(
                    name = branch.name,
                    stateChain = StateChain(devices = devices),
                )
            }
            Group(
                name = if (noteMode && inputNote == null) {
                    "Step ${step + 1}"
                } else {
                    matching.joinToString(separator = " + ") { branch -> branch.name }
                        .ifBlank { "Step ${step + 1}" }
                },
                stateChain = when (branchGroups.size) {
                    0 -> StateChain()
                    1 -> branchGroups.single().stateChain
                    else -> StateChain(
                        devices = listOf(
                            GroupChainDeviceState(groups = branchGroups),
                        ),
                    )
                },
            )
        }.withMultiPitchCompensation(enabled = noteMode && inputNote != null)

        val containerOn = instrumentContainer?.on?.manual?.value
            ?: midiContainer?.on?.manual?.value
            ?: drumContainer?.on?.manual?.value
            ?: true
        return listOf(
            MultiGroupChainDeviceState(
                type = MultiGroupChainDeviceState.TYPE.FORWARD,
                groups = groups,
            ),
        ).withMuteState(isOn = containerOn)
    }

    private fun containerBranches(): List<Branch> {
        return when {
            instrumentContainer != null -> instrumentContainer.branches.branches.map { branch ->
                Branch(
                    name = branch.name.effectiveName?.value ?: "Chain #",
                    keyMinimum = branch.zoneSettings.keyRange.min.value,
                    keyMaximum = branch.zoneSettings.keyRange.max.value,
                    selectorMinimum = branch.branchSelectorRange.min.value,
                    selectorMaximum = branch.branchSelectorRange.max.value,
                    velocityRange = branch.zoneSettings.velocityRange,
                    enabled = branch.masterDevice.speaker.manual.value,
                    volume = branch.masterDevice.volume.manual.value,
                    devices = branch.deviceChain.deviceChain.devices.devices,
                )
            }
            midiContainer != null -> midiContainer.branches.branches.map { branch ->
                Branch(
                    name = branch.name.effectiveName?.value ?: "Chain #",
                    keyMinimum = branch.zoneSettings.keyRange.min.value,
                    keyMaximum = branch.zoneSettings.keyRange.max.value,
                    selectorMinimum = branch.branchSelectorRange.min.value,
                    selectorMaximum = branch.branchSelectorRange.max.value,
                    velocityRange = branch.zoneSettings.velocityRange,
                    enabled = branch.masterDevice.speaker.manual.value,
                    devices = branch.deviceChain.deviceChain.devices.devices,
                )
            }
            drumContainer != null -> drumContainer.branches.branches.map { branch ->
                Branch(
                    name = branch.name.effectiveName?.value ?: "Chain #",
                    keyMinimum = branch.branchInfo.receivingNote.value,
                    keyMaximum = branch.branchInfo.receivingNote.value,
                    outputNote = branch.branchInfo.sendingNote.value,
                    selectorMinimum = branch.branchSelectorRange.min.value,
                    selectorMaximum = branch.branchSelectorRange.max.value,
                    enabled = branch.masterDevice.speaker.manual.value,
                    volume = branch.masterDevice.volume.manual.value,
                    devices = branch.deviceChain.deviceChain.devices.devices,
                )
            }
            else -> emptyList()
        }
    }

    private data class Branch(
        val name: String,
        val keyMinimum: Int,
        val keyMaximum: Int,
        val selectorMinimum: Int,
        val selectorMaximum: Int,
        val velocityRange: AbletonRackVelocityRange = AbletonRackVelocityRange(),
        val enabled: Boolean,
        val volume: Float = 1f,
        val devices: List<AbletonDevice>,
        val outputNote: Int? = null,
    )
}

internal fun MutableList<DeviceState>.appendRackVelocityRange(range: AbletonRackVelocityRange) {
    val minimum = range.min.value.coerceIn(minimumValue = 1, maximumValue = 127)
    val maximum = range.max.value.coerceIn(minimumValue = minimum, maximumValue = 127)
    if (minimum == 1 && maximum == 127) {
        return
    }
    add(
        element = AbletonVelocityChainDeviceState(
            outLow = minimum,
            outHigh = maximum,
            lowest = minimum,
            range = maximum - minimum + 1,
            mode = 1,
        ),
    )
}

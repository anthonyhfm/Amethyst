package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.PlaybackMode
import dev.anthonyhfm.amethyst.devices.effects.mask.MaskChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

class GenericMidiExtAdapter(
    private val device: MxDevice,
    private val offset: IntOffset,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val keyframes = importKeyframes() ?: return emptyList()
        val settings = readSettings(device)

        if (settings.mode == MidiExtensionMode.Mask) {
            val color = keyframes.copy(
                playbackMode = if (settings.loop) PlaybackMode.Continuous else PlaybackMode.Mono,
            )
            return listOf(
                MaskChainDeviceState(
                    colorStateChain = StateChain(devices = listOf(color)),
                    shapeStateChain = StateChain(
                        devices = listOf(ColorChainDeviceState(r = 1f, g = 1f, b = 1f)),
                    ),
                )
            )
        }

        return listOf(keyframes)
    }

    internal fun importKeyframes(): KeyframesChainDeviceState? {
        val fileRef = device.fileDropList.fileDropList.items.firstOrNull()?.ref?.fileRef ?: return null

        val palette = AbletonConverter.palette
        val filePath: String = fileRef.resolvePath()

        val data = if (AbletonConverter.isZip) {
            AbletonConverter.zipEntries[filePath]?.data ?: return null
        } else {
            try {
                runBlocking { PlatformFile(filePath).readBytes() }
            } catch (e: Exception) {
                return null
            }
        }

        return MidiFileImporter.loadData(
            data = data,
            palette = palette,
            bpm = AbletonConverter.bpm,
            launchpad = AbletonConverter.launchpadTarget(offset).midiImportTarget(),
        )
    }

    internal enum class MidiExtensionMode { Launch, Mask }

    internal data class MidiExtensionSettings(
        val mode: MidiExtensionMode = MidiExtensionMode.Launch,
        val loop: Boolean = false,
    )

    companion object {
        internal fun readSettings(device: MxDevice): MidiExtensionSettings {
            val parameters = device.parameterList.parameterList.parameters
            fun parameterValue(index: Int): Double? = when (val parameter = parameters.firstOrNull { it.index == index }) {
                is MxParameter.MxDFloatParameter -> parameter.timeable.manual.value.toDouble()
                is MxParameter.MxDIntParameter -> parameter.timeable.manual.value.toDouble()
                is MxParameter.MxDEnumParameter -> parameter.timeable.manual.value.toDouble()
                else -> null
            }

            return MidiExtensionSettings(
                mode = if (parameterValue(0)?.toInt() == 1) {
                    MidiExtensionMode.Mask
                } else {
                    MidiExtensionMode.Launch
                },
                loop = (parameterValue(1) ?: 0.0) >= 0.5,
            )
        }
    }
}

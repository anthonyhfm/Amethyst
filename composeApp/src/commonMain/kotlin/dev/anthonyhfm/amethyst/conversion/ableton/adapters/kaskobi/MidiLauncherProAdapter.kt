package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.devices.DeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

/**
 * Midi Launcher Pro support
 *
 * Found features and their implementation state:
 *
 * - ✅ Skip Silence
 * - ❌ Delay
 * - ❌ Loop
 * - ❌ Mirroring
 * - ❌ Key Tracking
 * - ❌ Input Response
 * - ❌ Choke Mode
 * - ❌ Shift Mode
 * - ❌ Side Lights
 * - ❌ ... more stuff that i dont want to scroll through rn
 */
class MidiLauncherProAdapter(
    private val device: MxDevice,
    private val offset: IntOffset
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        println("Midi Launcher Pro found. Not all features work yet. ")

        val fileRef = device.fileDropList.fileDropList.items.firstOrNull()?.ref?.fileRef ?: return emptyList()

        val palette = AbletonConverter.palette
        val filePath: String = fileRef.resolvePath()

        val skipSilence: MxParameter.MxDIntParameter = try {
            device.parameterList.parameterList.parameters[16] as MxParameter.MxDIntParameter
        } catch (e: Exception) {
            device.parameterList.parameterList.parameters[15] as MxParameter.MxDIntParameter
        }

        val data = if (AbletonConverter.isZip) {
            AbletonConverter.zipEntries[filePath]?.data ?: return emptyList()
        } else {
            try {
                runBlocking { PlatformFile(filePath).readBytes() }
            } catch (e: Exception) {
                return emptyList()
            }
        }

        var keyframes = MidiFileImporter.loadData(
            data = data,
            palette = palette,
            bpm = AbletonConverter.bpm,
            xyOffset = offset
        )

        keyframes = keyframes.copy(
            frames = keyframes.frames.toMutableList().apply {
                if (skipSilence.timeable.manual.value == 1) {
                    while (isNotEmpty() && this[0].entries.isEmpty()) {
                        removeAt(0)
                    }
                }
            }
        )

        return listOf(keyframes)
    }
}
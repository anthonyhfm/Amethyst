package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.devices.DeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

class MidiLauncherAdapter(
    private val device: MxDevice,
    private val hash: String,
    private val offset: IntOffset
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val fileRef = device.fileDropList.fileDropList.items.firstOrNull()?.ref?.fileRef ?: return emptyList()

        val palette = AbletonConverter.palette
        val filePath: String = fileRef.resolvePath()

        val parameters = MidiLauncherParameters(
            device = device,
            hash = hash
        )
        val playbackBpm = parameters.value(
            name = "MIDIext1Tempo",
            indicesByHash = mapOf("f135067227057b08f8d2d2ae66a22f8d" to 0)
        )?.takeIf { it.isFinite() && it > 0.0 } ?: AbletonConverter.bpm

        val skipSilence = parameters.value(
            name = "Skip Silence",
            indicesByHash = mapOf(
                "2ef098a53fe4e9a4b035588561080343" to 0,
                "f135067227057b08f8d2d2ae66a22f8d" to 1
            )
        ) == 1.0

        val data = if (AbletonConverter.isZip) {
            AbletonConverter.readZipEntry(path = filePath) ?: return emptyList()
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
            bpm = playbackBpm,
            launchpad = AbletonConverter.launchpadTarget(offset = offset).midiImportTarget(),
            preserveEndOfTrackTiming = true,
        )

        if (skipSilence) {
            val frames = keyframes.frames.dropWhile { it.entries.isEmpty() }

            if (frames.size != keyframes.frames.size) {
                keyframes = keyframes.copy(
                    frames = frames,
                    renderedAnimation = emptyList()
                )
            }
        }

        return listOf(keyframes)
    }
}

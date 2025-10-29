package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.conversion.ableton.utils.toFileHash
import dev.anthonyhfm.amethyst.devices.DeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

class GenericMidiExtAdapter(
    private val xml: XmlElement,
    private val offset: IntOffset,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val fileRef = xml.querySelector("MxDFullFileDrop")
            .first()
            .querySelector("FileRef")
            .firstOrNull() ?: return emptyList()

        val palette = AbletonConverter.palette

        val filePath: String = FileRef.resolveFileReference(fileRef)

        val data = if (AbletonConverter.isZip) {
            AbletonConverter.zipEntries[filePath]?.data ?: return emptyList()
        } else {
            try {
                runBlocking { PlatformFile(filePath).readBytes() }
            } catch (e: Exception) {
                return emptyList()
            }
        }

        return listOf(
            MidiFileImporter.loadData(
                data = data,
                palette = palette,
                bpm = AbletonConverter.bpm,
                xyOffset = offset
            )
        )
    }
}
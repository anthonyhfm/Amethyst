package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.devices.DeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path

class GenericMidiExtAdapter(
    private val xml: XmlElement,
    private val offset: IntOffset,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val fileRef = xml.querySelector("MxDFullFileDrop")

        if (fileRef.isEmpty()) {
            return emptyList()
        }

        val projectPath = AbletonConverter.file!!.parent()!!.path
        val palette = AbletonConverter.palette

        val filePath: String = FileRef.resolveFileReference(fileRef.first())

        return listOf(
            MidiFileImporter.loadFile(
                file = PlatformFile(filePath),
                palette = palette,
                bpm = AbletonConverter.bpm,
                xyOffset = offset
            )
        )
    }
}
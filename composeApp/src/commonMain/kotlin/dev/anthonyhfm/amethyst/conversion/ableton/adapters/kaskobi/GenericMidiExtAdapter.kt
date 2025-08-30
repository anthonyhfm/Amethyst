package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

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
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val fileRef = xml.querySelector("MxDFullFileDrop")[0]

        val filePath: String = FileRef.resolveFileReference(fileRef)

        return listOf(
            MidiFileImporter.loadFile(PlatformFile(filePath))
        )
    }
}
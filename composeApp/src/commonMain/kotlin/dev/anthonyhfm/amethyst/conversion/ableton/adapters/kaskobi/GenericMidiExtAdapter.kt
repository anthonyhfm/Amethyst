package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
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
        val fileRef = xml.querySelector("MxDFullFileDrop")

        if (fileRef.isEmpty()) {
            return emptyList()
        }

        val relativePathElements = fileRef.first().localQuerySelector("FileRef").first().children.first()
            .localQuerySelector("RelativePath").first()

        val fileName = fileRef.first().localQuerySelector("FileRef").first().children.first()
            .localQuerySelector("Name").first()
            .attributes["Value"] ?: ""

        var pathString = AbletonConverter.file!!.parent()!!.path

        relativePathElements.children.forEach {
            pathString += "/${it.attributes["Dir"]}"
        }

        return listOf(
            MidiFileImporter.loadFile(PlatformFile("$pathString/$fileName"))
        )
    }
}
package dev.anthonyhfm.amethyst.conversion.ableton

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.ableton.reader.MidiChainReader
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonImporterConfig
import dev.anthonyhfm.amethyst.conversion.ableton.utils.SimpleXmlParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.decodeFromString

object AbletonConverter : AmethystConverter {
    var file: PlatformFile? = null
        private set

    override fun convertToWorkspace(path: String): SaveableWorkspaceData {
        file = PlatformFile(path)

        val file = Zip.decode(path) // Decompresses the .als GZIP format
        val abletonXml = SimpleXmlParser.parse(file.decodeToString())
        val config = AbletonImporterConfig()

        if (abletonXml.attributes["MajorVersion"]?.toInt() != 5) {
            throw IllegalArgumentException("Unsupported Ableton Live version: ${abletonXml.attributes["MajorVersion"]}")
        }

        val xmlTracks: List<XmlElement> = abletonXml.querySelector("MidiTrack")

        val lightsTrackXML = xmlTracks.find { track ->
            val name = track.querySelector("Name")[0]
                .querySelector("UserName")[0]
                .attributes["Value"]

            name == config.launchpads[0].lightsTrack // TODO: Expand for multiple launchpads
        }

        val audioTrackXML = xmlTracks.find { track ->
            val name = track.querySelector("Name")[0]
                .querySelector("UserName")[0]
                .attributes["Value"]

            name == config.launchpads[0].audioTrack // TODO: Expand for multiple launchpads
        }

        return SaveableWorkspaceData(
            lights = MidiChainReader().readMidiChain(lightsTrackXML!!),
            launchpadDevices = listOf(
                SaveableWorkspaceData.SavableViewportLaunchpad(
                    positionX = 0f,
                    positionY = 0f,
                    type = SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                )
            )
        )
    }
}
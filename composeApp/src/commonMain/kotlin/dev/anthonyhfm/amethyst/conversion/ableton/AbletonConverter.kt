package dev.anthonyhfm.amethyst.conversion.ableton

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.ableton.reader.BPMReader
import dev.anthonyhfm.amethyst.conversion.ableton.reader.MidiChainReader
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonImporterConfig
import dev.anthonyhfm.amethyst.conversion.ableton.utils.SimpleXmlParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.audio.AudioClip
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.decodeFromString

object AbletonConverter : AmethystConverter {
    var file: PlatformFile? = null
        private set

    var bpm: Double = 120.0
        private set

    val audioClips: MutableList<AudioClip> = mutableListOf()

    override fun convertToWorkspace(path: String): SaveableWorkspaceData {
        file = PlatformFile(path)

        val file = Zip.decode(path) // Decompresses the .als GZIP format
        val abletonXml = SimpleXmlParser.parse(file.decodeToString())

        bpm = BPMReader().readBPM(abletonXml)

        if (abletonXml.attributes["MajorVersion"]?.toInt() != 5) {
            throw IllegalArgumentException("Unsupported Ableton Live version: ${abletonXml.attributes["MajorVersion"]}")
        }

        val xmlTracks: List<XmlElement> = abletonXml.querySelector("MidiTrack")

        // Get the two tracks (audio, lights)
        val sortedTracks = xmlTracks.sortedByDescending {
            MidiChainReader()
                .getChainWeight(it)
        }.chunked(2).first()

        val lightsTrackXML = sortedTracks.find {
            it.querySelector("InstrumentGroupDevice").isEmpty()
        }

        val audioTrackXML = sortedTracks.find {
            it.querySelector("InstrumentGroupDevice").isNotEmpty()
        }

        return SaveableWorkspaceData(
            lights = MidiChainReader().readMidiChain(lightsTrackXML!!),
            sampling = MidiChainReader().readMidiChain(audioTrackXML!!),
            audioClips = audioClips.toList(),
            settings = WorkspaceSettings(
                bpm = bpm
            ),
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
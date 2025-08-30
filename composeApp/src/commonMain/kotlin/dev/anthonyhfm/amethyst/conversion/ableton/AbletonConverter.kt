package dev.anthonyhfm.amethyst.conversion.ableton

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.ableton.reader.BPMReader
import dev.anthonyhfm.amethyst.conversion.ableton.reader.MidiChainReader
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonImporterConfig
import dev.anthonyhfm.amethyst.conversion.ableton.utils.SimpleXmlParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.decodeFromString

object AbletonConverter : AmethystConverter {
    var file: PlatformFile? = null
        private set

    var bpm: Double = 120.0
        private set

    var liveVersion: LiveVersion? = null
        private set

    override fun convertToWorkspace(path: String): SaveableWorkspaceData {
        file = PlatformFile(path)

        val file = Zip.decode(path) // Decompresses the .als GZIP format
        val abletonXml = SimpleXmlParser.parse(file.decodeToString())

        bpm = BPMReader().readBPM(abletonXml)

        val minorVersion: String = abletonXml.attributes["MinorVersion"]!!

        liveVersion = when {
            minorVersion.startsWith("9") -> LiveVersion.LIVE_9
            minorVersion.startsWith("10") -> LiveVersion.LIVE_10
            minorVersion.startsWith("11") -> LiveVersion.LIVE_11
            minorVersion.startsWith("12") -> LiveVersion.LIVE_12

            else -> null
        }

        val xmlTracks: List<XmlElement> = abletonXml.querySelector("MidiTrack")

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
            lights = lightsTrackXML?.let { MidiChainReader().readMidiChain(it) } ?: StateChain(emptyList()),
            sampling = audioTrackXML?.let { MidiChainReader().readMidiChain(it) } ?: StateChain(emptyList()),
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

    enum class LiveVersion {
        LIVE_12, // Bro who uses Live 12 fr
        LIVE_11,
        LIVE_10,
        LIVE_9
    }
}
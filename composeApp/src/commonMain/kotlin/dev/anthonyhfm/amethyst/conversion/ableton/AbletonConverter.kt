package dev.anthonyhfm.amethyst.conversion.ableton

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.reader.BPMReader
import dev.anthonyhfm.amethyst.conversion.ableton.reader.MidiChainReader
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayout
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayoutDetector
import dev.anthonyhfm.amethyst.conversion.ableton.utils.OriginalSimplerPrerenderer
import dev.anthonyhfm.amethyst.conversion.ableton.utils.PaletteFileParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.SimpleXmlParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString

object AbletonConverter : AmethystConverter {
    var file: PlatformFile? = null
        private set

    var bpm: Double = 120.0
        private set

    var liveVersion: LiveVersion? = null
        private set

    var palette: Array<Triple<Int, Int, Int>> = Palettes.novation
        private set

    var audioMap: Map<OriginalSimplerAdapter.OriginalSimplerData, ClipChainDeviceState> = emptyMap()
        private set

    override fun convertToWorkspace(path: String, palettePath: String?): SaveableWorkspaceData {
        MxDeviceMidiEffectAdapter.fileHashMap.clear()

        file = PlatformFile(path)

        val file = Zip.decode(path) // Decompresses the .als GZIP format
        val abletonXml = SimpleXmlParser.parse(file.decodeToString())

        bpm = BPMReader().readBPM(abletonXml)

        if (palettePath == null) {
            palette = Palettes.novation
        } else {
            val paletteFile = PlatformFile(palettePath ?: "")
            runBlocking {
                val content = paletteFile.readString()
                palette = PaletteFileParser.parsePaletteFileContent(content)
            }
        }

        val minorVersion: String = abletonXml.attributes["MinorVersion"]!!

        liveVersion = when {
            minorVersion.startsWith("9") -> LiveVersion.LIVE_9
            minorVersion.startsWith("10") -> LiveVersion.LIVE_10
            minorVersion.startsWith("11") -> LiveVersion.LIVE_11
            minorVersion.startsWith("12") -> LiveVersion.LIVE_12

            else -> null
        }

        val xmlTracks: List<XmlElement> = abletonXml.querySelector("MidiTrack")

        AbletonLayoutDetector.detectLayout(xmlTracks)

        val sortedTracks = xmlTracks.sortedByDescending {
            MidiChainReader()
                .getChainWeight(it)
        }.chunked(2).first()

        val lightsTrackXML = sortedTracks.find {
            it.querySelector("InstrumentGroupDevice").isEmpty() && it.querySelector("OriginalSimpler").isEmpty()
        }

        val audioTrackXML = sortedTracks.find {
            it.querySelector("OriginalSimpler").isNotEmpty()
        }

        audioTrackXML?.let {
            val simpler = OriginalSimplerPrerenderer()

            audioMap = simpler.decodeAll(listOf(it))
        }

        val lights = lightsTrackXML?.let { MidiChainReader().readMidiChain(it) } ?: StateChain(emptyList())
        val samples = audioTrackXML?.let { MidiChainReader().readMidiChain(it) } ?: StateChain(emptyList())

        audioMap = mapOf() // Clear the map to free memory after conversion is done
        MxDeviceMidiEffectAdapter.fileHashMap.clear()

        return SaveableWorkspaceData(
            lights = lights,
            sampling = samples,
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
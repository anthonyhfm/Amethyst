package dev.anthonyhfm.amethyst.conversion.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.Ableton
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiChainReader
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayout
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayoutDetector
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonTutorialDetector
import dev.anthonyhfm.amethyst.conversion.ableton.utils.Dual2LightLayoutScanner
import dev.anthonyhfm.amethyst.conversion.ableton.utils.OriginalSimplerPrerenderer
import dev.anthonyhfm.amethyst.conversion.ableton.utils.PaletteFileParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.ProjectSpecials
import dev.anthonyhfm.amethyst.conversion.ableton.utils.toFileHash
import dev.anthonyhfm.amethyst.core.util.FileHelper
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.ZipEntry
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.nameWithoutExtension
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor

object AbletonConverter : AmethystConverter {
    var file: PlatformFile? = null
        private set

    var special = ProjectSpecials()

    var bpm: Double = 120.0
        private set

    var liveVersion: LiveVersion? = null
        private set

    var projectLayout: AbletonLayout? = null
        private set

    var palette: Array<Triple<Int, Int, Int>> = Palettes.novation
        private set

    var audioMap: Map<OriginalSimplerAdapter.OriginalSimplerData, ClipChainDeviceState> = emptyMap()
        private set

    var isZip: Boolean = false
        private set

    val zipEntries: MutableMap<String, ZipEntry> = mutableMapOf()

    var zipStartPath: String = ""
        private set

    @OptIn(ExperimentalXmlUtilApi::class)
    val xml = XML(AbletonDevice.module) {
        defaultPolicy {
            autoPolymorphic = true

            unknownChildHandler = object : UnknownChildHandler {
                override fun handleUnknownChildRecovering(
                    input: XmlReader,
                    inputKind: InputKind,
                    descriptor: XmlDescriptor,
                    name: QName?,
                    candidates: Collection<Any>,
                ): List<XML.ParsedData<*>> {
                    return emptyList()
                }
            }
        }
    }

    @OptIn(ExperimentalXmlUtilApi::class)
    override fun convertZipToWorkspace(file: PlatformFile): SavableWorkspaceData {
        isZip = true

        zipEntries.clear()
        val entries = Zip.getEntries(file)

        entries.forEach { entry ->
            println("Entry: ${entry.path}")
        }
        FileHelper.clearCache()

        entries.filter {
            it.path.endsWith(".amxd") && !it.path.contains("__MACOSX")
        }.forEach {
            println("Hash (${it.data.toFileHash()}) - ${it.path.substringAfterLast("/")}")
        }

        zipStartPath = entries.map { it.path }
            .filter { it.endsWith(".als") }
            .minBy { it.length }
            .substringBeforeLast("/")

        zipEntries.putAll(
            entries.associateBy { it.path }
        )

        val alsEntry = entries
            .filter { it.path.endsWith(".als") }
            .minBy { it.path.length }

        val projectName = alsEntry.path.substringAfterLast("/").removeSuffix(".als")

        val decodedAlsBytes = Zip.decode(alsEntry.data)
        val sanitizedAlsString = decodedAlsBytes.decodeToString()
            .replace("> ", ">")
            .replace(" <", "<")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")

        val abletonData = xml.decodeFromString<Ableton>(sanitizedAlsString)

        zipEntries.remove(alsEntry.path)

        return runLiveConversion(
            name = projectName,
            abletonData = abletonData
        ).also {
            isZip = false
            zipEntries.clear()
        }
    }

    override fun convertToWorkspace(path: String, palettePath: String?): SavableWorkspaceData =
        convertToWorkspace(PlatformFile(path), palettePath)

    fun convertToWorkspace(file: PlatformFile, palettePath: String?): SavableWorkspaceData {
        MxDeviceMidiEffectAdapter.fileHashMap.clear()
        isZip = false

        this.file = file

        val liveSetBytes = Zip.decode(runBlocking { AbletonConverter.file?.readBytes() ?: ByteArray(0) }) // Decompresses the .als GZIP format
        val sanitizedAlsString = liveSetBytes.decodeToString()
            .replace("> ", ">")
            .replace(" <", "<")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")
        val abletonData = xml.decodeFromString<Ableton>(sanitizedAlsString)

        if (palettePath == null) {
            palette = Palettes.novation
        } else {
            val paletteFile = PlatformFile(palettePath)
            runBlocking {
                val content = paletteFile.readString()
                palette = PaletteFileParser.parsePaletteFileContent(content)
            }
        }

        return runLiveConversion(AbletonConverter.file?.nameWithoutExtension ?: "Ableton Live-Set", abletonData)
    }

    fun runLiveConversion(name: String, abletonData: Ableton): SavableWorkspaceData {
        val audioRenderer = OriginalSimplerPrerenderer()

        val layout = AbletonLayoutDetector.detectLayout(
            tracks = abletonData.liveSet.tracks.midiTracks
        )

        bpm = abletonData.liveSet.masterTrack.deviceChain.mixer.tempo.manual.value
        projectLayout = layout

        val autoPlayData: AutoPlayData = AbletonTutorialDetector.getAutoPlayData(layout, abletonData.liveSet.tracks.midiTracks)

        when (layout) {
            is AbletonLayout.Single -> {
                audioMap = layout.audioTrack?.let { audioRenderer.decodeAll(listOf(it)) } ?: mapOf()
            }

            is AbletonLayout.Dual2Light -> {
                val leftTracks = listOfNotNull(layout.audioLeft, layout.lightsLeft)
                val rightTracks = listOfNotNull(layout.audioRight, layout.lightsRight)

                audioMap = audioRenderer.decodeAll(leftTracks + rightTracks)
            }

            is AbletonLayout.Dual4Light -> {
                val leftTracks = listOfNotNull(layout.audioLeft, layout.lightsLeft, layout.lightsLeftToRight)
                val rightTracks = listOfNotNull(layout.audioRight, layout.lightsRight, layout.lightsRightToLeft)

                audioMap = audioRenderer.decodeAll(leftTracks + rightTracks)
            }
        }

        liveVersion = when {
            abletonData.minorVersion.startsWith("9") -> LiveVersion.LIVE_9
            abletonData.minorVersion.startsWith("10") -> LiveVersion.LIVE_10
            abletonData.minorVersion.startsWith("11") -> LiveVersion.LIVE_11
            abletonData.minorVersion.startsWith("12") -> LiveVersion.LIVE_12

            else -> null
        }

        if (layout is AbletonLayout.Dual2Light) {
            layout.lightsLeft?.let { Dual2LightLayoutScanner.scanTrackForMixer(it, IntOffset.Zero) }
            layout.lightsRight?.let { Dual2LightLayoutScanner.scanTrackForMixer(it, IntOffset(x = 10, y = 0)) }
        }

        val lights = if (layout is AbletonLayout.Dual2Light || layout is AbletonLayout.Dual4Light) {
            StateChain(
                devices = listOf(
                    GroupChainDeviceState(
                        groups = if (layout is AbletonLayout.Dual2Light) {
                            listOf(
                                Group(
                                    name = "Left",
                                    stateChain = layout.lightsLeft?.let {
                                        MidiChainReader()
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                ),
                                Group(
                                    name = "Right",
                                    stateChain = layout.lightsRight?.let {
                                        MidiChainReader(offset = IntOffset(x = 10, y = 0))
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                )
                            )
                        } else if (layout is AbletonLayout.Dual4Light) {
                            listOf(
                                Group(
                                    name = "Left",
                                    stateChain = layout.lightsLeft?.let {
                                        MidiChainReader()
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                ),
                                Group(
                                    name = "Left to Right",
                                    stateChain = layout.lightsLeftToRight?.let {
                                        MidiChainReader(outputOffset = IntOffset(x = 10, y = 0))
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                ),
                                Group(
                                    name = "Right",
                                    stateChain = layout.lightsRight?.let {
                                        MidiChainReader(offset = IntOffset(x = 10, y = 0))
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                ),
                                Group(
                                    name = "Right to Left",
                                    stateChain = layout.lightsRightToLeft?.let {
                                        MidiChainReader(
                                            offset = IntOffset(x = 10, y = 0),
                                            outputOffset = IntOffset(x = -10, y = 0)
                                        )
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                )
                            )
                        } else error("This should never happen")
                    )
                )
            )
        } else {
            (layout as AbletonLayout.Single).lightsTrack?.let { MidiChainReader().readMidiChain(it) } ?: StateChain(emptyList())
        }

        val samples = if (layout is AbletonLayout.Dual2Light || layout is AbletonLayout.Dual4Light) {
            StateChain(
                devices = listOf(
                    GroupChainDeviceState(
                        groups = if (layout is AbletonLayout.Dual2Light) {
                            listOf(
                                Group(
                                    name = "Left",
                                    stateChain = layout.audioLeft?.let {
                                        MidiChainReader()
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                ),
                                Group(
                                    name = "Right",
                                    stateChain = layout.audioRight?.let {
                                        MidiChainReader(offset = IntOffset(x = 10, y = 0))
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                )
                            )
                        } else if (layout is AbletonLayout.Dual4Light) {
                            listOf(
                                Group(
                                    name = "Left",
                                    stateChain = layout.audioLeft?.let {
                                        MidiChainReader()
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                ),
                                Group(
                                    name = "Right",
                                    stateChain = layout.audioRight?.let {
                                        MidiChainReader(offset = IntOffset(x = 10, y = 0))
                                            .readMidiChain(it)
                                    } ?: StateChain(emptyList())
                                )
                            )
                        } else error("This should never happen")
                    )
                )
            )
        } else {
            (layout as AbletonLayout.Single).audioTrack?.let { MidiChainReader().readMidiChain(it) } ?: StateChain(emptyList())
        }

        audioMap = emptyMap()
        MxDeviceMidiEffectAdapter.fileHashMap.clear()
        projectLayout = null

        return SavableWorkspaceData(
            title = name,
            lights = lights,
            sampling = samples,
            autoPlay = autoPlayData,
            settings = WorkspaceSettings(
                bpm = bpm
            ),
            launchpadDevices = if (layout is AbletonLayout.Single) {
                listOf(
                    SavableWorkspaceData.SavableViewportLaunchpad(
                        positionX = 0f,
                        positionY = 0f,
                        type = SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                    )
                )
            } else {
                listOf(
                    SavableWorkspaceData.SavableViewportLaunchpad(
                        positionX = 0f,
                        positionY = 0f,
                        type = SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                    ),
                    SavableWorkspaceData.SavableViewportLaunchpad(
                        positionX = 10f,
                        positionY = 0f,
                        type = SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                    )
                )
            }
        )
    }

    enum class LiveVersion {
        LIVE_12, // Bro who uses Live 12 fr
        LIVE_11,
        LIVE_10,
        LIVE_9
    }
}

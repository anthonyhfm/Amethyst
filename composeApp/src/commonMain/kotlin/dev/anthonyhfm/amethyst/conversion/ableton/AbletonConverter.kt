package dev.anthonyhfm.amethyst.conversion.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.reader.BPMReader
import dev.anthonyhfm.amethyst.conversion.ableton.reader.MidiChainReader
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayout
import dev.anthonyhfm.amethyst.conversion.ableton.utils.AbletonLayoutDetector
import dev.anthonyhfm.amethyst.conversion.ableton.utils.OriginalSimplerPrerenderer
import dev.anthonyhfm.amethyst.conversion.ableton.utils.PaletteFileParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.ProjectSpecials
import dev.anthonyhfm.amethyst.conversion.ableton.utils.SimpleXmlParser
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.nameWithoutExtension
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.runBlocking

object AbletonConverter : AmethystConverter {
    var file: PlatformFile? = null
        private set

    var special = ProjectSpecials()

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
        val audioRenderer = OriginalSimplerPrerenderer()

        val layout: AbletonLayout = AbletonLayoutDetector.detectLayout(
            tracks = abletonXml.querySelector("MidiTrack")
        )

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

        return SaveableWorkspaceData(
            title = this.file?.nameWithoutExtension ?: "Ableton Converted Workspace",
            lights = lights,
            sampling = samples,
            settings = WorkspaceSettings(
                bpm = bpm
            ),
            launchpadDevices = if (layout is AbletonLayout.Single) {
                listOf(
                    SaveableWorkspaceData.SavableViewportLaunchpad(
                        positionX = 0f,
                        positionY = 0f,
                        type = SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                    )
                )
            } else {
                listOf(
                    SaveableWorkspaceData.SavableViewportLaunchpad(
                        positionX = 0f,
                        positionY = 0f,
                        type = SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                    ),
                    SaveableWorkspaceData.SavableViewportLaunchpad(
                        positionX = 10f,
                        positionY = 0f,
                        type = SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
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
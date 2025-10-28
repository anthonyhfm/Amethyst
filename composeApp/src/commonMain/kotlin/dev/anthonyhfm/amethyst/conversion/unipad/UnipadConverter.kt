package dev.anthonyhfm.amethyst.conversion.unipad

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.unipad.data.KeyLED
import dev.anthonyhfm.amethyst.conversion.unipad.data.KeySound
import dev.anthonyhfm.amethyst.conversion.unipad.data.DecodedAudioClip
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.ZipEntry
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.runBlocking

object UnipadConverter : AmethystConverter {
    val entries: MutableMap<String, ZipEntry> = mutableMapOf()

    override fun convertZipToWorkspace(file: PlatformFile): SaveableWorkspaceData {
        entries.clear()
        entries.putAll(
            from = Zip.getEntries(file)
                .associateBy {
                    it.path
                }
                .toMutableMap()
        )

        val infoKey = entries.keys.first { it.endsWith("Info") || it.endsWith("info") }
        val infoMap: Map<String, String> = entries[infoKey]?.data
            ?.decodeToString()
            ?.trim()
            ?.split("\n")
            ?.map { it.trim() }?.associate {
                it.split("=")
                    .let { (key, value) -> key to value }
            } ?: emptyMap()

        val clipMap = runBlocking {
            KeySound.loadAllAudioClips()
        }

        return SaveableWorkspaceData(
            title = infoMap["title"] ?: "Untitled Workspace",
            author = infoMap["producerName"] ?: "Unknown",
            lights = createLightsChain(infoMap["chain"]?.toInt() ?: 1),
            sampling = createAudioChain(infoMap["chain"]?.toInt() ?: 1, clipMap),
            launchpadDevices = listOf(
                SaveableWorkspaceData.SavableViewportLaunchpad(
                    positionX = 0f,
                    positionY = 0f,
                    type = SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                )
            ),
        )
    }

    // still accept old function
    override fun convertToWorkspace(path: String, palettePath: String?): SaveableWorkspaceData {
        return convertZipToWorkspace(PlatformFile(path))
    }

    private fun createAudioChain(pages: Int, clipMap: Map<String, DecodedAudioClip>): StateChain {
        val keySound = entries.filter { it.key.startsWith("KeySound") }.values.firstOrNull()?.data ?: return StateChain()

        return StateChain(
            devices = listOf(
                GroupChainDeviceState(
                    groups = List(pages) { index ->
                        Group(
                            name = "Page ${index + 1}",
                            stateChain = KeySound.createChain(
                                page = index,
                                clipMap = clipMap,
                                entries = keySound.decodeToString().trim().split("\n").filter {
                                    it.startsWith("${index + 1}")
                                }
                            )
                        )
                    }
                )
            )
        )
    }

    private fun createLightsChain(pages: Int): StateChain {
        val entries = entries.filter { it.key.startsWith("keyLED/") }

        return StateChain(
            devices = listOf(
                GroupChainDeviceState(
                    groups = List(pages) { index ->
                        Group(
                            name = "Page ${index + 1}",
                            stateChain = KeyLED.createChain(
                                page = index,
                                entries = entries.filter { it.key.startsWith("keyLED/${index + 1}") }.map { it.key }
                            )
                        )
                    }.plus(
                        Group(
                            name = "Page Switch",
                            stateChain = StateChain(
                                devices = listOf(
                                    GroupChainDeviceState(
                                        groups = List(pages) { index ->
                                            Group(
                                                name = "Page Switch ${index + 1}",
                                                stateChain = StateChain(
                                                    devices = listOf(
                                                        CoordinateFilterChainDeviceState(
                                                            filters = listOf(Pair(9, 1 + index))
                                                        ),
                                                        SwitchChainDeviceState(
                                                            macro = 0,
                                                            value = index
                                                        ),
                                                        ColorChainDeviceState(
                                                            r = 0f,
                                                            g = 0f,
                                                            b = 0f
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }
}
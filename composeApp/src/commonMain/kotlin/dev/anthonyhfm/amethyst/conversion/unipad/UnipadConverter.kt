package dev.anthonyhfm.amethyst.conversion.unipad

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.unipad.data.KeyLED
import dev.anthonyhfm.amethyst.conversion.unipad.data.KeySound
import dev.anthonyhfm.amethyst.core.audio.AudioClip
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.ZipEntry
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData

object UnipadConverter : AmethystConverter {
    override fun convertToWorkspace(path: String): SaveableWorkspaceData {
        val entries = Zip.getEntries(path)

        if (!entries.contains(ZipEntry("Info")) && !entries.contains(ZipEntry("info"))) {
            throw IllegalArgumentException("Invalid Unipad file: Missing 'Info' entry")
        }

        val infomap: Map<String, String> = Zip.getInputStream(path, if (entries.contains(ZipEntry("Info"))) "Info" else "info")
            .decodeToString()
            .trim()
            .split("\n")
            .map { it.trim() }.associate {
                it.split("=")
                    .let { (key, value) -> key to value }
            }

        val clipMap = KeySound.loadAllAudioClips(path)

        return SaveableWorkspaceData(
            title = infomap["title"] ?: "Untitled Workspace",
            author = infomap["producerName"] ?: "Unknown",
            lights = createLightsChain(path, infomap["chain"]?.toInt() ?: 1),
            sampling = createAudioChain(path, infomap["chain"]?.toInt() ?: 1, clipMap.mapValues { it.value?.key ?: "" }),
            launchpadDevices = listOf(
                SaveableWorkspaceData.SavableViewportLaunchpad(
                    positionX = 0f,
                    positionY = 0f,
                    type = SaveableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                )
            ),
            audioClips = clipMap.values.map { it!! },
        )
    }

    fun createAudioChain(path: String, pages: Int, clipMap: Map<String, String>): StateChain {
        val keySound = Zip.getInputStream(path, if (Zip.getEntries(path).contains(ZipEntry("KeySound"))) "KeySound" else "keySound")

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

    fun createLightsChain(path: String, pages: Int): StateChain {
        val entries = Zip.getEntries(path).filter { it.path.startsWith("keyLED/") }

        return StateChain(
            devices = listOf(
                GroupChainDeviceState(
                    groups = List(pages) { index ->
                        Group(
                            name = "Page ${index + 1}",
                            stateChain = KeyLED.createChain(
                                path,
                                page = index,
                                entries = entries.filter { it.path.startsWith("keyLED/${index + 1}") }.map { it.path }
                            )
                        )
                    }.plus(
                        Group(
                            name = "Page Switch",
                            stateChain = StateChain(
                                listOf(
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
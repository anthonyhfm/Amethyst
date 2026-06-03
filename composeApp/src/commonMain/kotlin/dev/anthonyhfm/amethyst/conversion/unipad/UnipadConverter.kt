package dev.anthonyhfm.amethyst.conversion.unipad

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.unipad.data.KeyLED
import dev.anthonyhfm.amethyst.conversion.unipad.data.KeySound
import dev.anthonyhfm.amethyst.conversion.unipad.data.DecodedAudioClip
import dev.anthonyhfm.amethyst.conversion.unipad.data.UnipadAutoPlay
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.ZipEntry
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.runBlocking

object UnipadConverter : AmethystConverter {
    val entries: MutableMap<String, ZipEntry> = mutableMapOf()

    override fun convertZipToWorkspace(file: PlatformFile): SavableWorkspaceData {
        println("Starting Zip Decoding")

        entries.clear()
        entries.putAll(
            from = Zip.getEntries(file)
                .associateBy {
                    it.path
                }
                .toMutableMap()
        )

        println("Entries in zip: ${entries.size}")

        val rawInfoKey = entries.keys.firstOrNull { key ->
            val lower = key.lowercase()
            lower == "info" || lower.endsWith("/info")
        } ?: throw IllegalArgumentException("UniPack is missing required 'info' file")
        val rootPrefix = rawInfoKey.dropLast("info".length)

        if (rootPrefix.isNotEmpty()) {
            println("Detected ZIP root prefix: '$rootPrefix' — re-indexing entries")
            val reindexed = entries.values
                .filter { it.path.startsWith(rootPrefix) }
                .associateBy { it.path.removePrefix(rootPrefix) }
            entries.clear()
            entries.putAll(reindexed)
            println("Entries after re-indexing: ${entries.size}")
        }

        val infoKey = entries.keys.firstOrNull { key ->
            val lower = key.lowercase()
            lower == "info" || lower.endsWith("/info")
        } ?: throw IllegalArgumentException("UniPack is missing required 'info' file")

        val infoMap: Map<String, String> = entries[infoKey]?.data
            ?.decodeToString()
            ?.replace("\r\n", "\n")
            ?.replace("\r", "\n")
            ?.trim()
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.contains("=") }
            ?.associate {
                val idx = it.indexOf('=')
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            } ?: emptyMap()

        val chain = infoMap["chain"]?.toIntOrNull() ?: 1

        require(chain in 1..24) {
            "UniPack 'chain' value is $chain, must be in range 1–24"
        }

        println("Starting Audio-Clip Decoding")

        val clipMap = runBlocking {
            KeySound.loadAllAudioClips()
        }

        println("Finished Audio-Clip Decoding (${clipMap.size} total).")

        return SavableWorkspaceData(
            title = infoMap["title"] ?: "Untitled Workspace",
            author = infoMap["producerName"] ?: "Unknown",
            lights = createLightsChain(chain),
            sampling = createAudioChain(chain, clipMap),
            autoPlay = run {
                val autoPlayEntry = entries.entries.firstOrNull {
                    it.key.lowercase().endsWith("autoplay")
                }
                if (autoPlayEntry != null) {
                    try {
                        UnipadAutoPlay.getAutoPlayData(
                            autoPlayString = autoPlayEntry.value.data.decodeToString()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        AutoPlayData(emptyMap())
                    }
                } else {
                    AutoPlayData(emptyMap())
                }
            },
            launchpadDevices = listOf(
                SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(
                    positionX = 0f,
                    positionY = 0f
                )
            ),
        ).also {
            entries.clear()
        }
    }

    // still accept old function
    override fun convertToWorkspace(path: String, palettePath: String?): SavableWorkspaceData {
        return convertZipToWorkspace(PlatformFile(path))
    }

    private fun createAudioChain(pages: Int, clipMap: Map<String, DecodedAudioClip>): StateChain {
        val keySoundEntry = entries.entries.firstOrNull {
            it.key.lowercase().endsWith("keysound")
        } ?: return StateChain()
        val keySound = keySoundEntry.value.data

        return StateChain(
            devices = listOf(
                GroupChainDeviceState(
                    groups = List(pages) { index ->
                        Group(
                            name = "Page ${index + 1}",
                            stateChain = KeySound.createChain(
                                page = index,
                                clipMap = clipMap,
                                entries = keySound.decodeToString()
                                    .replace("\r\n", "\n").replace("\r", "\n")
                                    .trim().split("\n").filter {
                                        it.startsWith("${index + 1} ")
                                    }
                            )
                        )
                    }
                )
            )
        )
    }

    private fun createLightsChain(pages: Int): StateChain {
        val entries = entries.filter { it.key.lowercase().startsWith("keyled/") }

        return StateChain(
            devices = listOf(
                GroupChainDeviceState(
                    groups = List(pages) { index ->
                        Group(
                            name = "Page ${index + 1}",
                            stateChain = KeyLED.createChain(
                                page = index,
                                entries = entries.filter {
                                    it.key.lowercase().startsWith("keyled/${index + 1} ") ||
                                    it.key.lowercase() == "keyled/${index + 1}"
                                }.map { it.key }
                            )
                        ).also {
                            println("Created group ${it.name}")
                        }
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
                                                        MacroControlChainDeviceState(
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
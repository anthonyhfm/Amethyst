package dev.anthonyhfm.amethyst.conversion.unipad.data

import dev.anthonyhfm.amethyst.core.audio.AudioClip
import dev.anthonyhfm.amethyst.core.audio.AudioPlayer
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object KeySound {
    fun loadAllAudioClips(zipFile: String): MutableMap<String, AudioClip?> {
        val entries = Zip.getEntries(zipFile).filter { it.path.startsWith("Sounds/") && !it.isDirectory }
        val scope = CoroutineScope(Dispatchers.IO)
        val clipMap: MutableMap<String, AudioClip?> = mutableMapOf()

        entries.forEach { entry ->
            val clipName = entry.path.removePrefix("Sounds/")
            clipMap[clipName] = null

            scope.launch {
                println("Loading $clipName")
                clipMap[clipName] = AudioPlayer.getAudioClip(Zip.getInputStream(zipFile, entry.path))

                cancel()
            }
        }

        while (clipMap.any { it.value == null }) {
            runBlocking {
                delay(100)
            }
        }

        println("Finished loading ${clipMap.size} audio clips.")

        return clipMap
    }

    fun createChain(page: Int, clipMap: Map<String, String>, entries: List<String>): StateChain {
        val groups = mutableListOf<Group>()

        entries.forEachIndexed { index, entry ->
            val x = entry.split(" ")[2].toInt()
            val y = entry.split(" ")[1].toInt()
            val clip = entry.split(" ")[3]

            groups.add(
                Group(
                    name = "Single ${index + 1}",
                    stateChain = StateChain(
                        devices = listOf(
                            CoordinateFilterChainDeviceState(
                                filters = listOf(Pair(x, y))
                            ),
                            ClipChainDeviceState(
                                audioKey = clipMap[clip] ?: "",
                            )
                        )
                    )
                )
            )
        }

        println("Page ${page + 1} (audio) not fully implemented yet.")

        return StateChain(
            devices = listOf(
                MacroFilterChainDeviceState(
                    value = page
                ),
                GroupChainDeviceState(
                    groups = groups
                )
            )
        )
    }
}
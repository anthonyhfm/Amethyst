package dev.anthonyhfm.amethyst.conversion.unipad.data

import dev.anthonyhfm.amethyst.core.audio.AudioClip
import dev.anthonyhfm.amethyst.core.audio.AudioPlayer
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object KeySound {
    @OptIn(DelicateCoroutinesApi::class)
    fun loadAllAudioClips(zipFile: String): MutableMap<String, AudioClip?> {
        val entries = Zip.getEntries(zipFile).filter { (it.path.startsWith("Sounds/") || it.path.startsWith("sounds/")) && !it.isDirectory }
        val clipMap: MutableMap<String, AudioClip?> = mutableMapOf()
        val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(4))

        entries.forEach { entry ->
            val clipName = entry.path.removePrefix("Sounds/").removePrefix("sounds/").trim()
            clipMap[clipName] = null

            scope.launch {
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

        println(clipMap)

        entries.map {
            "${it.split(" ")[0]} ${it.split(" ")[1]} ${it.split(" ")[2]}"
        }.distinct().forEachIndexed { index, entry ->
            val x = entry.split(" ")[2].toInt()
            val y = entry.split(" ")[1].toInt()

            val entries = entries.filter {
                it.split(" ")[1].toInt() == y && it.split(" ")[2].toInt() == x
            }

            if (entries.count() > 1) {
                groups.add(
                    Group(
                        name = "Single ${index + 1}",
                        stateChain = StateChain(
                            devices = listOf(
                                CoordinateFilterChainDeviceState(
                                    filters = listOf(Pair(x, y))
                                ),
                                MultiGroupChainDeviceState(
                                    groups = entries.map { entry ->
                                        val clip = entry.split(" ")[3].trim()
                                        Group(
                                            name = clip,
                                            stateChain = StateChain(
                                                devices = listOf(
                                                    ClipChainDeviceState(
                                                        audioKey = clipMap[clip]!!
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
            } else {
                val clip = entries[0].split(" ")[3].trim()

                groups.add(
                    Group(
                        name = "Single ${index + 1}",
                        stateChain = StateChain(
                            devices = listOf(
                                CoordinateFilterChainDeviceState(
                                    filters = listOf(Pair(x, y))
                                ),
                                ClipChainDeviceState(
                                    audioKey = clipMap[clip]!!,
                                )
                            )
                        )
                    )
                )
            }
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
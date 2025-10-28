package dev.anthonyhfm.amethyst.conversion.unipad.data

import dev.anthonyhfm.amethyst.conversion.unipad.UnipadConverter
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.*

data class DecodedAudioClip(
    val name: String,
    val rawData: ByteArray?,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitDepth: Int = 16,
    val isLoaded: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedAudioClip) return false
        if (name != other.name) return false
        if (rawData != null) {
            if (other.rawData == null) return false
            if (!rawData.contentEquals(other.rawData)) return false
        } else if (other.rawData != null) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (bitDepth != other.bitDepth) return false
        if (isLoaded != other.isLoaded) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (rawData?.contentHashCode() ?: 0)
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + bitDepth
        result = 31 * result + isLoaded.hashCode()
        return result
    }
}

object KeySound {
    suspend fun loadAllAudioClips(): Map<String, DecodedAudioClip> = withContext(Dispatchers.IO) {
        val clipMap = mutableMapOf<String, DecodedAudioClip>()

        // Process audio files in parallel
        val jobs = UnipadConverter.entries.values.map { entry ->
            async(Dispatchers.Default) {
                val clipName = entry.path.removePrefix("Sounds/").removePrefix("sounds/").trim()

                try {
                    val audioData = entry.data
                    val audioSignal = AudioDecoder.decodeAudioData(audioData, clipName)

                    if (audioSignal != null) {
                        clipName to DecodedAudioClip(
                            name = clipName,
                            rawData = audioSignal.rawData,
                            sampleRate = audioSignal.sampleRate,
                            channels = audioSignal.channels,
                            bitDepth = audioSignal.bitDepth,
                            isLoaded = true
                        )
                    } else {
                        println("Failed to decode audio clip: $clipName")
                        clipName to DecodedAudioClip(
                            name = clipName,
                            rawData = null,
                            isLoaded = false
                        )
                    }
                } catch (e: Exception) {
                    println("Error loading audio clip '$clipName': ${e.message}")
                    clipName to DecodedAudioClip(
                        name = clipName,
                        rawData = null,
                        isLoaded = false
                    )
                }
            }
        }

        // Wait for all audio clips to be processed
        val results = jobs.awaitAll()
        clipMap.putAll(results)

        println("Finished loading ${clipMap.count { it.value.isLoaded }} audio clips successfully (${clipMap.size} total).")

        return@withContext clipMap.toMap()
    }

    fun createChain(page: Int, clipMap: Map<String, DecodedAudioClip>, entries: List<String>): StateChain {
        val groups = mutableListOf<Group>()

        entries.map {
            "${it.split(" ")[0]} ${it.split(" ")[1]} ${it.split(" ")[2]}"
        }.distinct().forEachIndexed { index, entry ->
            val x = entry.split(" ")[2].toInt()
            val y = entry.split(" ")[1].toInt()

            val matchingEntries = entries.filter {
                it.split(" ")[1].toInt() == y && it.split(" ")[2].toInt() == x
            }

            if (matchingEntries.count() > 1) {
                groups.add(
                    Group(
                        name = "Multi $x,$y",
                        stateChain = StateChain(
                            devices = listOf(
                                CoordinateFilterChainDeviceState(
                                    filters = listOf(Pair(x, y))
                                ),
                                MultiGroupChainDeviceState(
                                    groups = matchingEntries.mapNotNull { entry ->
                                        val clipName = entry.split(" ")[3].trim()
                                        val audioClip = clipMap[clipName]

                                        if (audioClip?.isLoaded == true) {
                                            Group(
                                                name = clipName,
                                                stateChain = StateChain(
                                                    devices = listOf(
                                                        ClipChainDeviceState(
                                                            fileName = audioClip.name,
                                                            rawData = audioClip.rawData,
                                                            sampleRate = audioClip.sampleRate,
                                                            channels = audioClip.channels,
                                                            bitDepth = audioClip.bitDepth,
                                                            isLoaded = true
                                                        )
                                                    )
                                                )
                                            )
                                        } else {
                                            println("Skipping unloaded audio clip: $clipName")
                                            null
                                        }
                                    }
                                )
                            )
                        )
                    )
                )
            } else {
                // Single clip for coordinate
                val clipName = matchingEntries[0].split(" ")[3].trim()
                val audioClip = clipMap[clipName]

                if (audioClip?.isLoaded == true) {
                    groups.add(
                        Group(
                            name = "Single $x,$y",
                            stateChain = StateChain(
                                devices = listOf(
                                    CoordinateFilterChainDeviceState(
                                        filters = listOf(Pair(x, y))
                                    ),
                                    ClipChainDeviceState(
                                        fileName = audioClip.name,
                                        rawData = audioClip.rawData,
                                        sampleRate = audioClip.sampleRate,
                                        channels = audioClip.channels,
                                        bitDepth = audioClip.bitDepth,
                                        isLoaded = true
                                    )
                                )
                            )
                        )
                    )
                } else {
                    println("Skipping unloaded audio clip: $clipName")
                }
            }
        }

        println("Page ${page + 1} (audio) created with ${groups.size} sound groups.")

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
package dev.anthonyhfm.amethyst.conversion.unipad.data

import dev.anthonyhfm.amethyst.conversion.unipad.UnipadConverter
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.core.util.ConversionTempFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        // Only process entries that live inside the sounds/ directory
        val soundEntries = UnipadConverter.entries.filter { (path, entry) ->
            path.lowercase().startsWith("sounds/") && !entry.isDirectory
        }

        soundEntries.forEach { (path, _) ->
            val clipName = path.substringAfter('/').trim()
            val temporaryPath = ConversionTempFiles.newPath(
                extension = clipName.substringAfterLast('.', "audio"),
            )
            try {
                val audioSignal = if (UnipadConverter.extractEntryToFile(
                        path = path,
                        destinationPath = temporaryPath,
                    )
                ) {
                    Echo.decodeAudioFile(filePath = temporaryPath)
                } else {
                    UnipadConverter.readEntry(path = path)?.let { bytes ->
                        Echo.decodeAudioData(audioData = bytes, fileName = clipName)
                    }
                }
                clipMap[clipName] = if (audioSignal != null) {
                    DecodedAudioClip(
                        name = clipName,
                        rawData = audioSignal.rawData,
                        sampleRate = audioSignal.sampleRate,
                        channels = audioSignal.channels,
                        bitDepth = audioSignal.bitDepth,
                        isLoaded = true,
                    )
                } else {
                    DecodedAudioClip(name = clipName, rawData = null, isLoaded = false)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Error loading audio clip '$clipName': ${exception.message}")
                clipMap[clipName] = DecodedAudioClip(name = clipName, rawData = null, isLoaded = false)
            } finally {
                ConversionTempFiles.remove(path = temporaryPath)
            }
        }

        println("Finished loading ${clipMap.count { it.value.isLoaded }} audio clips successfully (${clipMap.size} total).")

        return@withContext clipMap.toMap()
    }

    fun createChain(page: Int, clipMap: Map<String, DecodedAudioClip>, entries: List<String>): StateChain {
        val groups = mutableListOf<Group>()

        // Normalise and drop malformed lines (need at least chain x y soundFile)
        val validEntries = entries.map { it.trim() }.filter { line ->
            val fields = line.split(" ")
            fields.size >= 4 && fields[1].toIntOrNull() != null && fields[2].toIntOrNull() != null
        }

        // Docs: chain x y soundFile [loop] [wormhole]  — field[1]=x, field[2]=y
        validEntries.map {
            val f = it.split(" ")
            val rx = f[2].toInt()
            val ry = f[1].toInt()
            "${f[0]} $rx $ry"
        }.distinct().forEach { entry ->
            val f = entry.split(" ")
            val x = f[1].toInt()
            val y = f[2].toInt()

            val matchingEntries = validEntries.filter { line ->
                val lf = line.split(" ")
                val lx = lf[2].toInt()
                val ly = lf[1].toInt()
                lx == x && ly == y
            }

            if (matchingEntries.size > 1) {
                groups.add(
                    Group(
                        name = "Multi $x,$y",
                        stateChain = StateChain(
                            devices = listOf(
                                CoordinateFilterChainDeviceState(
                                    filters = listOf(Pair(x, y))
                                ),
                                MultiGroupChainDeviceState(
                                    groups = matchingEntries.mapNotNull { line ->
                                        val fields = line.split(" ")
                                        val clipName = fields[3].trim()
                                        val wormhole = fields.getOrNull(5)?.toIntOrNull()
                                        val audioClip = clipMap[clipName]

                                        val devices = mutableListOf<DeviceState>()
                                        if (audioClip?.isLoaded == true) {
                                            devices.add(
                                                SampleChainDeviceState(
                                                    fileName = audioClip.name,
                                                    rawData = audioClip.rawData,
                                                    sampleRate = audioClip.sampleRate,
                                                    channels = audioClip.channels,
                                                    bitDepth = audioClip.bitDepth,
                                                    isLoaded = true
                                                )
                                            )
                                        }
                                        if (wormhole != null) {
                                            devices.add(
                                                MacroControlChainDeviceState(
                                                    macro = 0,
                                                    value = wormhole - 1
                                                )
                                            )
                                        }

                                        if (devices.isNotEmpty()) {
                                            Group(
                                                name = clipName,
                                                stateChain = StateChain(
                                                    devices = devices
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
                val fields = matchingEntries[0].split(" ")
                val clipName = fields[3].trim()
                // fields[4] = loop (optional): omitted/1 = once, 0 = infinite, N = repeat N-1 times
                val loopRaw = fields.getOrNull(4)?.toIntOrNull()
                // fields[5] = wormhole chain (optional)
                val wormhole = fields.getOrNull(5)?.toIntOrNull()

                val audioClip = clipMap[clipName]

                if (loopRaw == 0) {
                    println("KeySound: loop=0 (infinite) at ($x,$y) – looping not yet supported by engine")
                } else if (loopRaw != null && loopRaw > 1) {
                    println("KeySound: loop=$loopRaw (repeat ${loopRaw - 1}x) at ($x,$y) – repeat count not yet supported by engine")
                }

                val devices = mutableListOf<DeviceState>()
                devices.add(
                    CoordinateFilterChainDeviceState(
                        filters = listOf(Pair(x, y))
                    )
                )

                if (audioClip?.isLoaded == true) {
                    devices.add(
                        SampleChainDeviceState(
                            fileName = audioClip.name,
                            rawData = audioClip.rawData,
                            sampleRate = audioClip.sampleRate,
                            channels = audioClip.channels,
                            bitDepth = audioClip.bitDepth,
                            isLoaded = true
                        )
                    )
                } else {
                    println("Skipping unloaded audio clip: $clipName")
                }

                if (wormhole != null) {
                    devices.add(
                        MacroControlChainDeviceState(
                            macro = 0,
                            value = wormhole - 1
                        )
                    )
                }

                if (devices.size > 1) {
                    groups.add(
                        Group(
                            name = "Single $x,$y",
                            stateChain = StateChain(
                                devices = devices
                            )
                        )
                    )
                }
            }
        }

        println("Page ${page + 1} (audio) created with ${groups.size} sound groups.")

        return StateChain(
            devices = listOf(
                MacroFilterChainDeviceState(
                    allowedValues = setOf(page)
                ),
                GroupChainDeviceState(
                    groups = groups
                )
            )
        )
    }
}

package dev.anthonyhfm.amethyst.conversion.ableton.adapters.dovitate

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceFileDropList
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceInstrument
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class SupersonicAdapter (
    val device: MxDeviceInstrument,
    val offset: IntOffset,
) : AbletonAdapter() {
    private companion object {
        const val MAX_PARALLEL_AUDIO_DECODES = 16
    }

    override fun toDeviceStates(): List<DeviceState> {
        val idToIndex = mapOf(
            1 to 48,  2 to 47,  3 to 46,  4 to 45,  5 to 38,  6 to 37,  7 to 36,  8 to 35,
            9 to 28,  10 to 27, 11 to 26, 12 to 25, 13 to 18, 14 to 17, 15 to 16, 16 to 15,
            17 to 88, 18 to 87, 19 to 86, 20 to 85, 21 to 78, 22 to 77, 23 to 76, 24 to 75,
            25 to 68, 26 to 67, 27 to 66, 28 to 65, 29 to 58, 30 to 57, 31 to 56, 32 to 55,
            33 to 10, 34 to 20, 35 to 30, 36 to 40, 37 to 50, 38 to 70, 39 to 19, 40 to 39,
            41 to 59, 42 to 52, 43 to 64, 44 to 73, 45 to 83, 46 to 33, 47 to 13, 48 to 60,
            49 to 80, 50 to 29, 51 to 49, 52 to 79, 53 to 89, 62 to 51, 64 to 63, 65 to 61,
            66 to 72, 67 to 82, 68 to 44, 69 to 32, 70 to 12, 71 to 69, 76 to 98, 77 to 7,
            78 to 4,  79 to 43, 80 to 31, 81 to 22, 82 to 11, 85 to 42, 86 to 24, 87 to 21,
            88 to 41, 89 to 53, 90 to 54, 91 to 62, 92 to 74, 93 to 71, 94 to 84, 95 to 81,
            96 to 34, 97 to 23, 98 to 14, 99 to 6,  100 to 97, 101 to 96, 102 to 95, 103 to 94,
            104 to 93, 105 to 92, 106 to 91, 107 to 8, 108 to 5, 109 to 3, 110 to 2, 111 to 1,
        )

        val drops = device.fileDropList.fileDropList.items
            .filter {
                it.ref.fileRef.path?.value != null
            }

        val audioByPath = decodeAudioFileDrops(drops)

        return listOf(
            GroupChainDeviceState(
                groups = drops
                    .map { drop ->
                        val id = Regex("\\d+").find(drop.name?.value ?: "0")?.value?.toIntOrNull()
                        val index = idToIndex[id] ?: 0

                        Group(
                            name = "Supersonic Entry",
                            stateChain = StateChain(
                                devices = listOfNotNull(
                                    CoordinateFilterChainDeviceState(
                                        filters = listOf(
                                            Pair(
                                                first = index % 10,
                                                second = 9 - index / 10
                                            )
                                        )
                                    ),
                                    audioByPath[drop.ref.fileRef.resolvePath()]
                                )
                            )
                        )
                    }
            ),
        )
    }

    private fun decodeAudioFileDrops(
        drops: List<MxDeviceFileDropList.FileDropList.MxDFullFileDrop>
    ): Map<String, SampleChainDeviceState?> = runBlocking {
        val paths = drops.map { it.ref.fileRef.resolvePath() }.distinct()
        val limitedIO = Dispatchers.Default.limitedParallelism(MAX_PARALLEL_AUDIO_DECODES)
        val gate = Semaphore(MAX_PARALLEL_AUDIO_DECODES)

        coroutineScope {
            paths.map { path ->
                async(limitedIO) {
                    gate.withPermit {
                        path to decodeAudioFile(path)
                    }
                }
            }.awaitAll().toMap()
        }
    }

    private suspend fun readAudioFileBytes(filePath: String): ByteArray? {
        return if (AbletonConverter.isZip) {
            val fileBytes = AbletonConverter.zipEntries[filePath]?.data
            if (fileBytes == null) {
                println("SupersonicAdapter: file not found in zip: $filePath")
                null
            } else {
                fileBytes
            }
        } else {
            val audioFile = PlatformFile(filePath)
            if (!audioFile.exists() || !audioFile.isRegularFile()) {
                println("SupersonicAdapter: file not found: $filePath")
                null
            } else {
                audioFile.readBytes()
            }
        }
    }

    private suspend fun decodeAudioFile(filePath: String): SampleChainDeviceState? {
        val audioFileBytes = readAudioFileBytes(filePath) ?: return null

        val audioSignal = Echo.decodeAudioData(
            audioData = audioFileBytes,
            fileName = filePath,
            sampleStart = null,
            sampleEnd = null
        ) ?: return null

        if (AbletonConverter.isZip) {
            AbletonConverter.zipEntries.remove(filePath)
        }

        return SampleChainDeviceState(
            fileName = filePath,
            rawData = audioSignal.rawData,
            sampleRate = audioSignal.sampleRate,
            channels = audioSignal.channels,
            bitDepth = audioSignal.bitDepth,
            isLoaded = true
        )
    }
}

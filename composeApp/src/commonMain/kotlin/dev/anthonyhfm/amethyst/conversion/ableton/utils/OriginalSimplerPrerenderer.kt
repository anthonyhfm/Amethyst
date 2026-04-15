package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiChainReader
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class OriginalSimplerPrerenderer {
    private data class FullAudio(
        val rawData: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val totalFrames: Long
    )

    fun decodeAll(tracksList: List<MidiTrack>): Map<OriginalSimplerAdapter.OriginalSimplerData, SampleChainDeviceState> {
        val simplers = tracksList
            .flatMap { MidiChainReader.getAllDevicesOfType<OriginalSimpler>(it) }
            .map { OriginalSimplerAdapter.getSimplerData(it) }

        if (simplers.isEmpty()) return emptyMap()

        val limitedIO = Dispatchers.Default.limitedParallelism(8)
        val gate = Semaphore(8)

        return runBlocking {
            val groupedByPath = simplers.groupBy { it.filePath }
            println("OriginalSimplerPrerenderer: Decoding ${groupedByPath.size} Files")

            coroutineScope {
                val perPathJobs = groupedByPath.map { (path, pathSimplers) ->
                    async(limitedIO) {
                        gate.withPermit {
                            val full = decodeFull(path) ?: return@async path to emptyMap<OriginalSimplerAdapter.OriginalSimplerData, SampleChainDeviceState>()

                            try {
                                val states = pathSimplers.associateWith { simpler ->
                                    sliceSegment(
                                        filePath = path,
                                        full = full,
                                        sampleStart = simpler.sampleStart,
                                        sampleEnd = simpler.sampleEnd
                                    )
                                }
                                path to states
                            } finally { }
                        }
                    }
                }

                perPathJobs
                    .awaitAll()
                    .flatMap { it.second.entries }
                    .associate { it.toPair() }
            }
        }
    }

    private suspend fun decodeFull(filePath: String): FullAudio? = withContext(Dispatchers.IO) {
        val audioFileBytes: ByteArray = if (AbletonConverter.isZip) {
            val fileBytes = AbletonConverter.zipEntries[filePath]?.data
            if (fileBytes == null) {
                println("OriginalSimplerPrerenderer: file not found in zip: $filePath")
                return@withContext null
            }
            fileBytes
        } else {
            val audioFile = PlatformFile(filePath)
            if (!audioFile.exists() || !audioFile.isRegularFile()) {
                println("OriginalSimplerPrerenderer: file not found: $filePath")
                return@withContext null
            }
            audioFile.readBytes()
        }

        val audioSignal = AudioDecoder.decodeAudioData(
            audioData = audioFileBytes,
            fileName = filePath,
            sampleStart = null,
            sampleEnd = null
        )

        if (AbletonConverter.isZip) {
            // zipEntries kann viel Speicher halten – frühzeitig freigeben
            AbletonConverter.zipEntries.remove(filePath)
        }

        if (audioSignal == null) {
            println("OriginalSimplerPrerenderer: error while decoding $filePath")
            return@withContext null
        }

        val frameSizeBytes = (audioSignal.channels * (audioSignal.bitDepth / 8))
        val totalFrames = if (frameSizeBytes > 0) (audioSignal.rawData?.size ?: 0) / frameSizeBytes else 0

        FullAudio(
            rawData = audioSignal.rawData ?: ByteArray(0),
            sampleRate = audioSignal.sampleRate,
            channels = audioSignal.channels,
            bitDepth = audioSignal.bitDepth,
            totalFrames = totalFrames.toLong()
        )
    }

    private fun sliceSegment(
        filePath: String,
        full: FullAudio,
        sampleStart: Long,
        sampleEnd: Long
    ): SampleChainDeviceState {
        val frameSize = full.channels * (full.bitDepth / 8)
        if (frameSize <= 0) return SampleChainDeviceState(fileName = filePath, isLoaded = false)

        val startF = sampleStart.coerceAtLeast(0L)
        val endRaw = if (sampleEnd <= 0L) full.totalFrames else sampleEnd
        val endF = endRaw.coerceAtMost(full.totalFrames)

        if (startF >= endF) {
            return SampleChainDeviceState(
                fileName = filePath,
                rawData = ByteArray(0),
                sampleRate = full.sampleRate,
                channels = full.channels,
                bitDepth = full.bitDepth,
                isLoaded = false
            )
        }

        val startByte = (startF * frameSize).toInt()
        val endByte = (endF * frameSize).toInt()
        if (startByte >= full.rawData.size) {
            return SampleChainDeviceState(fileName = filePath, isLoaded = false)
        }
        val safeEndByte = endByte.coerceAtMost(full.rawData.size)
        val slice = full.rawData.copyOfRange(startByte, safeEndByte)


        return SampleChainDeviceState(
            fileName = filePath,
            rawData = slice,
            sampleRate = full.sampleRate,
            channels = full.channels,
            bitDepth = full.bitDepth,
            isLoaded = true
        )
    }
}
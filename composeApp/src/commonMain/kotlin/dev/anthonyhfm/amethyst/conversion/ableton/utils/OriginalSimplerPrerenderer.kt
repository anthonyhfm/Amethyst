package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiChainReader
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.readBytes
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class OriginalSimplerPrerenderer {
    private companion object {
        const val MAX_PARALLEL_AUDIO_DECODES = 16
    }

    private data class FullAudio(
        val rawData: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val totalFrames: Long
    )

    fun decodeAll(
        tracksList: List<MidiTrack>,
        reporter: dev.anthonyhfm.amethyst.core.loading.ProgressReporter? = null,
    ): Map<OriginalSimplerAdapter.OriginalSimplerData, SampleChainDeviceState> {
        val simplers = tracksList
            .flatMap { MidiChainReader.getAllDevicesOfType<OriginalSimpler>(it) }
            .map { OriginalSimplerAdapter.getSimplerData(it) }

        if (simplers.isEmpty()) {
            val noSamplesMsg = runCatching { runBlocking { getString(Res.string.home_loading_no_samples_to_render) } }.getOrDefault("No audio samples to render")
            reporter?.update(1.0f, noSamplesMsg, detailText = null)
            return emptyMap()
        }

        val limitedIO = Dispatchers.Default.limitedParallelism(MAX_PARALLEL_AUDIO_DECODES)
        val gate = Semaphore(MAX_PARALLEL_AUDIO_DECODES)

        return runBlocking {
            val groupedByPath = simplers.groupBy { it.filePath }
            val total = groupedByPath.size
            var completedCount = 0
            val countMutex = Mutex()

            val startingMsg = runCatching { getString(Res.string.home_loading_starting_audio_rendering, total.toString()) }.getOrDefault("Starting audio rendering ($total samples)...")
            reporter?.update(0f, startingMsg, detailText = null)

            coroutineScope {
                val perPathJobs = groupedByPath.map { (path, pathSimplers) ->
                    async(limitedIO) {
                        gate.withPermit {
                            val result = if (pathSimplers.size == 1) {
                                val simpler = pathSimplers.first()
                                val state = decodeSegment(
                                    filePath = path,
                                    sampleStart = simpler.sampleStart,
                                    sampleEnd = simpler.sampleEnd
                                ) ?: return@async path to emptyMap<OriginalSimplerAdapter.OriginalSimplerData, SampleChainDeviceState>()

                                path to mapOf(simpler to state)
                            } else {
                                val full = decodeFull(path) ?: return@async path to emptyMap<OriginalSimplerAdapter.OriginalSimplerData, SampleChainDeviceState>()

                                val states = pathSimplers.associateWith { simpler ->
                                    sliceSegment(
                                        filePath = path,
                                        full = full,
                                        sampleStart = simpler.sampleStart,
                                        sampleEnd = simpler.sampleEnd
                                    )
                                }
                                path to states
                            }

                            val count = countMutex.withLock {
                                completedCount++
                                completedCount
                            }

                            // Throttle progress updates (e.g. max ~50 UI updates total across decoding) to avoid flooding UI recompositions
                            val updateStep = (total / 50).coerceAtLeast(1)
                            if (count == total || count == 1 || count % updateStep == 0) {
                                val fileName = path.substringAfterLast("/").substringAfterLast("\\")
                                val statusTextMsg = runCatching {
                                    getString(Res.string.home_loading_rendering_sample, count.toString(), total.toString())
                                }.getOrDefault("Rendering audio sample $count of $total")

                                reporter?.update(
                                    progress = count.toFloat() / total,
                                    statusText = statusTextMsg,
                                    detailText = fileName
                                )
                            }

                            result
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

    private suspend fun decodeSegment(
        filePath: String,
        sampleStart: Long,
        sampleEnd: Long
    ): SampleChainDeviceState? = withContext(Dispatchers.IO) {
        val audioFileBytes = readAudioFileBytes(filePath) ?: return@withContext null

        val audioSignal = Echo.decodeAudioData(
            audioData = audioFileBytes,
            fileName = filePath,
            sampleStart = sampleStart.takeIf { it > 0 },
            sampleEnd = sampleEnd.takeIf { it > 0 }
        )

        if (AbletonConverter.isZip) {
            AbletonConverter.zipEntries.remove(filePath)
        }

        if (audioSignal == null) {
            println("OriginalSimplerPrerenderer: error while decoding $filePath")
            return@withContext null
        }

        SampleChainDeviceState(
            fileName = filePath,
            rawData = audioSignal.rawData,
            sampleRate = audioSignal.sampleRate,
            channels = audioSignal.channels,
            bitDepth = audioSignal.bitDepth,
            isLoaded = true
        )
    }

    private suspend fun decodeFull(filePath: String): FullAudio? = withContext(Dispatchers.IO) {
        val audioFileBytes = readAudioFileBytes(filePath) ?: return@withContext null

        val audioSignal = Echo.decodeAudioData(
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

    private suspend fun readAudioFileBytes(filePath: String): ByteArray? {
        return if (AbletonConverter.isZip) {
            val fileBytes = AbletonConverter.zipEntries[filePath]?.data
            if (fileBytes == null) {
                println("OriginalSimplerPrerenderer: file not found in zip: $filePath")
                null
            } else {
                fileBytes
            }
        } else {
            val audioFile = PlatformFile(filePath)
            if (!audioFile.exists() || !audioFile.isRegularFile()) {
                println("OriginalSimplerPrerenderer: file not found: $filePath")
                null
            } else {
                audioFile.readBytes()
            }
        }
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

package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiChainReader
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
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

    data class Result(
        val states: Map<OriginalSimplerAdapter.OriginalSimplerData, SampleChainDeviceState>,
        val sources: List<AudioSource>,
    )

    fun decodeAll(
        tracksList: List<MidiTrack>,
        reporter: dev.anthonyhfm.amethyst.core.loading.ProgressReporter? = null,
    ): Result {
        val simplers = tracksList
            .flatMap { MidiChainReader.getAllDevicesOfType<OriginalSimpler>(it) }
            .map { OriginalSimplerAdapter.getSimplerData(it) }

        if (simplers.isEmpty()) {
            val noSamplesMsg = runCatching { runBlocking { getString(Res.string.home_loading_no_samples_to_render) } }.getOrDefault("No audio samples to render")
            reporter?.update(1.0f, noSamplesMsg, detailText = null)
            return Result(emptyMap(), emptyList())
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
                            val full = decodeFull(path)
                                ?: return@async Triple(path, null, emptyMap<OriginalSimplerAdapter.OriginalSimplerData, SampleChainDeviceState>())
                            val source = AudioSource(
                                id = UUID.randomUUID(),
                                fileName = path.substringAfterLast('/').substringAfterLast('\\'),
                                rawData = full.rawData,
                                sampleRate = full.sampleRate,
                                channels = full.channels,
                                bitDepth = full.bitDepth,
                            )
                            val states = pathSimplers.associateWith { simpler ->
                                referenceRegion(
                                    filePath = path,
                                    source = source,
                                    sampleStart = simpler.sampleStart,
                                    sampleEnd = simpler.sampleEnd,
                                )
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

                            Triple(path, source, states)
                        }
                    }
                }

                val decoded = perPathJobs.awaitAll()
                Result(
                    states = decoded.flatMap { it.third.entries }.associate { it.toPair() },
                    sources = decoded.mapNotNull { it.second },
                )
            }
        }
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

    private fun referenceRegion(
        filePath: String,
        source: AudioSource,
        sampleStart: Long,
        sampleEnd: Long
    ): SampleChainDeviceState {
        val startF = sampleStart.coerceAtLeast(0L)
            .coerceAtMost(source.totalSamples)
        val endRaw = if (sampleEnd <= 0L) source.totalSamples else sampleEnd
        val endF = endRaw.coerceIn(startF, source.totalSamples)

        if (startF >= endF) {
            return SampleChainDeviceState(
                fileName = filePath,
                sampleRate = source.sampleRate,
                channels = source.channels,
                bitDepth = source.bitDepth,
                totalDurationMs = source.totalDurationMs,
                sourceId = source.id,
                sourceStartFrame = startF,
                sourceEndFrameExclusive = endF,
                isLoaded = false,
            )
        }

        return SampleChainDeviceState(
            fileName = filePath,
            rawData = null,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
            totalDurationMs = source.totalDurationMs,
            isLoaded = true,
            sourceId = source.id,
            startPosition = startF.toDouble().div(source.totalSamples).toFloat(),
            endPosition = endF.toDouble().div(source.totalSamples).toFloat(),
            sourceStartFrame = startF,
            sourceEndFrameExclusive = endF,
        )
    }
}

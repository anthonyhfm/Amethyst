package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class OriginalSimplerPrerenderer {
    fun decodeAll(tracksList: List<XmlElement>): Map<OriginalSimplerAdapter.OriginalSimplerData, ClipChainDeviceState> {
        val simplers = tracksList
            .flatMap { it.querySelector("OriginalSimpler") }
            .mapNotNull {
                OriginalSimplerAdapter.getSimplerData(it)
            }

        val limitedIO = Dispatchers.Default.limitedParallelism(8)
        val gate = Semaphore(8)

        return runBlocking {
            val results = simplers.map { s ->
                async(limitedIO) {
                    gate.withPermit {
                        s to decodeAudio(s)
                    }
                }
            }.awaitAll()
            results.toMap()
        }
    }

    private suspend fun decodeAudio(data: OriginalSimplerAdapter.OriginalSimplerData): ClipChainDeviceState =
        withContext(Dispatchers.IO) {
            val audioFileBytes = if (AbletonConverter.isZip) {
                val fileBytes = AbletonConverter.zipEntries[data.filePath]?.data

                if (fileBytes == null) {
                    println("Error with decoding OriginalSimpler $data")
                    return@withContext ClipChainDeviceState()
                }

                fileBytes
            } else {
                val audioFile = PlatformFile(data.filePath)

                if (!audioFile.exists() || !audioFile.isRegularFile()) {
                    println("Error with decoding OriginalSimpler $data")
                    return@withContext ClipChainDeviceState()
                }

                audioFile.readBytes()
            }

            val audioSignal = AudioDecoder.decodeAudioData(
                audioData = audioFileBytes, // siehe Fix 3 unten
                fileName = data.filePath,
                sampleStart = data.sampleStart.takeIf { it > 0 },
                sampleEnd = data.sampleEnd.takeIf { it > 0 }
            )

            audioSignal?.let {
                ClipChainDeviceState(
                    fileName = data.filePath,
                    rawData = it.rawData,
                    sampleRate = it.sampleRate,
                    channels = it.channels,
                    bitDepth = it.bitDepth,
                    isLoaded = true
                )
            } ?: ClipChainDeviceState()
        }
}
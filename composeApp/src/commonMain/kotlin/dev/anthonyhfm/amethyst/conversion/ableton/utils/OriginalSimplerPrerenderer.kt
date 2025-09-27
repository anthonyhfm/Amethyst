package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.OriginalSimplerAdapter
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// ...

class OriginalSimplerPrerenderer {
    fun decodeAll(tracksList: List<XmlElement>): Map<OriginalSimplerAdapter.OriginalSimplerData, ClipChainDeviceState> {
        val simplers = tracksList
            .flatMap { it.querySelector("OriginalSimpler") }
            .map { OriginalSimplerAdapter.getSimplerData(it) }

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
            val audioFile = PlatformFile(data.filePath)
            val audioSignal = AudioDecoder.decodeAudioData(
                audioData = audioFile.readBytes(), // siehe Fix 3 unten
                fileName = audioFile.name,
                sampleStart = data.sampleStart.takeIf { it > 0 },
                sampleEnd = data.sampleEnd.takeIf { it > 0 }
            )

            audioSignal?.let {
                ClipChainDeviceState(
                    fileName = audioFile.name,
                    rawData = it.rawData,
                    sampleRate = it.sampleRate,
                    channels = it.channels,
                    bitDepth = it.bitDepth,
                    isLoaded = true
                )
            } ?: ClipChainDeviceState()
        }
}
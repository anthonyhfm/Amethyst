package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

class OriginalSimplerAdapter(
    private val xml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val player = xml.localQuerySelector("Player")[0]
        val samplePart = player.querySelector("MultiSamplePart").getOrNull(0) ?: return emptyList()
        val sampleRef = samplePart.localQuerySelector("SampleRef")[0]

        val filePath: String = FileRef.resolveFileReference(sampleRef.querySelector("FileRef")[0])

        val sampleStart = samplePart.querySelector("SampleStart")[0].attributes["Value"]?.toLong() ?: 0L
        val sampleEnd = samplePart.querySelector("SampleEnd")[0].attributes["Value"]?.toLong() ?: 0L

        val audioFile = PlatformFile(filePath)

        return runBlocking {
            try {
                val audioSignal = AudioDecoder.decodeAudioData(
                    audioData = audioFile.readBytes(),
                    fileName = audioFile.name,
                    sampleStart = if (sampleStart > 0) sampleStart else null,
                    sampleEnd = if (sampleEnd > 0) sampleEnd else null
                )

                if (audioSignal != null) {
                    listOf(
                        ClipChainDeviceState(
                            fileName = audioFile.name,
                            rawData = audioSignal.rawData,
                            sampleRate = audioSignal.sampleRate,
                            channels = audioSignal.channels,
                            bitDepth = audioSignal.bitDepth,
                            isLoaded = true
                        )
                    )
                } else {
                    println("Failed to decode audio file: ${audioFile.name}")
                    listOf(
                        ClipChainDeviceState(
                            fileName = "Failed to load: ${audioFile.name}",
                            isLoaded = false
                        )
                    )
                }
            } catch (e: Exception) {
                println("Error loading audio file '${audioFile.name}': ${e.message}")
                listOf(
                    ClipChainDeviceState(
                        fileName = "Error: ${audioFile.name}",
                        isLoaded = false
                    )
                )
            }
        }
    }
}
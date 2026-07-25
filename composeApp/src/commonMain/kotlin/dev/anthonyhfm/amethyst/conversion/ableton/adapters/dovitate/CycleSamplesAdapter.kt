package dev.anthonyhfm.amethyst.conversion.ableton.adapters.dovitate

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceFileDropList
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceInstrument
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MaxParam
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState.TYPE
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CycleSamplesAdapter(
    val device: MxDeviceInstrument,
    val offset: IntOffset = IntOffset.Zero,
) : AbletonAdapter() {
    private companion object {
        const val MAX_PARALLEL_AUDIO_DECODES = 16
        const val MAX_DROPS = 16
    }

    override fun toDeviceStates(): List<DeviceState> {
        val blob = runCatching { device.decodeBlob() }.getOrNull()
        val blobData = blob?.let {
            runCatching { jsonDecoder.decodeFromString<CycleSamplesBlob>(it) }.getOrNull()
        }

        val parameters = MaxParam(device.parameterList.parameterList.parameters)

        val fadeInMs = blobData?.fadeIn?.firstOrNull()
            ?: runCatching { parameters.getFloatValue(2) }.getOrDefault(0f)
        val fadeOutMs = blobData?.fadeOut?.firstOrNull()
            ?: runCatching { parameters.getFloatValue(3) }.getOrDefault(0f)

        val drops = device.fileDropList.fileDropList.items
            .filter { it.ref.fileRef.path?.value != null }
            .take(MAX_DROPS)
            .reversed()

        val audioByPath = decodeAudioFileDrops(drops, fadeInMs, fadeOutMs)

        val groups = drops.mapIndexed { index, drop ->
            val path = drop.ref.fileRef.resolvePath()
            val sampleState = audioByPath[path]

            Group(
                name = drop.name?.value?.ifBlank { null } ?: "Cycle Sample #${index + 1}",
                stateChain = StateChain(
                    devices = listOfNotNull(sampleState)
                )
            )
        }

        return listOf(
            MultiGroupChainDeviceState(
                type = TYPE.FORWARD,
                groups = groups
            )
        )
    }

    private fun decodeAudioFileDrops(
        drops: List<MxDeviceFileDropList.FileDropList.MxDFullFileDrop>,
        fadeInMs: Float,
        fadeOutMs: Float,
    ): Map<String, SampleChainDeviceState?> = runBlocking {
        val paths = drops.map { it.ref.fileRef.resolvePath() }.distinct()
        val limitedIO = Dispatchers.Default.limitedParallelism(MAX_PARALLEL_AUDIO_DECODES)
        val gate = Semaphore(MAX_PARALLEL_AUDIO_DECODES)

        coroutineScope {
            paths.map { path ->
                async(limitedIO) {
                    gate.withPermit {
                        path to decodeAudioFile(path, fadeInMs, fadeOutMs)
                    }
                }
            }.awaitAll().toMap()
        }
    }

    private suspend fun readAudioFileBytes(filePath: String): ByteArray? {
        return if (AbletonConverter.isZip) {
            val fileBytes = AbletonConverter.zipEntries[filePath]?.data
            if (fileBytes == null) {
                println("CycleSamplesAdapter: file not found in zip: $filePath")
                null
            } else {
                fileBytes
            }
        } else {
            val audioFile = PlatformFile(filePath)
            if (!audioFile.exists() || !audioFile.isRegularFile()) {
                println("CycleSamplesAdapter: file not found: $filePath")
                null
            } else {
                audioFile.readBytes()
            }
        }
    }

    private suspend fun decodeAudioFile(
        filePath: String,
        fadeInMs: Float,
        fadeOutMs: Float,
    ): SampleChainDeviceState? {
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
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
            isLoaded = true
        )
    }

    @Serializable
    private data class CycleSamplesBlob(
        @SerialName("live.dial")
        val fadeIn: List<Float> = emptyList(),

        @SerialName("live.dial[1]")
        val fadeOut: List<Float> = emptyList(),

        @SerialName("live.numbox[1]")
        val cycleLength: List<Float> = emptyList(),
    )
}

package dev.anthonyhfm.amethyst.core.engine.audio.source

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_EndOfStream
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterOutputStatus_EndOfStream
import platform.AVFAudio.AVAudioConverterOutputStatus_Error
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioQualityHigh

/** Core Audio's optimized sample-rate converter avoids billions of Kotlin sample reads on iOS. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformResampleToPcm24(
    source: PcmAudioSource,
    outputRate: Int,
): PcmAudioSource? {
    if (source.sampleRate == outputRate) {
        return source
    }
    val frames = (source.frameCount.toDouble() * outputRate / source.sampleRate)
        .toLong().coerceAtLeast(1L)
    if (frames > Int.MAX_VALUE / (source.channels * 3)) {
        return null
    }

    val inputFormat = AVAudioFormat(
        AVAudioPCMFormatFloat32,
        source.sampleRate.toDouble(),
        source.channels.toUInt(),
        false,
    )
    val outputFormat = AVAudioFormat(
        AVAudioPCMFormatFloat32,
        outputRate.toDouble(),
        source.channels.toUInt(),
        false,
    )
    val converter = AVAudioConverter(inputFormat, outputFormat) ?: return null
    converter.sampleRateConverterQuality = AVAudioQualityHigh
    val input = AVAudioPCMBuffer(inputFormat, INPUT_FRAMES.toUInt())
    val output = AVAudioPCMBuffer(outputFormat, OUTPUT_FRAMES.toUInt())
    val inputChannels = input.floatChannelData ?: return null
    val outputChannels = output.floatChannelData ?: return null
    val storage = runCatching {
        ProjectPcmFiles.prepare(expectedBytes = frames.toInt() * source.channels * 3) { writer ->
            converter.reset()
            val destination = ByteArray(OUTPUT_FRAMES * source.channels * 3)
            var inputPosition = 0L
            var outputPosition = 0
            var idleCount = 0

            while (outputPosition < frames.toInt()) {
                output.frameLength = 0u
                val status = converter.convertToBuffer(output, null) { _, outStatus ->
                    if (inputPosition >= source.frameCount) {
                        outStatus?.pointed?.value = AVAudioConverterInputStatus_EndOfStream
                        null
                    } else {
                        val count = minOf(INPUT_FRAMES.toLong(), source.frameCount - inputPosition).toInt()
                        input.frameLength = count.toUInt()
                        var channel = 0
                        while (channel < source.channels) {
                            val samples = inputChannels[channel] ?: return@convertToBuffer null
                            var frame = 0
                            while (frame < count) {
                                samples[frame] = source.sample(inputPosition + frame, channel)
                                frame++
                            }
                            channel++
                        }
                        inputPosition += count
                        outStatus?.pointed?.value = AVAudioConverterInputStatus_HaveData
                        input
                    }
                }
                check(status != AVAudioConverterOutputStatus_Error)
                val produced = output.frameLength.toInt().coerceAtMost(frames.toInt() - outputPosition)
                if (produced > 0) {
                    var frame = 0
                    while (frame < produced) {
                        var channel = 0
                        while (channel < source.channels) {
                            val samples = requireNotNull(outputChannels[channel])
                            writePcm24(destination, frame * source.channels + channel, samples[frame])
                            channel++
                        }
                        frame++
                    }
                    writer.write(bytes = destination, offset = 0, length = produced * source.channels * 3)
                    outputPosition += produced
                    idleCount = 0
                } else {
                    idleCount++
                    if (status == AVAudioConverterOutputStatus_EndOfStream || idleCount > 2) {
                        break
                    }
                }
            }

            check(outputPosition >= frames.toInt() - 1024)
            destination.fill(element = 0)
            while (outputPosition < frames.toInt()) {
                val count = minOf(OUTPUT_FRAMES, frames.toInt() - outputPosition)
                writer.write(bytes = destination, offset = 0, length = count * source.channels * 3)
                outputPosition += count
            }
        }
    }.getOrNull() ?: return null
    return pcmSourceFromStorage(
        id = source.id,
        sampleRate = outputRate,
        channels = source.channels,
        bitDepth = 24,
        storage = storage,
    )
}

private fun writePcm24(destination: ByteArray, sampleIndex: Int, sample: Float) {
    val normalized = sample.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
    val value = if (normalized <= -1f) {
        -8_388_608
    } else {
        (normalized * 8_388_607f).toInt()
    }
    val offset = sampleIndex * 3
    destination[offset] = value.toByte()
    destination[offset + 1] = (value ushr 8).toByte()
    destination[offset + 2] = (value ushr 16).toByte()
}

private const val INPUT_FRAMES = 8192
private const val OUTPUT_FRAMES = 16384

internal actual fun useNativeRateForLongSample(sourceFrames: Long, sourceRate: Int): Boolean =
    false

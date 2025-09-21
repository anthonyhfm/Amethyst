package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.*
import platform.AVFAudio.*
import platform.Foundation.*
import kotlinx.cinterop.*

actual object AudioOutput {
    private var audioEngine: AVAudioEngine? = null
    private var isInitialized = false
    private val activeSources = mutableMapOf<String, AVAudioPlayerNode>()

    init {
        initializeAVAudioEngine()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun initializeAVAudioEngine() {
        try {
            audioEngine = AVAudioEngine()

            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(AVAudioSessionCategoryPlayback)
            audioSession.setActive(true)

            audioEngine?.startAndReturnError(null)?.let { error ->
                return
            }

            isInitialized = true

        } catch (e: Exception) {
            // Silent error handling
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!isInitialized) return null

        val rawData = audioSignal.rawData
        if (rawData == null || rawData.isEmpty()) return null

        return try {
            val sourceId = "audio_${NSDate().timeIntervalSince1970}_${(0..999).random()}"

            val audioFormat = AVAudioFormat(
                commonFormat = AVAudioCommonFormatPCMFormatInt16,
                sampleRate = audioSignal.sampleRate.toDouble(),
                channels = audioSignal.channels.toUInt(),
                interleaved = true
            )

            val playerNode = AVAudioPlayerNode()
            audioEngine?.attachNode(playerNode)
            audioEngine?.connect(playerNode, audioEngine?.mainMixerNode, audioFormat)

            val frameCapacity = (rawData.size / (audioSignal.channels * (audioSignal.bitDepth / 8))).toUInt()
            val buffer = AVAudioPCMBuffer(pcmFormat = audioFormat, frameCapacity = frameCapacity)

            if (buffer != null) {
                val audioBuffer = buffer.audioBufferList.pointed.mBuffers
                audioBuffer.mData?.let { dataPtr ->
                    rawData.usePinned { pinnedData ->
                        platform.posix.memcpy(dataPtr, pinnedData.addressOf(0), rawData.size.toULong())
                    }
                }
                buffer.frameLength = frameCapacity

                playerNode.scheduleBuffer(buffer, completionHandler = null)
                playerNode.play()

                activeSources[sourceId] = playerNode
                sourceId
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    actual fun stop(sourceId: String) {
        activeSources[sourceId]?.let { playerNode ->
            try {
                playerNode.stop()
                audioEngine?.detachNode(playerNode)
                activeSources.remove(sourceId)
            } catch (e: Exception) {
                // Silent error handling
            }
        }
    }

    actual fun stopAll() {
        try {
            activeSources.values.forEach { playerNode ->
                playerNode.stop()
                audioEngine?.detachNode(playerNode)
            }
            activeSources.clear()
        } catch (e: Exception) {
            // Silent error handling
        }
    }
}

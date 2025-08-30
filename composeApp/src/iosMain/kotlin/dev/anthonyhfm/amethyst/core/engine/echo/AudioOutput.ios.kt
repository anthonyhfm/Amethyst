package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.*
import platform.AVFAudio.*
import platform.Foundation.*
import kotlinx.cinterop.*

actual object AudioOutput {
    private var audioEngine: AVAudioEngine? = null
    private val activeNodes = mutableMapOf<String, AVAudioPlayerNode>()
    private var isInitialized = false
    private var processingJob: Job? = null
    private val audioQueue = mutableListOf<QueuedAudio>()

    private const val MAX_SOURCES = 16

    data class QueuedAudio(
        val pcmData: ByteArray,
        val audioKey: String?,
        val origin: Any?,
        val sampleRate: Int,
        val channels: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QueuedAudio) return false
            return pcmData.contentEquals(other.pcmData) &&
                   audioKey == other.audioKey &&
                   origin == other.origin &&
                   sampleRate == other.sampleRate &&
                   channels == other.channels
        }

        override fun hashCode(): Int {
            var result = pcmData.contentHashCode()
            result = 31 * result + (audioKey?.hashCode() ?: 0)
            result = 31 * result + (origin?.hashCode() ?: 0)
            result = 31 * result + sampleRate
            result = 31 * result + channels
            return result
        }
    }

    init {
        initializeAVAudio()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun initializeAVAudio() {
        try {
            audioEngine = AVAudioEngine()

            // Configure audio session
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(AVAudioSessionCategoryPlayback)
            audioSession.setActive(true)

            // Start audio engine
            audioEngine?.startAndReturnError(null)?.let { error ->
                println("Error starting AVAudioEngine: $error")
                return
            }

            isInitialized = true
            println("iOS AVAudioEngine initialized successfully")
            startAudioProcessing()

        } catch (e: Exception) {
            println("AVAudioEngine initialization failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun startAudioProcessing() {
        processingJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && isInitialized) {
                try {
                    processAudioQueue()
                    delay(10)
                } catch (e: Exception) {
                    println("Audio processing error: ${e.message}")
                }
            }
        }
    }

    private fun processAudioQueue() {
        if (!isInitialized || audioEngine == null) return

        // Simple queue processing
        val itemsToProcess = mutableListOf<QueuedAudio>()

        if (audioQueue.isNotEmpty()) {
            repeat(minOf(4, audioQueue.size)) {
                if (audioQueue.isNotEmpty()) {
                    itemsToProcess.add(audioQueue.removeAt(0))
                }
            }
        }

        itemsToProcess.forEach { queuedAudio ->
            createAndPlayAudioNode(queuedAudio)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun createAndPlayAudioNode(queuedAudio: QueuedAudio) {
        try {
            if (activeNodes.size >= MAX_SOURCES) {
                println("Too many active audio nodes, skipping")
                return
            }

            val engine = audioEngine ?: return
            val playerNode = AVAudioPlayerNode()

            // Create audio format using standard format
            val format = AVAudioFormat(
                standardFormatWithSampleRate = queuedAudio.sampleRate.toDouble(),
                channels = queuedAudio.channels.toUInt()
            )

            // Attach and connect the player node
            engine.attachNode(playerNode)
            engine.connect(playerNode, engine.mainMixerNode, format)

            // Convert PCM data to AVAudioPCMBuffer
            val frameCount = queuedAudio.pcmData.size / (queuedAudio.channels * 2) // 16-bit = 2 bytes
            val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frameCount.toUInt())

            buffer.frameLength = frameCount.toUInt()

            val key = generateNodeKey(queuedAudio.audioKey, queuedAudio.origin)

            // Stop and remove existing node with same key
            activeNodes[key]?.let { existingNode ->
                existingNode.stop()
                engine.detachNode(existingNode)
            }

            activeNodes[key] = playerNode

            // Schedule and play the buffer (simplified without data copying for now)
            playerNode.scheduleBuffer(buffer) {
                // Completion handler - cleanup when done
                CoroutineScope(Dispatchers.Main).launch {
                    activeNodes.remove(key)
                    engine.detachNode(playerNode)
                }
            }

            playerNode.play()

        } catch (e: Exception) {
            println("Error creating iOS audio node: ${e.message}")
        }
    }

    private fun generateNodeKey(audioKey: String?, origin: Any?): String {
        return when {
            audioKey != null -> audioKey
            origin != null -> origin.toString()
            else -> "unknown_${NSDate().timeIntervalSince1970}"
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal) {
        if (!isInitialized) {
            println("iOS AudioOutput not initialized")
            return
        }

        val rawData = audioSignal.rawData
        if (rawData == null || rawData.isEmpty()) {
            println("AudioSignal has no raw data")
            return
        }

        println("Queueing iOS AudioSignal with ${rawData.size} bytes (audioKey: ${audioSignal.audioKey})")

        val queuedAudio = QueuedAudio(
            rawData,
            audioSignal.audioKey,
            audioSignal.origin,
            audioSignal.sampleRate,
            audioSignal.channels
        )

        audioQueue.add(queuedAudio)
    }

    fun stopAudio(audioKey: String) {
        activeNodes[audioKey]?.let { playerNode ->
            playerNode.stop()
            audioEngine?.detachNode(playerNode)
            activeNodes.remove(audioKey)
            println("Stopped iOS audio: $audioKey")
        }
    }

    fun stopAllAudio() {
        activeNodes.values.forEach { playerNode ->
            playerNode.stop()
            audioEngine?.detachNode(playerNode)
        }
        activeNodes.clear()
        audioQueue.clear()
        println("Stopped all iOS audio")
    }

    fun cleanup() {
        try {
            processingJob?.cancel()
            stopAllAudio()

            if (isInitialized) {
                audioEngine?.stop()
                audioEngine = null
                isInitialized = false
                println("iOS AudioOutput cleaned up")
            }
        } catch (e: Exception) {
            println("iOS cleanup error: ${e.message}")
        }
    }
}

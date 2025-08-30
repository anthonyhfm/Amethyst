package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

actual object AudioOutput {
    private val activeTracks = ConcurrentHashMap<String, AudioTrackWrapper>()
    private var isInitialized = false
    private var processingJob: Job? = null
    private val audioQueue = ConcurrentLinkedQueue<QueuedAudio>()
    private val trackIdGenerator = AtomicLong(0)

    private const val SAMPLE_RATE = 44100
    private const val MAX_SOURCES = 16

    data class QueuedAudio(
        val pcmData: ByteArray,
        val audioKey: String?,
        val origin: Any?,
        val sampleRate: Int,
        val channels: Int
    )

    data class AudioTrackWrapper(
        val audioTrack: AudioTrack,
        val audioKey: String?,
        val origin: Any?,
        val trackId: String
    ) {
        fun cleanup() {
            try {
                if (audioTrack.state != AudioTrack.STATE_UNINITIALIZED) {
                    audioTrack.stop()
                    audioTrack.release()
                }
            } catch (e: Exception) {
                println("Error cleaning up AudioTrack: ${e.message}")
            }
        }
    }

    init {
        initializeAndroidAudio()
    }

    private fun initializeAndroidAudio() {
        try {
            isInitialized = true
            println("Android AudioTrack output initialized successfully")
            startAudioProcessing()
        } catch (e: Exception) {
            println("Android audio initialization failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun startAudioProcessing() {
        processingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isInitialized) {
                try {
                    processAudioQueue()
                    cleanupFinishedTracks()
                    delay(1)
                } catch (e: Exception) {
                    println("Audio processing error: ${e.message}")
                }
            }
        }
    }

    private fun processAudioQueue() {
        if (!isInitialized) return

        repeat(4) {
            val queuedAudio = audioQueue.poll() ?: return
            createAndPlayAudioTrack(queuedAudio)
        }
    }

    private fun createAndPlayAudioTrack(queuedAudio: QueuedAudio) {
        try {
            if (activeTracks.size >= MAX_SOURCES) {
                println("Too many active audio tracks, skipping")
                return
            }

            // Calculate buffer size
            val channelConfig = if (queuedAudio.channels == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                queuedAudio.sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val bufferSize = maxOf(minBufferSize, queuedAudio.pcmData.size)

            // Create audio attributes for better audio routing
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            // Create audio format
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(queuedAudio.sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            // Create AudioTrack
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
                println("Failed to initialize AudioTrack")
                audioTrack.release()
                return
            }

            val trackId = "track_${trackIdGenerator.incrementAndGet()}"
            val key = generateTrackKey(queuedAudio.audioKey, queuedAudio.origin, trackId)

            // Stop and remove existing track with same key (excluding trackId part)
            val baseKey = generateTrackKey(queuedAudio.audioKey, queuedAudio.origin, "")
            activeTracks.entries.removeAll { (existingKey, wrapper) ->
                if (existingKey.startsWith(baseKey.dropLast(1))) { // Remove the trailing "_"
                    wrapper.cleanup()
                    true
                } else false
            }

            val wrapper = AudioTrackWrapper(audioTrack, queuedAudio.audioKey, queuedAudio.origin, trackId)
            activeTracks[key] = wrapper

            // Play audio in background thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    audioTrack.play()

                    // Write audio data to track
                    var offset = 0
                    val pcmData = queuedAudio.pcmData

                    while (offset < pcmData.size && audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        val bytesToWrite = minOf(bufferSize / 4, pcmData.size - offset) // Write in chunks
                        val bytesWritten = audioTrack.write(pcmData, offset, bytesToWrite)

                        if (bytesWritten > 0) {
                            offset += bytesWritten
                        } else {
                            break
                        }
                    }

                    // Wait for playback to finish
                    while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        delay(10)
                    }

                } catch (e: Exception) {
                    println("Error during AudioTrack playback: ${e.message}")
                } finally {
                    // Cleanup when done
                    activeTracks.remove(key)
                    wrapper.cleanup()
                }
            }

        } catch (e: Exception) {
            println("Error creating Android AudioTrack: ${e.message}")
        }
    }

    private fun generateTrackKey(audioKey: String?, origin: Any?, trackId: String): String {
        val baseKey = when {
            audioKey != null -> audioKey
            origin != null -> origin.toString()
            else -> "unknown_${System.currentTimeMillis()}"
        }
        return "${baseKey}_$trackId"
    }

    private fun cleanupFinishedTracks() {
        val iterator = activeTracks.iterator()
        while (iterator.hasNext()) {
            val (key, wrapper) = iterator.next()
            val audioTrack = wrapper.audioTrack

            if (audioTrack.playState == AudioTrack.PLAYSTATE_STOPPED ||
                audioTrack.state == AudioTrack.STATE_UNINITIALIZED) {
                wrapper.cleanup()
                iterator.remove()
            }
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal) {
        if (!isInitialized) {
            println("Android AudioOutput not initialized")
            return
        }

        val rawData = audioSignal.rawData
        if (rawData == null || rawData.isEmpty()) {
            println("AudioSignal has no raw data")
            return
        }

        println("Queueing Android AudioSignal with ${rawData.size} bytes")

        val queuedAudio = QueuedAudio(
            rawData,
            null,
            audioSignal.origin,
            audioSignal.sampleRate,
            audioSignal.channels
        )
        audioQueue.offer(queuedAudio)
    }

    fun stopAudio(audioKey: String) {
        val keysToRemove = activeTracks.keys.filter { it.startsWith("${audioKey}_") }
        keysToRemove.forEach { key ->
            activeTracks[key]?.let { wrapper ->
                wrapper.cleanup()
                activeTracks.remove(key)
            }
        }
        if (keysToRemove.isNotEmpty()) {
            println("Stopped Android audio: $audioKey")
        }
    }

    fun stopAllAudio() {
        activeTracks.values.forEach { it.cleanup() }
        activeTracks.clear()
        audioQueue.clear()
        println("Stopped all Android audio")
    }

    fun cleanup() {
        try {
            processingJob?.cancel()
            stopAllAudio()

            if (isInitialized) {
                isInitialized = false
                println("Android AudioOutput cleaned up")
            }
        } catch (e: Exception) {
            println("Android cleanup error: ${e.message}")
        }
    }
}

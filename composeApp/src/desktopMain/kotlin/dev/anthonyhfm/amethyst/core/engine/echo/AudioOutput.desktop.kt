package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.*
import org.lwjgl.openal.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

actual object AudioOutput {
    private var device: Long = 0L
    private var context: Long = 0L
    private val activeSources = ConcurrentHashMap<String, AudioSource>()
    private var isInitialized = false
    private var processingJob: Job? = null
    private val audioQueue = ConcurrentLinkedQueue<QueuedAudio>()

    private const val SAMPLE_RATE = 44100
    private const val FORMAT = AL10.AL_FORMAT_STEREO16
    private const val MAX_SOURCES = 16

    data class QueuedAudio(
        val pcmData: ByteArray,
        val audioKey: String?,
        val origin: Any?
    )

    data class AudioSource(
        val sourceId: Int,
        val bufferIds: IntArray,
        val audioKey: String?,
        val origin: Any?
    ) {
        fun cleanup() {
            AL10.alSourceStop(sourceId)
            AL10.alDeleteSources(sourceId)
            AL10.alDeleteBuffers(bufferIds)
        }
    }

    init {
        initializeOpenAL()
    }

    private fun initializeOpenAL() {
        try {
            device = ALC10.alcOpenDevice(null as ByteBuffer?)
            if (device == 0L) {
                println("Failed to open OpenAL device")
                return
            }

            context = ALC10.alcCreateContext(device, null as IntArray?)
            if (context == 0L) {
                println("Failed to create OpenAL context")
                ALC10.alcCloseDevice(device)
                return
            }

            ALC10.alcMakeContextCurrent(context)

            AL.createCapabilities(ALC.createCapabilities(device))

            isInitialized = true
            println("OpenAL Audio Output initialized successfully")

            startAudioProcessing()

        } catch (e: Exception) {
            println("OpenAL initialization failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun startAudioProcessing() {
        processingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isInitialized) {
                try {
                    processAudioQueue()
                    cleanupFinishedSources()
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
            createAndPlayAudioSource(queuedAudio)
        }
    }

    private fun createAndPlayAudioSource(queuedAudio: QueuedAudio) {
        try {
            if (activeSources.size >= MAX_SOURCES) {
                println("Too many active audio sources, skipping")
                return
            }

            val sourceId = AL10.alGenSources()
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                println("Failed to create OpenAL source")
                return
            }

            val numBuffers = 2
            val bufferIds = IntArray(numBuffers)
            AL10.alGenBuffers(bufferIds)
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                println("Failed to create OpenAL buffers")
                AL10.alDeleteSources(sourceId)
                return
            }

            AL10.alSourcef(sourceId, AL10.AL_PITCH, 1.0f)
            AL10.alSourcef(sourceId, AL10.AL_GAIN, 1.0f)
            AL10.alSource3f(sourceId, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f)
            AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE)

            val pcmData = queuedAudio.pcmData
            val validSize = (pcmData.size / 4) * 4
            val validData = if (validSize != pcmData.size) {
                pcmData.sliceArray(0 until validSize)
            } else {
                pcmData
            }

            val buffer = MemoryUtil.memAlloc(validData.size)
            buffer.put(validData)
            buffer.flip()

            AL10.alBufferData(bufferIds[0], FORMAT, buffer, SAMPLE_RATE)
            MemoryUtil.memFree(buffer)

            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                println("Failed to load audio data")
                AL10.alDeleteSources(sourceId)
                AL10.alDeleteBuffers(bufferIds)
                return
            }

            AL10.alSourceQueueBuffers(sourceId, bufferIds[0])

            val audioSource = AudioSource(sourceId, bufferIds, queuedAudio.audioKey, queuedAudio.origin)
            val key = generateSourceKey(queuedAudio.audioKey, queuedAudio.origin)

            activeSources[key]?.cleanup()
            activeSources[key] = audioSource

            AL10.alSourcePlay(sourceId)

            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                println("Failed to start audio playback")
                audioSource.cleanup()
                activeSources.remove(key)
            }

        } catch (e: Exception) {
            println("Error creating audio source: ${e.message}")
        }
    }

    private fun generateSourceKey(audioKey: String?, origin: Any?): String {
        return when {
            audioKey != null -> audioKey
            origin != null -> origin.toString()
            else -> "unknown_${System.currentTimeMillis()}"
        }
    }

    private fun cleanupFinishedSources() {
        val iterator = activeSources.iterator()
        while (iterator.hasNext()) {
            val (key, audioSource) = iterator.next()
            val state = AL10.alGetSourcei(audioSource.sourceId, AL10.AL_SOURCE_STATE)

            if (state == AL10.AL_STOPPED) {
                audioSource.cleanup()
                iterator.remove()
            }
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal) {
        if (!isInitialized) {
            println("OpenAL AudioOutput not initialized")
            return
        }

        val rawData = audioSignal.rawData
        if (rawData == null || rawData.isEmpty()) {
            println("AudioSignal has no raw data")
            return
        }

        println("Queueing AudioSignal with ${rawData.size} bytes")

        val queuedAudio = QueuedAudio(rawData, null, audioSignal.origin)
        audioQueue.offer(queuedAudio)
    }

    fun stopAudio(audioKey: String) {
        activeSources[audioKey]?.let { audioSource ->
            audioSource.cleanup()
            activeSources.remove(audioKey)
            println("Stopped audio: $audioKey")
        }
    }

    fun stopAllAudio() {
        activeSources.values.forEach { it.cleanup() }
        activeSources.clear()
        audioQueue.clear()
        println("Stopped all audio")
    }

    fun cleanup() {
        try {
            processingJob?.cancel()
            stopAllAudio()

            if (isInitialized) {
                ALC10.alcMakeContextCurrent(0L)
                ALC10.alcDestroyContext(context)
                ALC10.alcCloseDevice(device)

                isInitialized = false
                println("OpenAL Audio Output cleaned up")
            }
        } catch (e: Exception) {
            println("Cleanup error: ${e.message}")
        }
    }
}
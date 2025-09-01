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

    // Windows-spezifische Low-Latency Einstellungen
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val bufferSize = if (isWindows) 512 else 1024 // Kleinere Buffer für Windows
    private val updateFrequency = if (isWindows) 60 else 30 // Höhere Update-Frequenz für Windows

    data class QueuedAudio(
        val pcmData: ByteArray,
        val audioKey: String?,
        val origin: Any?,
        val priority: Int = 0 // Priorität für sofortige Wiedergabe
    )

    data class AudioSource(
        val sourceId: Int,
        val bufferIds: IntArray,
        val audioKey: String?,
        val origin: Any?
    ) {
        fun cleanup() {
            try {
                AL10.alSourceStop(sourceId)
                AL10.alDeleteSources(sourceId)
                AL10.alDeleteBuffers(bufferIds)
            } catch (e: Exception) {
                println("Error during AudioSource cleanup: ${e.message}")
            }
        }
    }

    init {
        initializeOpenAL()
    }

    private fun initializeOpenAL() {
        try {
            println("Initializing OpenAL with LWJGL for ${System.getProperty("os.name")}...")

            // Windows-spezifische OpenAL Device-Konfiguration für niedrige Latenz
            val deviceName = if (isWindows) {
                // Versuche DirectSound Device für bessere Latenz unter Windows
                val devices = ALC11.alcGetString(0L, ALC11.ALC_ALL_DEVICES_SPECIFIER)
                println("Available audio devices: $devices")
                null // Lass OpenAL das beste Device wählen
            } else {
                null
            }

            device = ALC10.alcOpenDevice(deviceName as ByteBuffer?)
            if (device == 0L) {
                println("Failed to open OpenAL device")
                return
            }

            // Windows-spezifische Context-Attribute für niedrige Latenz
            val contextAttribs = if (isWindows) {
                intArrayOf(
                    ALC10.ALC_FREQUENCY, SAMPLE_RATE,
                    ALC11.ALC_REFRESH, updateFrequency, // Höhere Refresh-Rate für Windows
                    ALC11.ALC_SYNC, ALC10.ALC_FALSE,    // Asynchron für niedrige Latenz
                    0
                )
            } else {
                null
            }

            context = ALC10.alcCreateContext(device, contextAttribs)
            if (context == 0L) {
                println("Failed to create OpenAL context")
                ALC10.alcCloseDevice(device)
                return
            }

            if (!ALC10.alcMakeContextCurrent(context)) {
                println("Failed to make OpenAL context current")
                ALC10.alcDestroyContext(context)
                ALC10.alcCloseDevice(device)
                return
            }

            val alcCapabilities = ALC.createCapabilities(device)
            val alCapabilities = AL.createCapabilities(alcCapabilities)

            if (!alCapabilities.OpenAL10) {
                println("OpenAL 1.0 not supported")
                cleanup()
                return
            }

            // Windows-spezifische Listener-Konfiguration
            if (isWindows) {
                AL10.alListenerf(AL10.AL_GAIN, 1.0f)
                AL10.alListener3f(AL10.AL_POSITION, 0.0f, 0.0f, 0.0f)
                AL10.alListener3f(AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f)
                val orientation = floatArrayOf(0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f)
                AL10.alListenerfv(AL10.AL_ORIENTATION, orientation)
            }

            isInitialized = true
            println("OpenAL Audio Output initialized successfully with ${if (isWindows) "Windows low-latency" else "standard"} configuration")

            startAudioProcessing()

        } catch (e: Exception) {
            println("OpenAL initialization failed: ${e.message}")
            e.printStackTrace()
            cleanup()
        }
    }

    private fun startAudioProcessing() {
        processingJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isInitialized) {
                try {
                    processAudioQueue()
                    cleanupFinishedSources()
                    // Kürzere Delays für Windows für bessere Responsivität
                    delay(if (isWindows) 5 else 10)
                } catch (e: Exception) {
                    println("Audio processing error: ${e.message}")
                }
            }
        }
    }

    private fun processAudioQueue() {
        if (!isInitialized) return

        // Mehr parallel verarbeitete Audio-Streams für Windows
        val batchSize = if (isWindows) 8 else 4
        repeat(batchSize) {
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

            val bufferIds = IntArray(1) // Nur ein Buffer für niedrigere Latenz
            AL10.alGenBuffers(bufferIds)
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                println("Failed to create OpenAL buffers")
                AL10.alDeleteSources(sourceId)
                return
            }

            // Windows-spezifische Source-Konfiguration für niedrige Latenz
            AL10.alSourcef(sourceId, AL10.AL_PITCH, 1.0f)
            AL10.alSourcef(sourceId, AL10.AL_GAIN, 1.0f)
            AL10.alSource3f(sourceId, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f)
            AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE)

            if (isWindows) {
                // Windows-spezifische Optimierungen
                AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 1.0f)
                AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 0.0f)
                AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, Float.MAX_VALUE)
            }

            val pcmData = queuedAudio.pcmData
            val validSize = (pcmData.size / 4) * 4
            val validData = if (validSize != pcmData.size) {
                pcmData.sliceArray(0 until validSize)
            } else {
                pcmData
            }

            // Direkte Buffer-Allokation ohne zusätzliche Kopien
            val buffer = MemoryUtil.memAlloc(validData.size)
            try {
                buffer.put(validData)
                buffer.flip()

                AL10.alBufferData(bufferIds[0], FORMAT, buffer, SAMPLE_RATE)

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    println("Failed to load audio data")
                    AL10.alDeleteSources(sourceId)
                    AL10.alDeleteBuffers(bufferIds)
                    return
                }

                AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferIds[0])

                val audioSource = AudioSource(sourceId, bufferIds, queuedAudio.audioKey, queuedAudio.origin)
                val key = generateSourceKey(queuedAudio.audioKey, queuedAudio.origin)

                // Sofortiges Cleanup alter Sources mit demselben Key
                activeSources[key]?.cleanup()
                activeSources[key] = audioSource

                // Sofortige Wiedergabe ohne Queue-Buffer für niedrigere Latenz
                AL10.alSourcePlay(sourceId)

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    println("Failed to start audio playback")
                    audioSource.cleanup()
                    activeSources.remove(key)
                }

            } finally {
                MemoryUtil.memFree(buffer)
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

        // Hohe Priorität für sofortige Wiedergabe unter Windows
        val priority = if (isWindows) 1 else 0
        val queuedAudio = QueuedAudio(rawData, null, audioSignal.origin, priority)

        // Unter Windows: Versuche sofortige Verarbeitung für bessere Latenz
        if (isWindows && audioQueue.isEmpty() && activeSources.size < MAX_SOURCES) {
            // Direkte Verarbeitung ohne Queue für minimale Latenz
            CoroutineScope(Dispatchers.Default).launch {
                createAndPlayAudioSource(queuedAudio)
            }
        } else {
            audioQueue.offer(queuedAudio)
        }
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

            if (context != 0L) {
                ALC10.alcMakeContextCurrent(0L)
                ALC10.alcDestroyContext(context)
                context = 0L
            }

            if (device != 0L) {
                ALC10.alcCloseDevice(device)
                device = 0L
            }

            isInitialized = false
            println("OpenAL AudioOutput cleanup completed")
        } catch (e: Exception) {
            println("Error during AudioOutput cleanup: ${e.message}")
        }
    }
}
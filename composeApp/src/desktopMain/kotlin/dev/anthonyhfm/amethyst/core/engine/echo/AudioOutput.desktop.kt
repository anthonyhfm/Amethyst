package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
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

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    // Windows needs higher update frequency for lower latency (120Hz vs 30Hz)
    private val updateFrequency = if (isWindows) 120 else 30
    // Windows needs smaller buffer periods for lower latency (2ms vs 10ms)
    private val processingDelay = if (isWindows) 2L else 10L
    // Windows processes more sources per cycle to compensate for faster updates
    private val batchSize = if (isWindows) 12 else 4

    data class QueuedAudio(
        val pcmData: ByteArray,
        val audioKey: String?,
        val origin: Any?,
        val priority: Int = 0,
        var format: Pair<Signal.AudioSignal, Int>? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QueuedAudio) return false
            return pcmData.contentEquals(other.pcmData) &&
                   audioKey == other.audioKey &&
                   origin == other.origin &&
                   priority == other.priority &&
                   format == other.format
        }

        override fun hashCode(): Int {
            var result = pcmData.contentHashCode()
            result = 31 * result + (audioKey?.hashCode() ?: 0)
            result = 31 * result + (origin?.hashCode() ?: 0)
            result = 31 * result + priority
            result = 31 * result + (format?.hashCode() ?: 0)
            return result
        }
    }

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
                // Silent cleanup
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioSource) return false
            return sourceId == other.sourceId &&
                   bufferIds.contentEquals(other.bufferIds) &&
                   audioKey == other.audioKey &&
                   origin == other.origin
        }

        override fun hashCode(): Int {
            var result = sourceId
            result = 31 * result + bufferIds.contentHashCode()
            result = 31 * result + (audioKey?.hashCode() ?: 0)
            result = 31 * result + (origin?.hashCode() ?: 0)
            return result
        }
    }

    init {
        initializeOpenAL()
    }

    private fun initializeOpenAL() {
        try {
            // On Windows, use default device for best compatibility
            // On other platforms, let OpenAL choose the best device
            val deviceName: ByteBuffer? = null

            device = ALC10.alcOpenDevice(deviceName)
            if (device == 0L) {
                return
            }

            val contextAttribs = if (isWindows) {
                // Configure OpenAL for low-latency on Windows:
                // - Higher refresh rate (120Hz) for more responsive updates
                // - Async mode (ALC_SYNC=FALSE) for non-blocking operations
                // - Smaller internal buffer periods for lower latency
                val AL_SOFT_BUFFER_SAMPLES = 0x1010  // OpenAL Soft extension for buffer control
                intArrayOf(
                    ALC10.ALC_FREQUENCY, SAMPLE_RATE,
                    ALC11.ALC_REFRESH, updateFrequency,
                    ALC11.ALC_SYNC, ALC10.ALC_FALSE,
                    AL_SOFT_BUFFER_SAMPLES, 512,  // Hint for smaller internal buffers
                    0
                )
            } else {
                null
            }

            context = ALC10.alcCreateContext(device, contextAttribs)
            if (context == 0L) {
                ALC10.alcCloseDevice(device)
                return
            }

            if (!ALC10.alcMakeContextCurrent(context)) {
                ALC10.alcDestroyContext(context)
                ALC10.alcCloseDevice(device)
                return
            }

            val alcCapabilities = ALC.createCapabilities(device)
            val alCapabilities = AL.createCapabilities(alcCapabilities)

            if (!alCapabilities.OpenAL10) {
                cleanup()
                return
            }

            if (isWindows) {
                AL10.alListenerf(AL10.AL_GAIN, 1.0f)
                AL10.alListener3f(AL10.AL_POSITION, 0.0f, 0.0f, 0.0f)
                AL10.alListener3f(AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f)
                val orientation = floatArrayOf(0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f)
                AL10.alListenerfv(AL10.AL_ORIENTATION, orientation)
            }

            isInitialized = true
            startAudioProcessing()

        } catch (e: Exception) {
            cleanup()
        }
    }

    private fun startAudioProcessing() {
        processingJob = CoroutineScope(Dispatchers.Default).launch {
            // On Windows, boost thread priority for audio processing to reduce latency
            if (isWindows) {
                try {
                    Thread.currentThread().priority = Thread.MAX_PRIORITY
                } catch (e: Exception) {
                    // Ignore if we can't set priority
                }
            }
            
            while (isActive && isInitialized) {
                try {
                    processAudioQueue()
                    cleanupFinishedSources()
                    delay(processingDelay)
                } catch (e: Exception) {
                    // Continue processing
                }
            }
        }
    }

    private fun processAudioQueue() {
        if (!isInitialized) return

        // Windows processes more sources per cycle due to higher update frequency
        repeat(batchSize) {
            val queuedAudio = audioQueue.poll() ?: return

            if (queuedAudio.format != null) {
                val (audioSignal, openALFormat) = queuedAudio.format!!
                createAndPlayAudioSourceWithFormat(queuedAudio, audioSignal, openALFormat)
            } else {
                createAndPlayAudioSourceLegacy(queuedAudio)
            }
        }
    }

    private fun createAndPlayAudioSourceLegacy(queuedAudio: QueuedAudio) {
        try {
            if (activeSources.size >= MAX_SOURCES) return

            val sourceId = AL10.alGenSources()
            if (AL10.alGetError() != AL10.AL_NO_ERROR) return

            val bufferIds = IntArray(1)
            AL10.alGenBuffers(bufferIds)
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                AL10.alDeleteSources(sourceId)
                return
            }

            AL10.alSourcef(sourceId, AL10.AL_PITCH, 1.0f)
            AL10.alSourcef(sourceId, AL10.AL_GAIN, 0.9f * GlobalSettings.masterVolume)
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
            try {
                buffer.put(validData)
                buffer.flip()

                AL10.alBufferData(bufferIds[0], FORMAT, buffer, SAMPLE_RATE)

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    AL10.alDeleteSources(sourceId)
                    AL10.alDeleteBuffers(bufferIds)
                    return
                }

                AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferIds[0])

                val audioSource = AudioSource(sourceId, bufferIds, queuedAudio.audioKey, queuedAudio.origin)
                val key = generateSourceKey(queuedAudio.audioKey, queuedAudio.origin)

                activeSources[key]?.cleanup()
                activeSources[key] = audioSource

                AL10.alSourcePlay(sourceId)

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    audioSource.cleanup()
                    activeSources.remove(key)
                }

            } finally {
                MemoryUtil.memFree(buffer)
            }

        } catch (e: Exception) {
            // Silent error handling
        }
    }

    private fun createAndPlayAudioSourceWithFormat(
        queuedAudio: QueuedAudio,
        audioSignal: Signal.AudioSignal,
        openALFormat: Int
    ) {
        try {
            if (activeSources.size >= MAX_SOURCES) return

            val sourceId = AL10.alGenSources()
            if (AL10.alGetError() != AL10.AL_NO_ERROR) return

            val bufferIds = IntArray(1)
            AL10.alGenBuffers(bufferIds)
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                AL10.alDeleteSources(sourceId)
                return
            }

            AL10.alSourcef(sourceId, AL10.AL_PITCH, 1.0f)
            AL10.alSourcef(sourceId, AL10.AL_GAIN, 0.9f * GlobalSettings.masterVolume)
            AL10.alSource3f(sourceId, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f)
            AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE)

            if (isWindows) {
                // Optimize for low-latency 2D audio on Windows:
                // - Use relative positioning (no 3D calculations)
                // - Disable distance attenuation
                // - Set maximum distance to avoid culling
                AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
                AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 1.0f)
                AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 0.0f)
                AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, Float.MAX_VALUE)
            }

            val pcmData = queuedAudio.pcmData
            val buffer = MemoryUtil.memAlloc(pcmData.size)

            try {
                buffer.put(pcmData)
                buffer.flip()

                AL10.alBufferData(bufferIds[0], openALFormat, buffer, audioSignal.sampleRate)

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    AL10.alDeleteSources(sourceId)
                    AL10.alDeleteBuffers(bufferIds)
                    return
                }

                AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferIds[0])

                val audioSource = AudioSource(sourceId, bufferIds, queuedAudio.audioKey, queuedAudio.origin)
                val key = generateSourceKey(queuedAudio.audioKey, queuedAudio.origin)

                activeSources[key]?.cleanup()
                activeSources[key] = audioSource

                AL10.alSourcePlay(sourceId)

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    audioSource.cleanup()
                    activeSources.remove(key)
                }

            } finally {
                MemoryUtil.memFree(buffer)
            }

        } catch (e: Exception) {
            // Silent error handling
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
            val (_, audioSource) = iterator.next()
            val state = AL10.alGetSourcei(audioSource.sourceId, AL10.AL_SOURCE_STATE)

            if (state == AL10.AL_STOPPED) {
                audioSource.cleanup()
                iterator.remove()
            }
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!isInitialized) return null

        val rawData = audioSignal.rawData
        if (rawData == null || rawData.isEmpty()) return null

        val openALFormat = determineOpenALFormat(audioSignal)
        if (openALFormat == -1) return null

        val normalizedData = normalizeAudioForPlayback(rawData, audioSignal)

        val sourceId = "audio_${System.currentTimeMillis()}_${(0..999).random()}"
        val queuedAudio = QueuedAudio(normalizedData, sourceId, audioSignal.origin, if (isWindows) 1 else 0)
        queuedAudio.format = Pair(audioSignal, openALFormat)

        if (isWindows && audioQueue.isEmpty() && activeSources.size < MAX_SOURCES) {
            CoroutineScope(Dispatchers.Default).launch {
                createAndPlayAudioSourceWithFormat(queuedAudio, audioSignal, openALFormat)
            }
        } else {
            audioQueue.offer(queuedAudio)
        }

        return sourceId
    }

    actual fun stop(sourceId: String) {
        val audioSource = activeSources[sourceId]

        if (audioSource != null) {
            try {
                AL10.alSourceStop(audioSource.sourceId)
                activeSources.remove(sourceId)
                audioSource.cleanup()
            } catch (e: Exception) { }
        }
    }

    actual fun stopAll() {
        try {
            activeSources.values.forEach { audioSource ->
                AL10.alSourceStop(audioSource.sourceId)
                audioSource.cleanup()
            }
            activeSources.clear()
            audioQueue.clear()
        } catch (e: Exception) {
            // Silent error handling
        }
    }

    actual fun stopByOrigin(origin: Any?) {
        if (origin == null) return
        val toRemove = activeSources.filter { it.value.origin == origin }.keys
        toRemove.forEach { stop(it) }
    }

    private fun determineOpenALFormat(audioSignal: Signal.AudioSignal): Int {
        return when {
            audioSignal.channels == 1 && audioSignal.bitDepth == 8 -> AL10.AL_FORMAT_MONO8
            audioSignal.channels == 1 && audioSignal.bitDepth == 16 -> AL10.AL_FORMAT_MONO16
            audioSignal.channels == 2 && audioSignal.bitDepth == 8 -> AL10.AL_FORMAT_STEREO8
            audioSignal.channels == 2 && audioSignal.bitDepth == 16 -> AL10.AL_FORMAT_STEREO16
            else -> AL10.AL_FORMAT_STEREO16
        }
    }

    private fun normalizeAudioForPlayback(data: ByteArray, audioSignal: Signal.AudioSignal): ByteArray {
        if (audioSignal.bitDepth != 16) return data

        val maxAmplitude = findMaxAmplitude(data, audioSignal.bitDepth)
        val clippingThreshold = 32767

        val amplitudeRatio = maxAmplitude.toDouble() / clippingThreshold
        if (amplitudeRatio > 0.95) {
            val reductionFactor = 0.85
            return applyVolumeReduction(data, audioSignal.bitDepth, reductionFactor)
        }

        return data
    }

    private fun findMaxAmplitude(data: ByteArray, bitDepth: Int): Int {
        val bytesPerSample = bitDepth / 8
        var maxAmplitude = 0

        for (i in data.indices step bytesPerSample) {
            if (i + bytesPerSample <= data.size) {
                val amplitude = when (bitDepth) {
                    16 -> {
                        val sample = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)
                        kotlin.math.abs(if (sample > 32767) sample - 65536 else sample)
                    }
                    else -> 0
                }
                maxAmplitude = maxOf(maxAmplitude, amplitude)
            }
        }

        return maxAmplitude
    }

    private fun applyVolumeReduction(data: ByteArray, bitDepth: Int, reductionFactor: Double): ByteArray {
        val result = ByteArray(data.size)
        val bytesPerSample = bitDepth / 8

        for (i in data.indices step bytesPerSample) {
            if (i + bytesPerSample <= data.size) {
                when (bitDepth) {
                    16 -> {
                        val sample = (data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)
                        val signedSample = if (sample > 32767) sample - 65536 else sample
                        val reducedSample = (signedSample * reductionFactor).toInt().coerceIn(-32768, 32767)

                        result[i] = (reducedSample and 0xFF).toByte()
                        result[i + 1] = ((reducedSample shr 8) and 0xFF).toByte()
                    }
                    else -> {
                        for (j in 0 until bytesPerSample) {
                            result[i + j] = data[i + j]
                        }
                    }
                }
            }
        }

        return result
    }

    private fun cleanup() {
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
        } catch (e: Exception) {
            // Silent cleanup
        }
    }

    private fun stopAllAudio() {
        activeSources.values.forEach { it.cleanup() }
        activeSources.clear()
        audioQueue.clear()
    }
}

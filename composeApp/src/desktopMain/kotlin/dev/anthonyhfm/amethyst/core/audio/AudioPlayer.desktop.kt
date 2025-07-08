package dev.anthonyhfm.amethyst.core.audio

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import org.lwjgl.stb.STBVorbis.stb_vorbis_decode_memory
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ShortBuffer

actual object AudioPlayer {
    private var device: Long = 0
    private var context: Long = 0
    private val availableSources = mutableListOf<Int>()
    private var isInitialized = false

    private val audioBuffers = mutableMapOf<String, Int>()

    private fun ensureInitialized() {
        if (!isInitialized) {
            initializeOpenAL()
        }
    }

    private fun initializeOpenAL() {
        try {
            device = ALC10.alcOpenDevice(null as ByteBuffer?)
            if (device == MemoryUtil.NULL) {
                throw RuntimeException("Failed to open OpenAL device")
            }

            val contextAttribs = intArrayOf(
                ALC10.ALC_FREQUENCY, 44100,
                ALC10.ALC_REFRESH, 120,
                ALC10.ALC_SYNC, ALC10.ALC_FALSE,
                0
            )

            context = ALC10.alcCreateContext(device, contextAttribs)
            if (context == MemoryUtil.NULL) {
                ALC10.alcCloseDevice(device)
                throw RuntimeException("Failed to create OpenAL context")
            }

            ALC10.alcMakeContextCurrent(context)
            AL.createCapabilities(ALC.createCapabilities(device))

            repeat(64) {
                val source = AL10.alGenSources()
                if (AL10.alGetError() == AL10.AL_NO_ERROR) {
                    availableSources.add(source)
                }
            }

            isInitialized = true
            println("OpenAL initialized with ${availableSources.size} sources (anti-crackling mode)")

        } catch (e: Exception) {
            println("Failed to initialize OpenAL: ${e.message}")
            throw e
        }
    }

    actual fun loadAudio(data: ByteArray, uuid: String?): String {
        ensureInitialized()

        val audioId = uuid ?: UUID.randomUUID()

        try {
            val audioInfo = decodeAudioData(data)

            val bufferId = AL10.alGenBuffers()
            val format = when (audioInfo.channels) {
                1 -> AL10.AL_FORMAT_MONO16
                2 -> AL10.AL_FORMAT_STEREO16
                else -> throw RuntimeException("Unsupported channel count: ${audioInfo.channels}")
            }

            AL10.alBufferData(bufferId, format, audioInfo.pcmData, audioInfo.sampleRate)
            checkALError("Buffer data")

            audioBuffers[audioId] = bufferId

            val audioClip = AudioClip(
                name = "Audio_${audioId.take(8)}",
                length = audioInfo.duration,
                data = data,
                key = audioId
            )

            WorkspaceRepository.audioRegistry[audioId] = audioClip

            println("Audio loaded: $audioId (${audioInfo.duration}ms, ${audioInfo.sampleRate}Hz)")
            return audioId

        } catch (e: Exception) {
            println("Failed to load audio: ${e.message}")
            throw e
        }
    }

    actual fun playAudio(audioKey: String) {
        ensureInitialized()

        val audioClip = WorkspaceRepository.audioRegistry[audioKey]
            ?: return

        val bufferId = audioBuffers[audioKey]
            ?: return

        try {
            val source = getAvailableSource()

            AL10.alSourcei(source, AL10.AL_BUFFER, bufferId)
            AL10.alSourcef(source, AL10.AL_PITCH, 1.0f)
            AL10.alSourcef(source, AL10.AL_GAIN, 1.0f)
            AL10.alSource3f(source, AL10.AL_POSITION, 0.0f, 0.0f, 0.0f)
            AL10.alSource3f(source, AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f)
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE)

            AL10.alSourcePlay(source)
            checkALError("Source play")

        } catch (e: Exception) {
            println("Failed to play audio $audioKey: ${e.message}")
        }
    }

    fun removeAudio(audioKey: String) {
        audioBuffers[audioKey]?.let { bufferId ->
            AL10.alDeleteBuffers(bufferId)
            audioBuffers.remove(audioKey)
        }
        WorkspaceRepository.audioRegistry.remove(audioKey)
    }

    actual fun preloadFromAudioClip(audioClip: AudioClip) {
        ensureInitialized()

        try {
            val audioInfo = decodeAudioData(audioClip.data)
            val bufferId = AL10.alGenBuffers()
            val format = when (audioInfo.channels) {
                1 -> AL10.AL_FORMAT_MONO16
                2 -> AL10.AL_FORMAT_STEREO16
                else -> throw RuntimeException("Unsupported channel count: ${audioInfo.channels}")
            }

            AL10.alBufferData(bufferId, format, audioInfo.pcmData, audioInfo.sampleRate)
            checkALError("Preload buffer data")

            audioBuffers[audioClip.key] = bufferId
            WorkspaceRepository.audioRegistry[audioClip.key] = audioClip

        } catch (e: Exception) {
            println("Failed to preload audio ${audioClip.key}: ${e.message}")
        }
    }

    private fun getAvailableSource(): Int {
        availableSources.forEach { source ->
            val state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE)
            if (state != AL10.AL_PLAYING) {
                return source
            }
        }
        return availableSources.firstOrNull()
            ?: throw RuntimeException("No audio sources available")
    }

    private fun decodeAudioData(audioData: ByteArray): AudioInfo {
        return when {
            isWavFile(audioData) -> decodeWav(audioData)
            isOggFile(audioData) -> decodeOgg(audioData)
            else -> throw RuntimeException("Unsupported audio format")
        }
    }

    private fun isWavFile(data: ByteArray): Boolean {
        return data.size >= 12 &&
                data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
                data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()
    }

    private fun isOggFile(data: ByteArray): Boolean {
        return data.size >= 4 &&
                data[0] == 'O'.code.toByte() && data[1] == 'g'.code.toByte() &&
                data[2] == 'g'.code.toByte() && data[3] == 'S'.code.toByte()
    }

    private fun decodeWav(audioData: ByteArray): AudioInfo {
        val buffer = ByteBuffer.wrap(audioData).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        buffer.position(22)
        val channels = buffer.short.toInt()
        buffer.position(24)
        val sampleRate = buffer.int
        buffer.position(34)
        val bitsPerSample = buffer.short.toInt()

        if (bitsPerSample != 16 && bitsPerSample != 8 && bitsPerSample != 24) {
            throw RuntimeException("Unsupported bit depth: $bitsPerSample. Supported: 8, 16, 24 bit")
        }

        buffer.position(36)
        while (buffer.remaining() >= 8) {
            val chunkType = String(buffer.array(), buffer.position(), 4)
            buffer.position(buffer.position() + 4)
            val chunkSize = buffer.int

            if (chunkType == "data") {
                val samples = when (bitsPerSample) {
                    8 -> chunkSize / channels
                    16 -> chunkSize / (channels * 2)
                    24 -> chunkSize / (channels * 3)
                    else -> chunkSize / (channels * 2)
                }

                val duration = (samples * 1000L) / sampleRate
                val pcmBuffer = MemoryUtil.memAllocShort(samples * channels)

                when (bitsPerSample) {
                    8 -> {
                        repeat(samples * channels) {
                            val sample = (buffer.get().toInt() and 0xFF) - 128
                            pcmBuffer.put((sample shl 8).toShort())
                        }
                    }
                    16 -> {
                        repeat(samples * channels) {
                            pcmBuffer.put(buffer.short)
                        }
                    }
                    24 -> {
                        repeat(samples * channels) {
                            val byte1 = buffer.get().toInt() and 0xFF
                            val byte2 = buffer.get().toInt() and 0xFF
                            val byte3 = buffer.get().toInt() and 0xFF
                            val sample = (byte3 shl 16) or (byte2 shl 8) or byte1
                            pcmBuffer.put((sample shr 8).toShort())
                        }
                    }
                }

                pcmBuffer.flip()
                return AudioInfo(pcmBuffer, duration, sampleRate, channels)
            } else {
                buffer.position(buffer.position() + chunkSize)
            }
        }

        throw RuntimeException("No data chunk found in WAV file")
    }

    private fun decodeOgg(audioData: ByteArray): AudioInfo {
        val buffer = MemoryUtil.memAlloc(audioData.size)
        buffer.put(audioData).flip()

        return stackPush().use { stack ->
            val channelsBuffer = stack.mallocInt(1)
            val sampleRateBuffer = stack.mallocInt(1)

            val rawPcm = stb_vorbis_decode_memory(buffer, channelsBuffer, sampleRateBuffer)
                ?: throw RuntimeException("Failed to decode OGG file")

            val channels = channelsBuffer.get(0)
            val sampleRate = sampleRateBuffer.get(0)
            val samples = rawPcm.remaining() / channels
            val duration = (samples * 1000L) / sampleRate

            AudioInfo(rawPcm, duration, sampleRate, channels)
        }.also {
            MemoryUtil.memFree(buffer)
        }
    }

    private fun checkALError(operation: String) {
        val error = AL10.alGetError()
        if (error != AL10.AL_NO_ERROR) {
            throw RuntimeException("OpenAL error during $operation: $error")
        }
    }

    fun cleanup() {
        if (isInitialized) {
            // Alle OpenAL Buffer löschen
            audioBuffers.values.forEach { bufferId ->
                AL10.alDeleteBuffers(bufferId)
            }
            audioBuffers.clear()

            // Sources löschen
            availableSources.forEach { source ->
                AL10.alDeleteSources(source)
            }
            availableSources.clear()

            // OpenAL cleanup
            ALC10.alcMakeContextCurrent(MemoryUtil.NULL)
            ALC10.alcDestroyContext(context)
            ALC10.alcCloseDevice(device)

            isInitialized = false
            println("OpenAL cleaned up")
        }
    }
}

private data class AudioInfo(
    val pcmData: ShortBuffer,
    val duration: Long,
    val sampleRate: Int,
    val channels: Int
)

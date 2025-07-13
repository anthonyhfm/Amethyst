package dev.anthonyhfm.amethyst.core.audio

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import org.lwjgl.stb.STBVorbis.stb_vorbis_decode_memory
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioSystem

actual object AudioPlayer {
    private var device: Long = 0
    private var context: Long = 0
    private val availableSources = mutableListOf<Int>()
    private val initialized = AtomicBoolean(false)
    private val initializationLock = Object()

    private val audioBuffers = mutableMapOf<String, Int>()

    private fun ensureInitialized() {
        if (!initialized.get()) {
            synchronized(initializationLock) {
                if (!initialized.get()) {
                    try {
                        initializeOpenAL()
                        initialized.set(true)
                    } catch (e: Exception) {
                        println("Failed to initialize OpenAL: ${e.message}")
                        e.printStackTrace()
                        throw e
                    }
                }
            }
        }
    }

    private fun initializeOpenAL() {
        try {
            // Verwende null anstatt null as ByteBuffer? für mehr Klarheit
            device = ALC10.alcOpenDevice(null as String?)
            if (device == MemoryUtil.NULL) {
                throw RuntimeException("Failed to open OpenAL device")
            }

            val contextAttribs = IntBuffer.allocate(7)
            contextAttribs.put(ALC10.ALC_FREQUENCY).put(44100)
            contextAttribs.put(ALC10.ALC_REFRESH).put(120)
            contextAttribs.put(ALC10.ALC_SYNC).put(ALC10.ALC_FALSE)
            contextAttribs.put(0)
            contextAttribs.flip()

            context = ALC10.alcCreateContext(device, contextAttribs)
            if (context == MemoryUtil.NULL) {
                ALC10.alcCloseDevice(device)
                throw RuntimeException("Failed to create OpenAL context")
            }

            if (!ALC10.alcMakeContextCurrent(context)) {
                ALC10.alcDestroyContext(context)
                ALC10.alcCloseDevice(device)
                throw RuntimeException("Failed to make OpenAL context current")
            }

            // Capabilities erstellen
            AL.createCapabilities(ALC.createCapabilities(device))

            // Quellen generieren
            repeat(32) {
                val source = AL10.alGenSources()
                if (AL10.alGetError() == AL10.AL_NO_ERROR) {
                    availableSources.add(source)
                } else {
                    return
                }
            }

            if (availableSources.isEmpty()) {
                throw RuntimeException("Failed to generate any OpenAL sources")
            }

            println("OpenAL initialized successfully with ${availableSources.size} sources")

        } catch (e: Exception) {
            cleanup() // Aufräumen bei Fehlern
            throw RuntimeException("OpenAL initialization failed: ${e.message}", e)
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
            e.printStackTrace()
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
            isWavFile(audioData) -> decodeWavSafely(audioData)
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

    private fun decodeWavSafely(audioData: ByteArray): AudioInfo {
        // Verwende Java's AudioSystem für sicherere WAV-Dekodierung
        try {
            val inputStream = ByteArrayInputStream(audioData)
            val audioInputStream = AudioSystem.getAudioInputStream(inputStream)

            val format = audioInputStream.format
            val channels = format.channels
            val sampleRate = format.sampleRate.toInt()
            val frameSize = format.frameSize

            // Berechne Samples und Dauer
            val frameCount = audioInputStream.frameLength
            val samples = frameCount.toInt()
            val duration = (frameCount * 1000 / sampleRate).toLong()

            // PCM-Daten lesen
            val byteData = ByteArray(samples * frameSize)
            val bytesRead = audioInputStream.read(byteData)

            if (bytesRead <= 0) {
                throw RuntimeException("Failed to read audio data from WAV")
            }

            // Konvertiere zu ShortBuffer für OpenAL
            val pcmBuffer = MemoryUtil.memAllocShort(samples * channels)
            val byteBuffer = ByteBuffer.wrap(byteData).order(java.nio.ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until samples * channels) {
                if (byteBuffer.remaining() >= 2) {
                    pcmBuffer.put(byteBuffer.short)
                } else {
                    break
                }
            }

            pcmBuffer.flip()
            audioInputStream.close()

            return AudioInfo(pcmBuffer, duration, sampleRate, channels)
        } catch (e: Exception) {
            throw RuntimeException("Failed to decode WAV file: ${e.message}", e)
        }
    }

    private fun decodeOgg(audioData: ByteArray): AudioInfo {
        var buffer: ByteBuffer? = null

        try {
            buffer = MemoryUtil.memAlloc(audioData.size)
            buffer.put(audioData).flip()

            return MemoryStack.stackPush().use { stack ->
                val channelsBuffer = stack.mallocInt(1)
                val sampleRateBuffer = stack.mallocInt(1)

                val rawPcm = stb_vorbis_decode_memory(buffer, channelsBuffer, sampleRateBuffer)
                    ?: throw RuntimeException("Failed to decode OGG file")

                val channels = channelsBuffer.get(0)
                val sampleRate = sampleRateBuffer.get(0)
                val samples = rawPcm.remaining() / channels
                val duration = (samples * 1000L) / sampleRate

                AudioInfo(rawPcm, duration, sampleRate, channels)
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to decode OGG file: ${e.message}", e)
        } finally {
            buffer?.let { MemoryUtil.memFree(it) }
        }
    }

    private fun checkALError(operation: String) {
        val error = AL10.alGetError()
        if (error != AL10.AL_NO_ERROR) {
            throw RuntimeException("OpenAL error during $operation: $error")
        }
    }

    // Stelle sicher, dass in der cleanup()-Methode auch alle PCM-Daten freigegeben werden
    fun cleanup() {
        if (initialized.get()) {
            try {
                // Audio-Puffer freigeben
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
                if (context != MemoryUtil.NULL) {
                    ALC10.alcDestroyContext(context)
                    context = MemoryUtil.NULL
                }

                if (device != MemoryUtil.NULL) {
                    ALC10.alcCloseDevice(device)
                    device = MemoryUtil.NULL
                }

                initialized.set(false)
                println("OpenAL cleaned up successfully")
            } catch (e: Exception) {
                println("Error during OpenAL cleanup: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

private data class AudioInfo(
    val pcmData: ShortBuffer,
    val duration: Long,
    val sampleRate: Int,
    val channels: Int
)

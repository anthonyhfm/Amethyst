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

actual object AudioPlayer {
    private var device: Long = 0
    private var context: Long = 0
    private val availableSources = mutableListOf<Int>()
    private var isInitialized = false

    private val audioBuffers = mutableMapOf<String, Int>()
    private val pcmBuffers = mutableMapOf<String, ByteBuffer>()

    private fun ensureInitialized() {
        if (!isInitialized) {
            try {
                initializeOpenAL()
            } catch (e: Exception) {
                println("Failed to initialize OpenAL: ${e.message}")
                println("This might be a JVM module access issue. Please ensure proper JVM arguments are set.")
                throw RuntimeException("Audio system initialization failed. Check JVM arguments for LWJGL compatibility.", e)
            }
        }
    }

    private fun initializeOpenAL() {
        device = ALC10.alcOpenDevice(null as ByteBuffer?)
        if (device == MemoryUtil.NULL) throw RuntimeException("Failed to open OpenAL device")

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
            val src = AL10.alGenSources()
            if (AL10.alGetError() == AL10.AL_NO_ERROR) availableSources.add(src)
        }
        isInitialized = true
        println("OpenAL initialized with ${'$'}{availableSources.size} sources (anti-crackling mode)")
    }

    actual fun loadAudio(data: ByteArray, uuid: String?): String {
        ensureInitialized()
        val audioId = uuid ?: UUID.randomUUID()
        try {
            val info = decodeAudioData(data)
            val bufId = AL10.alGenBuffers()
            val format = when (info.channels) {
                1 -> AL10.AL_FORMAT_MONO16
                2 -> AL10.AL_FORMAT_STEREO16
                else -> throw RuntimeException("Unsupported channel count: ${'$'}{info.channels}")
            }
            AL10.alBufferData(bufId, format, info.pcmData, info.sampleRate)
            checkALError("Buffer data")

            audioBuffers[audioId] = bufId
            pcmBuffers[audioId] = info.pcmData

            val clip = AudioClip(
                name = "Audio_${'$'}{audioId.take(8)}",
                length = info.duration,
                data = data,
                key = audioId
            )
            WorkspaceRepository.audioRegistry[audioId] = clip
            println("Audio loaded: ${'$'}audioId (${info.duration}ms, ${info.sampleRate}Hz)")
            return audioId
        } catch (e: Exception) {
            pcmBuffers.remove(audioId)?.let { MemoryUtil.memFree(it) }
            throw e
        }
    }

    actual fun playAudio(audioKey: String) {
        ensureInitialized()
        val bufId = audioBuffers[audioKey] ?: return
        try {
            val src = getAvailableSource()
            AL10.alSourcei(src, AL10.AL_BUFFER, bufId)
            AL10.alSourcef(src, AL10.AL_PITCH, 1.0f)
            AL10.alSourcef(src, AL10.AL_GAIN, 1.0f)
            AL10.alSource3f(src, AL10.AL_POSITION, 0f, 0f, 0f)
            AL10.alSource3f(src, AL10.AL_VELOCITY, 0f, 0f, 0f)
            AL10.alSourcei(src, AL10.AL_LOOPING, AL10.AL_FALSE)
            AL10.alSourcePlay(src)
            checkALError("Source play")
        } catch (e: Exception) {
            println("Failed to play audio $audioKey: ${'$'}{e.message}")
        }
    }

    fun removeAudio(audioKey: String) {
        audioBuffers.remove(audioKey)?.let { AL10.alDeleteBuffers(it) }
        pcmBuffers.remove(audioKey)?.let { MemoryUtil.memFree(it) }
        WorkspaceRepository.audioRegistry.remove(audioKey)
    }

    actual fun preloadFromAudioClip(audioClip: AudioClip) {
        ensureInitialized()
        try {
            val info = decodeAudioData(audioClip.data)
            val bufId = AL10.alGenBuffers()
            val format = when (info.channels) {
                1 -> AL10.AL_FORMAT_MONO16
                2 -> AL10.AL_FORMAT_STEREO16
                else -> throw RuntimeException("Unsupported channel count: ${'$'}{info.channels}")
            }
            AL10.alBufferData(bufId, format, info.pcmData, info.sampleRate)
            checkALError("Preload buffer data")

            audioBuffers[audioClip.key] = bufId
            pcmBuffers[audioClip.key] = info.pcmData
            WorkspaceRepository.audioRegistry[audioClip.key] = audioClip
        } catch (e: Exception) {
            pcmBuffers.remove(audioClip.key)?.let { MemoryUtil.memFree(it) }
        }
    }

    private fun getAvailableSource(): Int = availableSources.firstOrNull {
        AL10.alGetSourcei(it, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING
    } ?: throw RuntimeException("No audio sources available")

    private fun decodeAudioData(data: ByteArray): AudioInfo = when {
        isWav(data) -> decodeWav(data)
        isOgg(data) -> decodeOgg(data)
        else -> throw RuntimeException("Unsupported audio format")
    }

    private fun isWav(d: ByteArray) = d.size >= 12 && d[0]=='R'.toByte() && d[1]=='I'.toByte() && d[2]=='F'.toByte() && d[3]=='F'.toByte()
    private fun isOgg(d: ByteArray) = d.size >=4 && d[0]=='O'.toByte() && d[1]=='g'.toByte() && d[2]=='g'.toByte() && d[3]=='S'.toByte()

    private fun decodeWav(audioData: ByteArray): AudioInfo {
        val buf = ByteBuffer.wrap(audioData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        require(buf.limit() >= 44) { "Invalid WAV: too short" }
        buf.position(22); val channels = buf.short.toInt()
        buf.position(24); val rate = buf.int
        buf.position(34); val bits = buf.short.toInt()
        require(bits in listOf(8, 16, 24)) { "Unsupported bit depth: $bits" }

        buf.position(36)
        var dataSize = 0; var samples = 0
        while (buf.remaining() >= 8) {
            val idBytes = ByteArray(4).also { buf.get(it) }
            val id = String(idBytes, Charsets.US_ASCII)
            val size = buf.int
            if (id == "data") {
                dataSize = size
                samples = when (bits) {
                    8 -> size / channels
                    16 -> size / (channels * 2)
                    24 -> size / (channels * 3)
                    else -> size / (channels * 2)
                }
                break
            }
            buf.position(buf.position() + size)
        }
        require(dataSize > 0) { "No data chunk in WAV" }
        val duration = (samples * 1000L) / rate

        val byteCount = samples * channels * 2
        val pcmBuf = MemoryUtil.memAlloc(byteCount).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val pcmView = pcmBuf.asShortBuffer()

        repeat(samples * channels) {
            when (bits) {
                8 -> {
                    val u = buf.get().toInt() and 0xFF
                    val s16 = ((u - 128) shl 8).toShort()
                    pcmView.put(s16)
                }
                16 -> {
                    pcmView.put(buf.short)
                }
                24 -> {
                    val b1 = buf.get().toInt() and 0xFF
                    val b2 = (buf.get().toInt() and 0xFF) shl 8
                    var b3 = (buf.get().toInt() and 0xFF) shl 16
                    var sample24 = b1 or b2 or b3
                    if (sample24 and 0x800000 != 0) sample24 = sample24 or -0x1000000
                    pcmView.put((sample24 shr 8).toShort())
                }
            }
        }
        pcmView.flip(); pcmBuf.position(0)
        return AudioInfo(pcmBuf, duration, rate, channels)
    }

    private fun decodeOgg(data: ByteArray): AudioInfo {
        val input = MemoryUtil.memAlloc(data.size)
        input.put(data).flip()
        return stackPush().use { stack ->
            val chB = stack.mallocInt(1)
            val rB = stack.mallocInt(1)
            val raw = stb_vorbis_decode_memory(input, chB, rB)
                ?: throw RuntimeException("Failed to decode OGG")
            val channels = chB.get(0)
            val rate = rB.get(0)
            val samples = raw.remaining() / channels
            val duration = (samples * 1000L) / rate

            val byteCount = raw.remaining() * 2
            val pcmBuf = try {
                MemoryUtil.memAlloc(byteCount).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            } catch (e: OutOfMemoryError) {
                MemoryUtil.memFree(input)
                throw RuntimeException("Failed to allocate memory for OGG decoding. This may be due to missing JVM arguments for LWJGL.", e)
            }
            pcmBuf.asShortBuffer().put(raw).flip(); pcmBuf.position(0)
            MemoryUtil.memFree(input)
            AudioInfo(pcmBuf, duration, rate, channels)
        }
    }

    private fun checkALError(op: String) {
        val err = AL10.alGetError()
        if (err != AL10.AL_NO_ERROR) throw RuntimeException("OpenAL error during $op: $err")
    }

    fun cleanup() {
        if (!isInitialized) return
        pcmBuffers.values.forEach { try { MemoryUtil.memFree(it) } catch (_: Exception) {} }
        pcmBuffers.clear()
        audioBuffers.values.forEach { AL10.alDeleteBuffers(it) }
        audioBuffers.clear()
        availableSources.forEach { AL10.alDeleteSources(it) }
        availableSources.clear()
        ALC10.alcMakeContextCurrent(MemoryUtil.NULL)
        ALC10.alcDestroyContext(context)
        ALC10.alcCloseDevice(device)
        isInitialized = false
    }
}

private data class AudioInfo(
    val pcmData: ByteBuffer,
    val duration: Long,
    val sampleRate: Int,
    val channels: Int
)

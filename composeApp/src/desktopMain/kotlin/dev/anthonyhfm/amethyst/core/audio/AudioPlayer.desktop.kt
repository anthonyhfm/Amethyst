package dev.anthonyhfm.amethyst.core.audio

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

actual object AudioPlayer {
    private var isInitialized = false
    private val audioClips = ConcurrentHashMap<String, LoadedAudioClip>()
    private val playingClips = ConcurrentHashMap<String, Clip>()
    private var mixer: Mixer? = null
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(4)
    private val cleanupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private data class LoadedAudioClip(
        val audioFormat: AudioFormat,
        val audioData: ByteArray,
        val duration: Long
    )

    private fun ensureInitialized() {
        if (!isInitialized) {
            try {
                initializeAudioSystem()
            } catch (e: Exception) {
                println("Failed to initialize audio system: ${e.message}")
                throw RuntimeException("Audio system initialization failed.", e)
            }
        }
    }

    private fun initializeAudioSystem() {
        // Verwende das beste verfügbare Audio-Device mit niedriger Latenz
        val mixerInfos = AudioSystem.getMixerInfo()

        // Bevorzuge DirectSound auf Windows oder CoreAudio auf macOS für niedrige Latenz
        mixer = mixerInfos.find { info ->
            info.name.contains("DirectSound", ignoreCase = true) ||
            info.name.contains("CoreAudio", ignoreCase = true) ||
            info.name.contains("Primary Sound Driver", ignoreCase = true)
        }?.let { AudioSystem.getMixer(it) } ?: AudioSystem.getMixer(null)

        isInitialized = true

        // Starte Cleanup-Task für beendete Clips
        executor.scheduleAtFixedRate({
            cleanupFinishedClips()
        }, 1, 1, TimeUnit.SECONDS)

        println("Java Sound API initialized with mixer: ${mixer?.mixerInfo?.name ?: "Default"}")
    }

    actual fun loadAudio(data: ByteArray, uuid: String?): String {
        ensureInitialized()
        val audioId = uuid ?: UUID.randomUUID()

        try {
            val audioInfo = decodeAudioData(data)
            audioClips[audioId] = LoadedAudioClip(
                audioFormat = audioInfo.format,
                audioData = audioInfo.pcmData,
                duration = audioInfo.duration
            )

            val clip = AudioClip(
                name = "Audio_${audioId.take(8)}",
                length = audioInfo.duration,
                data = data,
                key = audioId
            )
            WorkspaceRepository.audioRegistry[audioId] = clip

            println("Audio loaded: $audioId (${audioInfo.duration}ms, ${audioInfo.format.sampleRate.toInt()}Hz)")
            return audioId
        } catch (e: Exception) {
            audioClips.remove(audioId)
            throw e
        }
    }

    actual fun playAudio(audioKey: String) {
        ensureInitialized()
        val loadedClip = audioClips[audioKey] ?: return

        try {
            // Erstelle einen neuen Clip für jede Wiedergabe (ermöglicht mehrfache gleichzeitige Wiedergabe)
            val clip = AudioSystem.getClip(mixer?.mixerInfo)
            val audioInputStream = AudioInputStream(
                ByteArrayInputStream(loadedClip.audioData),
                loadedClip.audioFormat,
                loadedClip.audioData.size / loadedClip.audioFormat.frameSize.toLong()
            )

            clip.open(audioInputStream)

            // Optimiere für niedrige Latenz
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                gainControl.value = 0.0f // 0 dB = normale Lautstärke
            }

            // Tracking für Cleanup
            val playId = "${audioKey}_${System.currentTimeMillis()}"
            playingClips[playId] = clip

            // Cleanup nach Wiedergabe
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    cleanupScope.launch {
                        delay(100) // Kurze Verzögerung für sauberes Cleanup
                        playingClips.remove(playId)?.close()
                    }
                }
            }

            clip.start()
        } catch (e: Exception) {
            println("Failed to play audio $audioKey: ${e.message}")
        }
    }

    actual fun preloadFromAudioClip(audioClip: AudioClip) {
        ensureInitialized()
        try {
            val audioInfo = decodeAudioData(audioClip.data)
            audioClips[audioClip.key] = LoadedAudioClip(
                audioFormat = audioInfo.format,
                audioData = audioInfo.pcmData,
                duration = audioInfo.duration
            )
            WorkspaceRepository.audioRegistry[audioClip.key] = audioClip
        } catch (e: Exception) {
            audioClips.remove(audioClip.key)
            println("Failed to preload audio clip ${audioClip.key}: ${e.message}")
        }
    }

    fun removeAudio(audioKey: String) {
        audioClips.remove(audioKey)
        WorkspaceRepository.audioRegistry.remove(audioKey)

        // Stoppe alle laufenden Instanzen dieses Clips
        playingClips.entries.removeAll { (playId, clip) ->
            if (playId.startsWith(audioKey)) {
                clip.stop()
                clip.close()
                true
            } else false
        }
    }

    private fun cleanupFinishedClips() {
        playingClips.entries.removeAll { (_, clip) ->
            if (!clip.isRunning) {
                try {
                    clip.close()
                } catch (e: Exception) {
                    // Ignoriere Cleanup-Fehler
                }
                true
            } else false
        }
    }

    private fun decodeAudioData(data: ByteArray): AudioInfo = when {
        isWav(data) -> decodeWav(data)
        isOgg(data) -> throw RuntimeException("OGG format not supported in Java Sound implementation. Please use WAV files.")
        else -> throw RuntimeException("Unsupported audio format. Only WAV files are supported.")
    }

    private fun isWav(d: ByteArray) = d.size >= 12 &&
        d[0] == 'R'.code.toByte() && d[1] == 'I'.code.toByte() &&
        d[2] == 'F'.code.toByte() && d[3] == 'F'.code.toByte()

    private fun isOgg(d: ByteArray) = d.size >= 4 &&
        d[0] == 'O'.code.toByte() && d[1] == 'g'.code.toByte() &&
        d[2] == 'g'.code.toByte() && d[3] == 'S'.code.toByte()

    private fun decodeWav(audioData: ByteArray): AudioInfo {
        val buf = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.limit() >= 44) { "Invalid WAV: too short" }

        // WAV Header parsing
        buf.position(22); val channels = buf.short.toInt()
        buf.position(24); val sampleRate = buf.int
        buf.position(34); val bitsPerSample = buf.short.toInt()

        require(bitsPerSample in listOf(8, 16, 24)) { "Unsupported bit depth: $bitsPerSample" }
        require(channels in 1..2) { "Unsupported channel count: $channels" }

        // Find data chunk
        buf.position(36)
        var dataSize = 0
        var dataStartPos = 0

        while (buf.remaining() >= 8) {
            val chunkId = ByteArray(4).also { buf.get(it) }
            val chunkSize = buf.int

            if (String(chunkId, Charsets.US_ASCII) == "data") {
                dataSize = chunkSize
                dataStartPos = buf.position()
                break
            }
            buf.position(buf.position() + chunkSize)
        }

        require(dataSize > 0) { "No data chunk found in WAV file" }

        // Create AudioFormat
        val encoding = if (bitsPerSample == 8) AudioFormat.Encoding.PCM_UNSIGNED else AudioFormat.Encoding.PCM_SIGNED
        val frameSize = channels * (bitsPerSample / 8)
        val frameRate = sampleRate.toFloat()
        val bigEndian = false

        val audioFormat = AudioFormat(encoding, frameRate, bitsPerSample, channels, frameSize, frameRate, bigEndian)

        // Extract audio data
        buf.position(dataStartPos)
        val audioBytes = ByteArray(dataSize)
        buf.get(audioBytes, 0, minOf(dataSize, buf.remaining()))

        val samples = dataSize / frameSize
        val duration = (samples * 1000L) / sampleRate

        return AudioInfo(audioFormat, audioBytes, duration)
    }

    fun cleanup() {
        if (!isInitialized) return

        // Stoppe alle laufenden Clips
        playingClips.values.forEach { clip ->
            try {
                clip.stop()
                clip.close()
            } catch (e: Exception) {
                // Ignoriere Cleanup-Fehler
            }
        }
        playingClips.clear()
        audioClips.clear()

        // Cleanup Coroutine Scope
        cleanupScope.cancel()

        // Shutdown Executor
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }

        isInitialized = false
        println("Audio system cleaned up")
    }
}

private data class AudioInfo(
    val format: AudioFormat,
    val pcmData: ByteArray,
    val duration: Long
)

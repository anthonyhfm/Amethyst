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
        val mixerInfos = AudioSystem.getMixerInfo()

        mixer = mixerInfos.find { info ->
            info.name.contains("DirectSound", ignoreCase = true) ||
            info.name.contains("CoreAudio", ignoreCase = true) ||
            info.name.contains("Primary Sound Driver", ignoreCase = true)
        }?.let { AudioSystem.getMixer(it) } ?: AudioSystem.getMixer(null)

        isInitialized = true

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

    actual fun getAudioClip(data: ByteArray): AudioClip? {
        ensureInitialized()
        val audioId = UUID.randomUUID()

        try {
            val audioInfo = decodeAudioData(data)

            val clip = AudioClip(
                name = "Audio_${audioId.take(8)}",
                length = audioInfo.duration,
                data = data,
                key = audioId
            )

            println("Audio loaded: $audioId (${audioInfo.duration}ms, ${audioInfo.format.sampleRate.toInt()}Hz)")

            return clip
        } catch (e: Exception) {
            return null
        }
    }

    actual fun getAudioClip(data: ByteArray, sampleStart: Long, sampleEnd: Long): AudioClip? {
        ensureInitialized()
        val audioId = UUID.randomUUID()

        try {
            val audioInfo = decodeAudioData(data)

            // Validiere die Sample-Parameter
            val totalSamples = audioInfo.pcmData.size / audioInfo.format.frameSize
            if (sampleStart < 0 || sampleEnd <= sampleStart || sampleEnd > totalSamples) {
                println("Invalid sample range: start=$sampleStart, end=$sampleEnd, totalSamples=$totalSamples")
                return null
            }

            // Berechne Byte-Positionen basierend auf Frame-Grenzen
            val frameSize = audioInfo.format.frameSize
            val startByte = (sampleStart * frameSize).toInt()
            val endByte = (sampleEnd * frameSize).toInt()
            val extractedLength = endByte - startByte

            // Extrahiere den gewünschten Datenbereich
            val extractedPcmData = audioInfo.pcmData.copyOfRange(startByte, endByte)

            // Erstelle neue WAV-Datei aus den extrahierten PCM-Daten
            val extractedWavData = createWavFromPcm(extractedPcmData, audioInfo.format)

            // Berechne die Dauer des extrahierten Samples
            val extractedSamples = sampleEnd - sampleStart
            val extractedDuration = (extractedSamples * 1000L) / audioInfo.format.sampleRate.toLong()

            val clip = AudioClip(
                name = "Sample_${audioId.take(8)}_${sampleStart}-${sampleEnd}",
                length = extractedDuration,
                data = extractedWavData,
                key = audioId
            )

            println("Audio sample extracted: $audioId (${extractedDuration}ms, samples: $sampleStart-$sampleEnd)")

            return clip
        } catch (e: Exception) {
            println("Failed to extract audio sample: ${e.message}")
            return null
        }
    }

    actual fun playAudio(audioKey: String) {
        ensureInitialized()
        val loadedClip = audioClips[audioKey] ?: return

        try {
            val clip = AudioSystem.getClip(mixer?.mixerInfo)
            val audioInputStream = AudioInputStream(
                ByteArrayInputStream(loadedClip.audioData),
                loadedClip.audioFormat,
                loadedClip.audioData.size / loadedClip.audioFormat.frameSize.toLong()
            )

            clip.open(audioInputStream)

            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                gainControl.value = 0.0f
            }

            val playId = "${audioKey}_${System.currentTimeMillis()}"
            playingClips[playId] = clip

            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    cleanupScope.launch {
                        delay(100)
                        playingClips.remove(playId)?.close()
                    }
                }
            }

            clip.start()
        } catch (e: Exception) {
            println("Failed to play audio $audioKey: ${e.message}")
        }
    }

    actual fun stopAudio(audioKey: String) {
        ensureInitialized()

        cleanupScope.launch {
            playingClips.remove(audioKey)?.close()
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
                } catch (e: Exception) { }

                true
            } else false
        }
    }

    private fun decodeAudioData(data: ByteArray): AudioInfo = when {
        isWav(data) -> decodeWav(data)
        isMp3(data) -> decodeMp3(data)
        isOgg(data) -> throw RuntimeException("OGG format not supported in Java Sound implementation. Please use WAV or MP3 files.")
        else -> throw RuntimeException("Unsupported audio format. Only WAV and MP3 files are supported.")
    }

    private fun isWav(d: ByteArray) = d.size >= 12 &&
        d[0] == 'R'.code.toByte() && d[1] == 'I'.code.toByte() &&
        d[2] == 'F'.code.toByte() && d[3] == 'F'.code.toByte()

    private fun isMp3(d: ByteArray) = d.size >= 3 && (
        (d[0] == 'I'.code.toByte() && d[1] == 'D'.code.toByte() && d[2] == '3'.code.toByte()) ||
        (d[0] == 0xFF.toByte() && (d[1].toInt() and 0xE0) == 0xE0) ||
        d.indices.take(100).any { i ->
            i < d.size - 1 && d[i] == 0xFF.toByte() && (d[i + 1].toInt() and 0xE0) == 0xE0
        }
    )

    private fun isOgg(d: ByteArray) = d.size >= 4 &&
        d[0] == 'O'.code.toByte() && d[1] == 'g'.code.toByte() &&
        d[2] == 'g'.code.toByte() && d[3] == 'S'.code.toByte()

    private fun decodeWav(audioData: ByteArray): AudioInfo {
        val buf = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.limit() >= 44) { "Invalid WAV: too short" }

        // Verify RIFF header
        val riffHeader = ByteArray(4).also { buf.get(it) }
        require(String(riffHeader, Charsets.US_ASCII) == "RIFF") { "Not a valid RIFF file" }

        val fileSize = buf.int // File size (can be ignored)

        val waveHeader = ByteArray(4).also { buf.get(it) }
        require(String(waveHeader, Charsets.US_ASCII) == "WAVE") { "Not a valid WAVE file" }

        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataSize = 0
        var dataStartPos = 0
        var fmtFound = false

        // Parse chunks
        while (buf.remaining() >= 8) {
            val chunkId = ByteArray(4).also { buf.get(it) }
            val chunkSize = buf.int
            val chunkIdStr = String(chunkId, Charsets.US_ASCII)

            when (chunkIdStr) {
                "fmt " -> {
                    val chunkStart = buf.position()
                    val audioFormat = buf.short.toInt() // Should be 1 for PCM
                    require(audioFormat == 1) { "Only PCM format is supported, got format: $audioFormat" }

                    channels = buf.short.toInt()
                    sampleRate = buf.int
                    val byteRate = buf.int // Can be ignored
                    val blockAlign = buf.short.toInt() // Can be ignored
                    bitsPerSample = buf.short.toInt()

                    fmtFound = true

                    // Skip any remaining bytes in the fmt chunk
                    buf.position(chunkStart + chunkSize)
                }
                "data" -> {
                    require(fmtFound) { "fmt chunk must come before data chunk" }
                    dataSize = chunkSize
                    dataStartPos = buf.position()
                    break // Stop parsing, we found the data
                }
                else -> {
                    // Skip unknown chunks
                    buf.position(buf.position() + chunkSize)
                }
            }
        }

        require(fmtFound) { "No fmt chunk found in WAV file" }
        require(dataSize > 0) { "No data chunk found in WAV file" }
        require(bitsPerSample in listOf(8, 16, 24, 32)) { "Unsupported bit depth: $bitsPerSample" }
        require(channels in 1..2) { "Unsupported channel count: $channels" }

        val encoding = if (bitsPerSample == 8) AudioFormat.Encoding.PCM_UNSIGNED else AudioFormat.Encoding.PCM_SIGNED
        val frameSize = channels * (bitsPerSample / 8)
        val frameRate = sampleRate.toFloat()
        val bigEndian = false

        val audioFormat = AudioFormat(encoding, frameRate, bitsPerSample, channels, frameSize, frameRate, bigEndian)

        buf.position(dataStartPos)
        val audioBytes = ByteArray(dataSize)
        buf.get(audioBytes, 0, minOf(dataSize, buf.remaining()))

        val samples = dataSize / frameSize
        val duration = (samples * 1000L) / sampleRate

        return AudioInfo(audioFormat, audioBytes, duration)
    }

    private fun decodeMp3(audioData: ByteArray): AudioInfo {
        try {
            val inputStream = ByteArrayInputStream(audioData)
            val audioInputStream = AudioSystem.getAudioInputStream(inputStream)

            val sourceFormat = audioInputStream.format
            println("MP3 Source format: $sourceFormat")

            val targetFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.sampleRate,
                16,
                sourceFormat.channels,
                sourceFormat.channels * 2,
                sourceFormat.sampleRate,
                false
            )

            val pcmStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream)

            val pcmData = pcmStream.readAllBytes()

            val frameLength = pcmData.size / targetFormat.frameSize
            val duration = (frameLength * 1000L / targetFormat.sampleRate).toLong()

            pcmStream.close()
            audioInputStream.close()

            return AudioInfo(targetFormat, pcmData, duration)

        } catch (e: Exception) {
            throw RuntimeException("Failed to decode MP3: ${e.message}", e)
        }
    }

    fun cleanup() {
        if (!isInitialized) return

        playingClips.values.forEach { clip ->
            try {
                clip.stop()
                clip.close()
            } catch (e: Exception) { }
        }
        playingClips.clear()
        audioClips.clear()

        cleanupScope.cancel()

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

private fun createWavFromPcm(pcmData: ByteArray, audioFormat: AudioFormat): ByteArray {
    val byteArrayOutputStream = java.io.ByteArrayOutputStream()

    // WAV Header
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + pcmData.size) // File size - 8 bytes
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // Subchunk1 size (16 for PCM)
    header.putShort(1) // Audio format (1 for PCM)
    header.putShort(audioFormat.channels.toShort()) // Number of channels
    header.putInt(audioFormat.sampleRate.toInt()) // Sample rate
    header.putInt(audioFormat.sampleRate.toInt() * audioFormat.channels * (audioFormat.sampleSizeInBits / 8)) // Byte rate
    header.putShort((audioFormat.channels * (audioFormat.sampleSizeInBits / 8)).toShort()) // Block align
    header.putShort(audioFormat.sampleSizeInBits.toShort()) // Bits per sample
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(pcmData.size) // Subchunk2 size (data size)

    byteArrayOutputStream.write(header.array())
    byteArrayOutputStream.write(pcmData)

    return byteArrayOutputStream.toByteArray()
}

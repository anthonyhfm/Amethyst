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
    private var audioTrack: AudioTrack? = null
    private var isInitialized = false
    private val activeSources = mutableMapOf<String, AudioPlaybackInfo>()

    data class AudioPlaybackInfo(
        val audioTrack: AudioTrack,
        val startTime: Long = System.currentTimeMillis()
    )

    init {
        initializeAudioTrack()
    }

    private fun initializeAudioTrack() {
        try {
            isInitialized = true
        } catch (e: Exception) {
            // Silent error handling
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!isInitialized) return null

        val rawData = audioSignal.rawData
        if (rawData == null || rawData.isEmpty()) return null

        return try {
            val sourceId = "audio_${System.currentTimeMillis()}_${(0..999).random()}"

            val bufferSize = AudioTrack.getMinBufferSize(
                audioSignal.sampleRate,
                if (audioSignal.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
                if (audioSignal.bitDepth == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                audioSignal.sampleRate,
                if (audioSignal.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
                if (audioSignal.bitDepth == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STATIC
            )

            audioTrack.write(rawData, 0, rawData.size)
            audioTrack.play()

            activeSources[sourceId] = AudioPlaybackInfo(audioTrack)
            sourceId
        } catch (e: Exception) {
            null
        }
    }

    actual fun stop(sourceId: String) {
        activeSources[sourceId]?.let { playbackInfo ->
            try {
                playbackInfo.audioTrack.stop()
                playbackInfo.audioTrack.release()
                activeSources.remove(sourceId)
            } catch (e: Exception) {
                // Silent error handling
            }
        }
    }

    actual fun stopAll() {
        try {
            activeSources.values.forEach { playbackInfo ->
                playbackInfo.audioTrack.stop()
                playbackInfo.audioTrack.release()
            }
            activeSources.clear()
        } catch (e: Exception) {
            // Silent error handling
        }
    }
}

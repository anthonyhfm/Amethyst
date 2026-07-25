package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.settings.data.AudioSettings
import dev.anthonyhfm.amethyst.timeline.data.AudioSourceLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSRecursiveLock

/** Native iOS implementation of the shared Echo renderer and voice graph. */
actual object Echo {
    private val lifecycleLock = NSRecursiveLock()

    private var playback = AudioPlaybackEngine(AudioChain())
    private var output: IosAudioOutput? = null
    private var preferredBufferFrames = DEFAULT_BUFFER_FRAMES

    actual suspend fun decodeAudioFile(
        filePath: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? = withContext(Dispatchers.Default) {
        IosAudioDecoder.decodeFile(filePath, sampleStart, sampleEnd)
    }

    actual suspend fun decodeAudioData(
        audioData: ByteArray,
        fileName: String,
        sampleStart: Long?,
        sampleEnd: Long?,
    ): Signal.AudioSignal? = withContext(Dispatchers.Default) {
        IosAudioDecoder.decodeData(audioData, fileName, sampleStart, sampleEnd)
    }

    actual fun isFormatSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in IosAudioDecoder.formats

    actual fun getSupportedFormats(): List<String> = IosAudioDecoder.formats

    actual fun initialize(): Boolean = withLifecycleLock {
        val activeOutput = output ?: IosAudioOutput(playback).also { output = it }
        val initialized = activeOutput.initialize(
            preferredBufferFrames = preferredBufferFrames,
            initialMasterGain = AudioSettings.masterVolume.value,
        )
        if (!initialized) {
            println("Echo/iOS: output initialization failed: ${activeOutput.startFailure ?: "app is in background"}")
        }
        initialized
    }

    actual fun setPreferredBufferFrames(frames: Int) {
        val normalized = when {
            frames <= 64 -> 64
            frames <= 128 -> 128
            frames <= 256 -> 256
            else -> frames.coerceAtMost(2_048)
        }
        withLifecycleLock {
            if (preferredBufferFrames == normalized) return@withLifecycleLock
            preferredBufferFrames = normalized
            output?.updatePreferredBufferFrames(normalized)
        }
    }

    /** iOS owns output-route selection through Control Center and route pickers. */
    actual fun outputDevices(): List<String> = emptyList()

    actual fun setPreferredOutputDevice(name: String?) = Unit

    actual fun setMasterGain(gain: Float) {
        playback.setMasterGain(gain)
    }

    actual fun attachAudioChain(chain: AudioChain) {
        withLifecycleLock {
            if (playback.renderer.chain === chain) return@withLifecycleLock
            output?.close()
            output = null
            playback = AudioPlaybackEngine(chain)
            // Desktop initializes Echo explicitly during application startup.
            // iOS has no equivalent bootstrap, so prepare its exclusive
            // foreground output as soon as the workspace audio chain exists.
            // This also removes session/graph startup from the first live note.
            initialize()
        }
    }

    actual fun play(audioSignal: Signal.AudioSignal): String? {
        if (!initialize()) return null
        playback.setMasterGain(AudioSettings.masterVolume.value)
        return playback.play(audioSignal)
    }

    actual fun playSource(
        sourceId: String,
        startFrame: Long,
        endFrameExclusive: Long,
        gain: Float,
        pan: Float,
        origin: Any?,
    ): String? = playSources(
        listOf(
            AudioSourcePlayback(
                sourceId = sourceId,
                startFrame = startFrame,
                endFrameExclusive = endFrameExclusive,
                gain = gain,
                pan = pan,
                origin = origin,
            )
        )
    ).firstOrNull()

    actual fun playSources(sources: List<AudioSourcePlayback>): List<String?> {
        if (sources.isEmpty()) return emptyList()
        if (!initialize()) return List(sources.size) { null }
        playback.setMasterGain(AudioSettings.masterVolume.value)
        val targetFrame = playback.renderer.absoluteFrame
        return sources.map { request ->
            val source = AudioSourceLibrary.get(request.sourceId)
                ?: return@map null
            val pcm = runCatching {
                ByteArrayPcmAudioSource(
                    id = source.id,
                    sampleRate = source.sampleRate,
                    channels = source.channels,
                    bitDepth = source.bitDepth,
                    rawData = source.rawData,
                )
            }.getOrNull() ?: return@map null
            playback.play(
                source = pcm,
                sourceStartFrame = request.startFrame,
                sourceEndFrameExclusive = request.endFrameExclusive,
                origin = request.origin,
                gain = request.gain,
                pan = request.pan,
                targetFrame = targetFrame,
            )
        }
    }

    actual fun playMultiple(signals: List<Signal.AudioSignal>): List<String?> {
        if (signals.isEmpty()) return emptyList()
        if (!initialize()) return List(signals.size) { null }
        playback.setMasterGain(AudioSettings.masterVolume.value)
        val targetFrame = playback.renderer.absoluteFrame
        return signals.map { playback.play(it, targetFrame) }
    }

    actual fun update(sourceId: String, gain: Float, pan: Float) {
        playback.setMasterGain(AudioSettings.masterVolume.value)
        playback.update(sourceId, gain, pan)
    }

    actual fun stop(sourceId: String) = playback.stop(sourceId)

    actual fun stopAll() = playback.stopAll()

    actual fun stopByOrigin(origin: Any?) = playback.stopByOrigin(origin)

    actual fun audioEnter(signals: List<Signal.AudioSignal>) {
        playMultiple(signals)
    }

    actual fun cancel(signalOrigin: Any?) = stopByOrigin(signalOrigin)

    actual fun reset() = playback.reset()

    actual fun shutdown() {
        withLifecycleLock {
            output?.close()
            output = null
        }
    }

    private inline fun <T> withLifecycleLock(block: () -> T): T {
        lifecycleLock.lock()
        return try {
            block()
        } finally {
            lifecycleLock.unlock()
        }
    }

    private const val DEFAULT_BUFFER_FRAMES = 128
}

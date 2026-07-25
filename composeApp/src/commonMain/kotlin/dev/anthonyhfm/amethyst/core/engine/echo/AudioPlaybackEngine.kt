package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.audio.AudioRenderer
import dev.anthonyhfm.amethyst.core.engine.audio.command.AudioRenderCommand
import dev.anthonyhfm.amethyst.core.engine.audio.source.AudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.voice.PcmAudioVoice
import dev.anthonyhfm.amethyst.core.engine.audio.voice.VoiceId
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import kotlinx.atomicfu.atomic

/**
 * Platform-independent control plane for Echo playback.
 *
 * It owns the only renderer/voice graph. Platform backends only move the
 * rendered Float32 blocks to their native low-latency output.
 */
class AudioPlaybackEngine(
    chain: AudioChain,
    private val maximumVoices: Int = AudioRenderer.DEFAULT_MAXIMUM_VOICES,
) {
    val renderer = AudioRenderer(
        chain = chain,
        maximumVoices = maximumVoices,
    )

    private data class Playback(
        val voiceId: VoiceId,
        val origin: Any?,
    )

    private val nextVoiceId = atomic(1L)
    private val playbacks = atomic<Map<String, Playback>>(emptyMap())

    val configuration: AudioConfiguration?
        get() = renderer.configuration

    fun prepare(configuration: AudioConfiguration) {
        renderer.prepare(configuration)
        playbacks.value = emptyMap()
    }

    fun play(
        signal: Signal.AudioSignal,
        targetFrame: Long = renderer.absoluteFrame,
    ): String? {
        val bytes = signal.rawData ?: return null
        if (bytes.isEmpty() || signal.channels !in 1..2) return null
        val source = runCatching {
            ByteArrayPcmAudioSource(
                id = "signal-${nextVoiceId.value}",
                sampleRate = signal.sampleRate,
                channels = signal.channels,
                bitDepth = signal.bitDepth,
                rawData = bytes,
            )
        }.getOrNull() ?: return null
        return play(
            source = source,
            origin = signal.origin,
            gain = signal.gain,
            pan = signal.pan,
            targetFrame = targetFrame,
        )
    }

    fun play(
        source: AudioSource,
        sourceStartFrame: Long = 0L,
        sourceEndFrameExclusive: Long = source.frameCount,
        origin: Any? = null,
        gain: Float = 1f,
        pan: Float = 0f,
        targetFrame: Long = renderer.absoluteFrame,
    ): String? {
        if (renderer.configuration == null) return null
        val voiceId = VoiceId(nextVoiceId.getAndIncrement())
        val publicId = "echo-${voiceId.value}"
        val voice = runCatching {
            PcmAudioVoice(
                id = voiceId,
                source = source,
                startFrame = targetFrame,
                sourceStartFrame = sourceStartFrame,
                sourceEndFrameExclusive = sourceEndFrameExclusive,
                gain = gain.coerceAtLeast(0f),
                pan = pan.coerceIn(-1f, 1f),
            )
        }.getOrNull() ?: return null
        if (!renderer.enqueue(AudioRenderCommand.StartVoice(targetFrame, voice))) return null
        updatePlaybacks { current ->
            val oldestPossibleVoice = (voiceId.value - maximumVoices + 1L).coerceAtLeast(1L)
            current
                .filterValues { it.voiceId.value >= oldestPossibleVoice }
                .plus(publicId to Playback(voiceId, origin))
        }
        return publicId
    }

    fun update(
        sourceId: String,
        gain: Float,
        pan: Float,
        rampFrames: Int = DEFAULT_PARAMETER_RAMP_FRAMES,
    ) {
        if (renderer.configuration == null) return
        val playback = playbacks.value[sourceId] ?: return
        renderer.enqueue(
            AudioRenderCommand.UpdateVoiceMix(
                targetFrame = renderer.absoluteFrame,
                voiceId = playback.voiceId,
                gain = gain.coerceAtLeast(0f),
                pan = pan.coerceIn(-1f, 1f),
                rampFrames = rampFrames.coerceAtLeast(0),
            )
        )
    }

    fun stop(
        sourceId: String,
        fadeOutFrames: Int = DEFAULT_STOP_FADE_FRAMES,
    ) {
        if (renderer.configuration == null) {
            removePlayback(sourceId)
            return
        }
        val playback = removePlayback(sourceId) ?: return
        renderer.enqueue(
            AudioRenderCommand.StopVoice(
                targetFrame = renderer.absoluteFrame,
                voiceId = playback.voiceId,
                fadeOutFrames = fadeOutFrames.coerceAtLeast(0),
            )
        )
    }

    fun stopByOrigin(
        origin: Any?,
        fadeOutFrames: Int = DEFAULT_STOP_FADE_FRAMES,
    ) {
        if (renderer.configuration == null) return
        val matches = playbacks.value
            .filterValues { it.origin == origin }
        if (matches.isEmpty()) return
        matches.forEach { (_, playback) ->
            renderer.enqueue(
                AudioRenderCommand.StopVoice(
                    targetFrame = renderer.absoluteFrame,
                    voiceId = playback.voiceId,
                    fadeOutFrames = fadeOutFrames.coerceAtLeast(0),
                )
            )
        }
        updatePlaybacks { current -> current - matches.keys }
    }

    fun stopAll(fadeOutFrames: Int = DEFAULT_STOP_FADE_FRAMES) {
        if (renderer.configuration == null) {
            playbacks.value = emptyMap()
            return
        }
        renderer.enqueue(
            AudioRenderCommand.StopAll(
                targetFrame = renderer.absoluteFrame,
                fadeOutFrames = fadeOutFrames.coerceAtLeast(0),
            )
        )
        playbacks.value = emptyMap()
    }

    fun setMasterGain(
        gain: Float,
        rampFrames: Int = DEFAULT_PARAMETER_RAMP_FRAMES,
    ) {
        if (renderer.configuration == null) return
        renderer.enqueue(
            AudioRenderCommand.SetMasterGain(
                targetFrame = renderer.absoluteFrame,
                gain = gain.coerceAtLeast(0f),
                rampFrames = rampFrames.coerceAtLeast(0),
            )
        )
    }

    fun reset() {
        playbacks.value = emptyMap()
        if (renderer.configuration == null) {
            renderer.reset()
            return
        }
        renderer.enqueue(
            AudioRenderCommand.ResetAll(
                targetFrame = renderer.absoluteFrame,
            )
        )
    }

    fun release() {
        renderer.release()
        playbacks.value = emptyMap()
    }

    private fun removePlayback(sourceId: String): Playback? {
        while (true) {
            val current = playbacks.value
            val removed = current[sourceId] ?: return null
            if (playbacks.compareAndSet(current, current - sourceId)) return removed
        }
    }

    private inline fun updatePlaybacks(
        transform: (Map<String, Playback>) -> Map<String, Playback>,
    ) {
        while (true) {
            val current = playbacks.value
            if (playbacks.compareAndSet(current, transform(current))) return
        }
    }

    companion object {
        const val DEFAULT_PARAMETER_RAMP_FRAMES = 64
        const val DEFAULT_STOP_FADE_FRAMES = 64
    }
}

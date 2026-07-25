package dev.anthonyhfm.amethyst.core.engine.audio.command

import dev.anthonyhfm.amethyst.core.engine.audio.voice.AudioVoice
import dev.anthonyhfm.amethyst.core.engine.audio.voice.VoiceId

/** A command applied immediately before [targetFrame] is rendered. */
sealed interface AudioRenderCommand {
    val targetFrame: Long

    data class StartVoice(
        override val targetFrame: Long,
        val voice: AudioVoice,
    ) : AudioRenderCommand

    data class StopVoice(
        override val targetFrame: Long,
        val voiceId: VoiceId,
        val fadeOutFrames: Int,
    ) : AudioRenderCommand

    data class UpdateVoiceMix(
        override val targetFrame: Long,
        val voiceId: VoiceId,
        val gain: Float,
        val pan: Float,
        val rampFrames: Int,
    ) : AudioRenderCommand

    data class StopAll(
        override val targetFrame: Long,
        val fadeOutFrames: Int,
    ) : AudioRenderCommand

    /** Clears voices and generator/effect state on the render thread. */
    data class ResetAll(
        override val targetFrame: Long,
    ) : AudioRenderCommand

    data class SetMasterGain(
        override val targetFrame: Long,
        val gain: Float,
        val rampFrames: Int,
    ) : AudioRenderCommand
}

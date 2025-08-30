package dev.anthonyhfm.amethyst.core.engine.elements

import androidx.compose.ui.graphics.Color

sealed interface Signal {
    val origin: Any?

    data class LED(
        override val origin: Any?,
        val x: Int,
        val y: Int,
        val color: Color,
        val layer: Int = 0,
        val blendingMode: BlendingMode = BlendingMode.Normal
    ) : Signal {
        enum class BlendingMode {
            Normal, Multiply, Add, Mask
        }
    }

    data class Midi(
        override val origin: Any?,
        val x: Int,
        val y: Int,
        val velocity: Int,
    ) : Signal

    data class AudioSignal(
        override val origin: Any?,
        val rawData: ByteArray? = null,  // Raw audio data (PCM samples) - das einzige was zählt
        val sampleRate: Int = 44100,
        val channels: Int = 2,
        val bitDepth: Int = 16,
        val audioKey: String? = null
    ) : Signal {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioSignal) return false
            if (origin != other.origin) return false
            if (rawData != null) {
                if (other.rawData == null) return false
                if (!rawData.contentEquals(other.rawData)) return false
            } else if (other.rawData != null) return false
            if (sampleRate != other.sampleRate) return false
            if (channels != other.channels) return false
            if (bitDepth != other.bitDepth) return false
            if (audioKey != other.audioKey) return false
            return true
        }

        override fun hashCode(): Int {
            var result = origin?.hashCode() ?: 0
            result = 31 * result + (rawData?.contentHashCode() ?: 0)
            result = 31 * result + sampleRate
            result = 31 * result + channels
            result = 31 * result + bitDepth
            result = 31 * result + (audioKey?.hashCode() ?: 0)
            return result
        }
    }
}
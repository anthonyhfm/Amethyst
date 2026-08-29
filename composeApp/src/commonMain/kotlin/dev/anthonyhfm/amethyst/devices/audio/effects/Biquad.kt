package dev.anthonyhfm.amethyst.devices.audio.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.exp

internal enum class BiquadType { LowPass, HighPass, BandPass, Notch }

/** Stereo transposed-direct-form-II biquad with finite coefficient guards. */
internal class StereoBiquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1Left = 0f
    private var z2Left = 0f
    private var z1Right = 0f
    private var z2Right = 0f

    fun configure(type: BiquadType, cutoffHz: Float, q: Float, sampleRate: Int) {
        val cutoff = cutoffHz.takeIf(Float::isFinite)?.coerceIn(10f, sampleRate * 0.49f) ?: 1_000f
        val safeQ = q.takeIf(Float::isFinite)?.coerceIn(0.1f, 24f) ?: 0.7071f
        val omega = (2.0 * PI * cutoff / sampleRate).toFloat()
        val cosine = cos(omega)
        val sine = sin(omega)
        val alpha = sine / (2f * safeQ)
        val a0 = 1f + alpha
        when (type) {
            BiquadType.LowPass -> {
                b0 = (1f - cosine) / 2f / a0
                b1 = (1f - cosine) / a0
                b2 = b0
            }
            BiquadType.HighPass -> {
                b0 = (1f + cosine) / 2f / a0
                b1 = -(1f + cosine) / a0
                b2 = b0
            }
            BiquadType.BandPass -> {
                b0 = alpha / a0
                b1 = 0f
                b2 = -alpha / a0
            }
            BiquadType.Notch -> {
                b0 = 1f / a0
                b1 = -2f * cosine / a0
                b2 = b0
            }
        }
        a1 = (-2f * cosine) / a0
        a2 = (1f - alpha) / a0
        if (!b0.isFinite() || !b1.isFinite() || !b2.isFinite() || !a1.isFinite() || !a2.isFinite()) {
            b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
        }
    }

    fun processLeft(input: Float): Float {
        val output = b0 * input + z1Left
        z1Left = b1 * input - a1 * output + z2Left
        z2Left = b2 * input - a2 * output
        return output.takeIf(Float::isFinite) ?: 0f
    }

    fun processRight(input: Float): Float {
        val output = b0 * input + z1Right
        z1Right = b1 * input - a1 * output + z2Right
        z2Right = b2 * input - a2 * output
        return output.takeIf(Float::isFinite) ?: 0f
    }

    fun reset() {
        z1Left = 0f; z2Left = 0f; z1Right = 0f; z2Right = 0f
    }
}

internal class StereoOnePoleLowPass {
    private var alpha = 0.1f
    private var left = 0f
    private var right = 0f

    fun configure(cutoffHz: Float, sampleRate: Int) {
        val cutoff = cutoffHz.takeIf(Float::isFinite)?.coerceIn(10f, sampleRate * 0.49f) ?: 1_000f
        alpha = (1.0 - exp(-2.0 * PI * cutoff / sampleRate)).toFloat().coerceIn(0f, 1f)
    }

    fun processLeft(input: Float): Float {
        left += alpha * (input - left)
        return left
    }

    fun processRight(input: Float): Float {
        right += alpha * (input - right)
        return right
    }

    fun reset() { left = 0f; right = 0f }
}

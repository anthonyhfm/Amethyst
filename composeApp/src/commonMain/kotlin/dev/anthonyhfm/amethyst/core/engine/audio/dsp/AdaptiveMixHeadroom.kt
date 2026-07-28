package dev.anthonyhfm.amethyst.core.engine.audio.dsp

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Energy-preserving gain stage for dense live mixes.
 *
 * Renderers report the squared nominal gain of every contribution through the
 * render context. A single unity-gain voice is untouched, while dense mixes are
 * attenuated before they can keep the safety limiter in permanent reduction.
 */
class AdaptiveMixHeadroom(
    val attackMillis: Double = DEFAULT_ATTACK_MILLIS,
    val releaseMillis: Double = DEFAULT_RELEASE_MILLIS,
) {
    init {
        require(attackMillis > 0.0)
        require(releaseMillis > 0.0)
    }

    var currentGain: Float = 1f
        private set

    private var attackCoefficient = 0.0
    private var releaseCoefficient = 0.0

    fun prepare(sampleRate: Int) {
        require(sampleRate > 0)
        attackCoefficient = coefficient(sampleRate, attackMillis)
        releaseCoefficient = coefficient(sampleRate, releaseMillis)
        reset()
    }

    fun reset() {
        currentGain = 1f
    }

    fun processInterleaved(
        samples: FloatArray,
        frameCount: Int,
        contributionEnergy: Float,
    ) {
        require(frameCount >= 0 && frameCount * 2 <= samples.size)
        val finiteEnergy = contributionEnergy
            .takeIf { it.isFinite() && it > 1f }
            ?: 1f
        val targetGain = (1.0 / sqrt(finiteEnergy.toDouble())).toFloat()
        var frame = 0
        while (frame < frameCount) {
            val coefficient = if (targetGain < currentGain) {
                attackCoefficient
            } else {
                releaseCoefficient
            }
            currentGain = (
                targetGain + coefficient * (currentGain - targetGain)
            ).toFloat()
            val sampleIndex = frame * 2
            samples[sampleIndex] = finite(samples[sampleIndex]) * currentGain
            samples[sampleIndex + 1] = finite(samples[sampleIndex + 1]) * currentGain
            frame++
        }
    }

    private fun coefficient(sampleRate: Int, milliseconds: Double): Double =
        exp(-1.0 / (sampleRate * milliseconds / 1_000.0))

    private fun finite(value: Float): Float = if (value.isFinite()) value else 0f

    companion object {
        const val DEFAULT_ATTACK_MILLIS = 5.0
        const val DEFAULT_RELEASE_MILLIS = 100.0
    }
}

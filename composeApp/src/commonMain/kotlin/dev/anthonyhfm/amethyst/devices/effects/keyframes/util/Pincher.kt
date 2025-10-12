package dev.anthonyhfm.amethyst.devices.effects.keyframes.util

import kotlin.math.pow

object Pincher {
    private fun basicPinch(actual: Double, value: Double): Double {
        val v = value.coerceIn(0.0, 1.0)
        return 1.0 - (1.0 - v.pow(actual)).pow(1.0 / actual)
    }

    private fun computeActual(clampedPinch: Float): Double = if (clampedPinch < 0f) {
        (((1.0 / (1.0 - clampedPinch)) - 1.0) * 0.9) + 1.0
    } else {
        1.0 + (clampedPinch * 4.0 / 3.0)
    }

    fun mapFraction(fraction: Double, pinch: Float, bilateral: Boolean): Double {
        val clamped = pinch.coerceIn(-2f, 2f)
        val f = fraction.coerceIn(0.0, 1.0)
        val actual = computeActual(clamped)
        return if (bilateral) {
            if (f < 0.5) {
                basicPinch(actual, f * 2.0) / 2.0
            } else {
                1.0 - basicPinch(actual, (1.0 - f) * 2.0) / 2.0
            }
        } else {
            basicPinch(actual, f)
        }
    }

    fun applyPinch(time: Double, total: Double, pinch: Float, bilateral: Boolean): Double {
        if (total <= 0.0) return 0.0
        val fraction = (time / total).coerceIn(0.0, 1.0)
        return (mapFraction(fraction, pinch, bilateral) * total).coerceIn(0.0, total)
    }
}

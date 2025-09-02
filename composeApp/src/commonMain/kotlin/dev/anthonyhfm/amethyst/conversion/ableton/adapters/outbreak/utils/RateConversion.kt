package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun rythmIndexToDuration(timing: String, bpm: Double, steps: Int): Duration {
    val factor = timing.split('/').let { parts ->
        if (parts.size == 2) {
            val num = parts[0].trim().toFloatOrNull()
            val den = parts[1].trim().toFloatOrNull()
            if (num != null && den != null && den != 0f) num / den else 1f / 8f
        } else {
            1f / 8f // Default to 1/8 if unknown
        }
    }

    val fraction = factor * 4
    val secondsPerQuarter = 60.0 / bpm
    println("Converted $timing at $bpm BPM to ${(secondsPerQuarter * fraction * 1000).toInt()} ms")
    return ((secondsPerQuarter * fraction * 1000).toInt() * steps).milliseconds
}
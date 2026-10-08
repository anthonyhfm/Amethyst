package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.isGradient
import dev.anthonyhfm.amethyst.timeline.data.resolvedDeviceIndex
import dev.anthonyhfm.amethyst.timeline.data.resolvedPadIndex

internal fun pianoRollFrameColors(
    notes: List<MidiNote>,
    timeMs: Long,
    clipDurationMs: Long,
): Map<Pair<Int, Int>, Color> {
    if (timeMs < 0L || timeMs >= clipDurationMs) {
        return emptyMap()
    }
    return notes.filter { note ->
        note.startTimeMs <= timeMs && note.endTimeMs > timeMs && note.durationMs > 0L
    }.groupBy { note ->
        note.resolvedDeviceIndex to note.resolvedPadIndex
    }.mapValues { (_, activeNotes) ->
        val colorsByLayer = linkedMapOf<Int, Pair<MidiNote, Color>>()
        activeNotes.sortedBy(selector = MidiNote::startTimeMs).forEach { note ->
            val color = if (note.isGradient) {
                val fraction = ((timeMs - note.startTimeMs).toFloat() / note.durationMs).coerceIn(
                    minimumValue = 0f,
                    maximumValue = 1f,
                )
                val (red, green, blue) = GradientInterpolator.interpolate(
                    stops = note.led.gradient.orEmpty(),
                    t = fraction,
                )
                Color(red = red, green = green, blue = blue)
            } else {
                Color(red = note.led.red, green = note.led.green, blue = note.led.blue)
            }
            if (color.red > 0f || color.green > 0f || color.blue > 0f) {
                colorsByLayer[note.led.layer] = note to color
            } else {
                colorsByLayer.remove(key = note.led.layer)
            }
        }
        val layers = colorsByLayer.entries.sortedByDescending { it.key }.map { it.value }
        var color = Color.Black
        for (index in layers.indices) {
            val (note, noteColor) = layers[index]
            val next = layers.getOrNull(index = index + 1)?.first
            if (note.led.blendingMode != Signal.LED.BlendingMode.Normal &&
                (next == null || note.led.layer - next.led.layer > 200)
            ) {
                continue
            }
            if (note.led.blendingMode == Signal.LED.BlendingMode.Mask) {
                break
            }
            val previous = layers.getOrNull(index = index - 1)?.first
            val multiply = previous?.led?.blendingMode == Signal.LED.BlendingMode.Multiply &&
                previous.led.layer - note.led.layer <= 200
            color = if (multiply) {
                Color(
                    red = color.red * noteColor.red,
                    green = color.green * noteColor.green,
                    blue = color.blue * noteColor.blue,
                )
            } else {
                Color(
                    red = 1f - (1f - color.red) * (1f - noteColor.red),
                    green = 1f - (1f - color.green) * (1f - noteColor.green),
                    blue = 1f - (1f - color.blue) * (1f - noteColor.blue),
                )
            }
            if (note.led.blendingMode == Signal.LED.BlendingMode.Normal) {
                break
            }
        }
        color
    }
}

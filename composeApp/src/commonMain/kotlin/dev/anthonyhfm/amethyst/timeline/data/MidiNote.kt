package dev.anthonyhfm.amethyst.timeline.data

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.serialization.Serializable

/**
 * Represents a single LED note for light shows on launchpad devices.
 * 
 * @property pitch Note position (0-127), for launchpad: x + y*10 within 0-99 range per device
 * @property led LED signal containing color, position, layer, and blending mode
 * @property startTimeMs Start time of the note in milliseconds relative to the track
 * @property durationMs Duration of the note in milliseconds
 */
@Serializable
data class MidiNote(
    val device: Int,
    val pitch: Int,
    val led: NoteLED,
    val startTimeMs: Long,
    val durationMs: Long
) {
    val endTimeMs: Long get() = startTimeMs + durationMs
    
    companion object {
        /**
         * Creates a MidiNote with a simple color (for backwards compatibility and ease of use)
         */
        fun withColor(
            device: Int,
            pitch: Int,
            color: Color,
            startTimeMs: Long,
            durationMs: Long,
            layer: Int = 0,
            blendingMode: Signal.LED.BlendingMode = Signal.LED.BlendingMode.Normal
        ) = MidiNote(
            device = device,
            pitch = pitch,
            led = NoteLED(
                index = pitch,
                red = color.red,
                green = color.green,
                blue = color.blue,
                layer = layer,
                blendingMode = blendingMode
            ),
            startTimeMs = startTimeMs,
            durationMs = durationMs
        )
    }

    @Serializable
    data class NoteLED(
        val index: Int,
        val red: Float,
        val green: Float,
        val blue: Float,
        val layer: Int = 0,
        val blendingMode: Signal.LED.BlendingMode = Signal.LED.BlendingMode.Normal
    )
}

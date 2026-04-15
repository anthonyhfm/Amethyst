package dev.anthonyhfm.amethyst.timeline.data

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientSmoothness
import kotlinx.serialization.Serializable

@Serializable
data class NoteGradientStop(
    val position: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val smoothness: GradientSmoothness = GradientSmoothness.Linear,
    val selectionUUID: String = UUID.randomUUID()
)

val MidiNote.isGradient: Boolean get() = led.gradient != null && (led.gradient?.size ?: 0) >= 2

fun MidiNote.isOutOfBounds(clipDurationMs: Long): Boolean = startTimeMs < 0 || startTimeMs >= clipDurationMs

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
        fun withPaint(
            device: Int,
            pitch: Int,
            color: Color,
            startTimeMs: Long,
            durationMs: Long,
            gradient: List<NoteGradientStop>? = null,
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
                blendingMode = blendingMode,
                gradient = gradient?.takeIf { it.size >= 2 }
            ),
            startTimeMs = startTimeMs,
            durationMs = durationMs
        )

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
        ) = withPaint(
            device = device,
            pitch = pitch,
            color = color,
            startTimeMs = startTimeMs,
            durationMs = durationMs,
            layer = layer,
            blendingMode = blendingMode
        )
    }

    @Serializable
    data class NoteLED(
        val index: Int,
        val red: Float,
        val green: Float,
        val blue: Float,
        val layer: Int = 0,
        val blendingMode: Signal.LED.BlendingMode = Signal.LED.BlendingMode.Normal,
        val gradient: List<NoteGradientStop>? = null
    )
}

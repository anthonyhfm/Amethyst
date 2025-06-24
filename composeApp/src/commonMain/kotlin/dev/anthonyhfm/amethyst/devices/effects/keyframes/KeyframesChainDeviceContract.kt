package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.Serializable

sealed interface KeyframesChainDeviceContract {
    sealed interface Event {
        data class OnPaintButton(val x: Int, val y: Int, val offset: Offset) : Event
        data class OnColorUpdate(val color: Color) : Event
        data class OnSelectFrame(val frameIndex: Int) : Event
        data class OnAddFrame(val atIndex: Int? = null) : Event
        data class OnChangeFrameTiming(val frameIndex: Int, val timing: Timing) : Event
        data class OnChangeFramePosition(
            val from: Int,
            val to: Int
        ) : Event
    }

    @Serializable
    data class KeyframesChainDeviceState(
        val selectedColor: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
        val selectedFrameIndex: Int = 0,
        val frames: List<Frame> = listOf(
            Frame(
                timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
            )
        )
    ) : DeviceState()

    @Serializable
    data class Frame(
        val timing: Timing,
        val entries: List<KeyframesEntry> = emptyList(),
        val _internalUuid: String = UUID.randomUUID()
    )

    @Serializable
    data class KeyframesEntry(
        val x: Int,
        val y: Int,
        val r: Float,
        val g: Float,
        val b: Float
    )
}
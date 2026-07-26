package dev.anthonyhfm.amethyst.devices.effects.copy

import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.Serializable

@Serializable
data class CopyChainDeviceState(
    val mode: CopyMode = CopyMode.STATIC,
    val gridMode: GridMode = GridMode.NONE,
    val wrap: Boolean = false,
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val gate: Float = 0.5f, // Apollo 100% = 0.5f
    val pinch: Float = 0f,
    val bilateral: Boolean = false,
    val reverse: Boolean = false,
    val infinite: Boolean = false,
    val isolate: IsolationType = IsolationType.NONE,
    val offsets: List<Offset> = emptyList(),
) : DeviceState() {
    enum class IsolationType {
        NONE,
        EDGELESS,
        FULL
    }

    enum class CopyMode {
        STATIC,
        ANIMATE,
        INTERPOLATE,
        HOLD_INTERPOLATE,
        RANDOM_SINGLE,
        RANDOM_LOOP
    }

    enum class GridMode {
        NONE,
        EDGELESS,
        FULL,
    }

    @Serializable
    data class Offset(
        val x: Int,
        val y: Int,
        val isAbsolute: Boolean = false,
        val absoluteX: Int = 0,
        val absoluteY: Int = 0,
        val angle: Int = 0
    )
}

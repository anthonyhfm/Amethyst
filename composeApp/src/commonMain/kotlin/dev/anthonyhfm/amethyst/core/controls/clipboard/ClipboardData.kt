package dev.anthonyhfm.amethyst.core.controls.clipboard

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame

sealed interface ClipboardData {
    data class ChainDevice(
        val states: List<DeviceState>
    ) : ClipboardData

    data class GradientStep(
        val step: Selectable.GradientStep
    ) : ClipboardData

    data class Keyframe(
        val frames: List<Frame>
    ) : ClipboardData
}
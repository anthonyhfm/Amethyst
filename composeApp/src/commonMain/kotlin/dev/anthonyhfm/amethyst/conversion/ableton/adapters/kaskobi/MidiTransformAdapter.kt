package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxParameter
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDeviceState

class MidiTransformAdapter(
    private val device: MxDevice,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> = when (mode(device)) {
        1 -> listOf(
            RotateChainDeviceState(
                mode = RotateChainDeviceState.RotateMode.DEGREES_270,
                angleDegrees = 270f,
                isolate = true,
            )
        )
        2 -> listOf(
            RotateChainDeviceState(
                mode = RotateChainDeviceState.RotateMode.DEGREES_90,
                angleDegrees = 90f,
                isolate = true,
            )
        )
        3 -> listOf(
            RotateChainDeviceState(
                mode = RotateChainDeviceState.RotateMode.DEGREES_180,
                angleDegrees = 180f,
                isolate = true,
            )
        )
        4 -> listOf(
            FlipChainDeviceState(
                mode = FlipChainDeviceState.FlipMode.HORIZONTAL,
                isolate = true,
            )
        )
        5 -> listOf(
            FlipChainDeviceState(
                mode = FlipChainDeviceState.FlipMode.VERTICAL,
                isolate = true,
            )
        )
        else -> emptyList()
    }

    companion object {
        internal fun mode(device: MxDevice): Int =
            (device.parameterList.parameterList.parameters
                .firstOrNull { it.index == 0 } as? MxParameter.MxDEnumParameter)
                ?.timeable?.manual?.value ?: 0
    }
}

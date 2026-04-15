package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.conversion.apollo.utils.toTiming
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.copy.CopyChainDeviceState

class ApolloCopyAdapter(
    model: ApolloModel.Device.Copy
) : ApolloAdapter<ApolloModel.Device.Copy>(model) {
    override fun toDeviceState(): DeviceState {
        return CopyChainDeviceState(
            mode = when (model.mode) {
                0 -> CopyChainDeviceState.CopyMode.STATIC
                1 -> CopyChainDeviceState.CopyMode.ANIMATE
                2 -> CopyChainDeviceState.CopyMode.INTERPOLATE
                3 -> CopyChainDeviceState.CopyMode.RANDOM_SINGLE
                4 -> CopyChainDeviceState.CopyMode.RANDOM_LOOP
                else -> CopyChainDeviceState.CopyMode.STATIC
            },
            gridMode = when (model.gridMode) {
                0 -> CopyChainDeviceState.GridMode.NONE
                1 -> CopyChainDeviceState.GridMode.EDGELESS
                2 -> CopyChainDeviceState.GridMode.FULL
                else -> CopyChainDeviceState.GridMode.NONE
            },
            wrap = model.wrap,
            timing = model.time.toTiming(),
            gate = model.gate.toFloat() / 2f,
            pinch = model.pinch.toFloat(),
            bilateral = model.bilateral,
            reverse = model.reverse,
            infinite = model.infinite,
            isolate = CopyChainDeviceState.IsolationType.NONE,
            offsets = model.offsets.mapIndexed { index, it ->
                CopyChainDeviceState.Offset(
                    x = it.x,
                    y = it.y,
                    isAbsolute = it.isAbsolute,
                    absoluteX = it.absoluteX,
                    absoluteY = it.absoluteY,
                    angle = model.angles.getOrElse(index) { 0 }
                )
            }
        )
    }
}

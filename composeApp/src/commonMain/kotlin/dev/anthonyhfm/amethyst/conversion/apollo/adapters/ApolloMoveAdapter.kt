package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState

class ApolloMoveAdapter(
    model: ApolloModel.Device.Move
) : ApolloAdapter<ApolloModel.Device.Move>(model) {
    override fun toDeviceState(): DeviceState = OffsetChainDeviceState(
        offsetX = model.offset.x,
        offsetY = model.offset.y,
        isAbsolute = model.offset.isAbsolute,
        absoluteX = model.offset.absoluteX,
        absoluteY = 9 - model.offset.absoluteY,
        gridMode = when (model.gridMode) {
            1 -> OffsetChainDeviceState.GridMode.EDGELESS
            else -> OffsetChainDeviceState.GridMode.FULL
        },
        wrap = model.wrap,
        apolloMove = true,
    )
}

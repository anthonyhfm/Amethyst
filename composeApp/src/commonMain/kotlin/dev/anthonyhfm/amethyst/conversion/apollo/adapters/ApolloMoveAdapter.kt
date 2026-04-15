package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState

class ApolloMoveAdapter(
    model: ApolloModel.Device.Move
) : ApolloAdapter<ApolloModel.Device.Move>(model) {
    override fun toDeviceState(): DeviceState {
        val (x, y) = if (model.offset.isAbsolute) {
            model.offset.absoluteX to model.offset.absoluteY
        } else {
            model.offset.x to model.offset.y
        }
        return OffsetChainDeviceState(
            offsetX = x,
            offsetY = y,
            gridMode = OffsetChainDeviceState.GridMode.entries.getOrElse(model.gridMode) { OffsetChainDeviceState.GridMode.NONE },
            wrap = model.wrap,
        )
    }
}

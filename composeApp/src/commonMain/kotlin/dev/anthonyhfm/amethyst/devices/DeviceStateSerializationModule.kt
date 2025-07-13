package dev.anthonyhfm.amethyst.devices

import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.copy.CopyChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.layer_filter.LayerFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDeviceState
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val DeviceStateSerializationModule = SerializersModule {
    polymorphic(DeviceState::class) {
        subclass(ColorChainDeviceState::class)
        subclass(CoordinateFilterChainDeviceState::class)
        subclass(CopyChainDeviceState::class)
        subclass(DelayChainDeviceState::class)
        subclass(FlipChainDeviceState::class)
        subclass(GradientChainDeviceState::class)
        subclass(GroupChainDeviceState::class)
        subclass(HoldChainDeviceState::class)
        subclass(KeyframesChainDeviceContract.KeyframesChainDeviceState::class)
        subclass(LayerChainDeviceState::class)
        subclass(LayerFilterChainDeviceState::class)
        subclass(LoopChainDeviceState::class)
        subclass(OffsetChainDeviceState::class)
        subclass(RotateChainDeviceState::class)
        subclass(ClipChainDeviceState::class)
        subclass(MacroFilterChainDeviceState::class)
        subclass(SwitchChainDeviceState::class)
    }
}

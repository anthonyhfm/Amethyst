package dev.anthonyhfm.amethyst.devices

import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.blur.BlurChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.color_filter.ColorFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.copy.CopyChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.preview.PreviewChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.layer_filter.LayerFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.shift.ShiftChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.adjust.AdjustChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.opacity.OpacityChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.clear.ClearChainDeviceState
import dev.anthonyhfm.amethyst.gem.host.GemDeviceState
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val DeviceSerializationModule = SerializersModule {
    polymorphic(DeviceState::class) {
        subclass(ColorChainDeviceState::class)
        subclass(CoordinateFilterChainDeviceState::class)
        subclass(CopyChainDeviceState::class)
        subclass(DelayChainDeviceState::class)
        subclass(FlipChainDeviceState::class)
        subclass(GradientChainDeviceState::class)
        subclass(GroupChainDeviceState::class)
        subclass(MultiGroupChainDeviceState::class)
        subclass(ChokeChainDeviceState::class)
        subclass(HoldChainDeviceState::class)
        subclass(KeyframesChainDeviceContract.KeyframesChainDeviceState::class)
        subclass(PianoRollChainDeviceState::class)
        subclass(LayerChainDeviceState::class)
        subclass(LayerFilterChainDeviceState::class)
        subclass(LoopChainDeviceState::class)
        subclass(OffsetChainDeviceState::class)
        subclass(RotateChainDeviceState::class)
        subclass(SampleChainDeviceState::class)
        subclass(MacroFilterChainDeviceState::class)
        subclass(MacroControlChainDeviceState::class)
        subclass(BlurChainDeviceState::class)
        subclass(OpacityChainDeviceState::class)
        subclass(PreviewChainDeviceState::class)
        subclass(ShiftChainDeviceState::class)
        subclass(AdjustChainDeviceState::class)
        subclass(TransmitChainDeviceState::class)
        subclass(ColorFilterChainDeviceState::class)
        subclass(GemDeviceState::class)
        
        subclass(ClearChainDeviceState::class)
    }
}

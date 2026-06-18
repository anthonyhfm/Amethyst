package dev.anthonyhfm.amethyst.devices

import dev.anthonyhfm.amethyst.devices.ableton.AbletonArpeggiatorChainDevice
import dev.anthonyhfm.amethyst.devices.ableton.AbletonPitcherChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.effects.adjust.AdjustChainDevice
import dev.anthonyhfm.amethyst.devices.effects.blur.BlurChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.clear.ClearChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDevice
import dev.anthonyhfm.amethyst.devices.effects.color_filter.ColorFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.copy.CopyChainDevice
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDevice
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDevice
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDevice
import dev.anthonyhfm.amethyst.devices.effects.layer_filter.LayerFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDevice
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDevice
import dev.anthonyhfm.amethyst.devices.effects.opacity.OpacityChainDevice
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDevice
import dev.anthonyhfm.amethyst.devices.effects.preview.PreviewChainDevice
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDevice
import dev.anthonyhfm.amethyst.devices.effects.shift.ShiftChainDevice
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDevice
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDevice
import kotlin.reflect.KClass

object DeviceRegistry {
    private val _factories = mutableMapOf<KClass<*>, ChainDeviceFactory<*>>()
    val factories: Map<KClass<*>, ChainDeviceFactory<*>> get() = _factories

    init {
        register(AdjustChainDevice)
        register(BlurChainDevice)
        register(ChokeChainDevice)
        register(ClearChainDevice)
        register(CompositionChainDevice)
        register(ColorChainDevice)
        register(ColorFilterChainDevice)
        register(CoordinateFilterChainDevice)
        register(CopyChainDevice)
        register(DelayChainDevice)
        register(FlipChainDevice)
        register(GradientChainDevice)
        register(GroupChainDevice)
        register(HoldChainDevice)
        register(KeyframesChainDevice)
        register(LayerChainDevice)
        register(LayerFilterChainDevice)
        register(LoopChainDevice)
        register(MacroControlChainDevice)
        register(MacroFilterChainDevice)
        register(MultiGroupChainDevice)
        register(OffsetChainDevice)
        register(OpacityChainDevice)
        register(PianoRollChainDevice)
        register(PreviewChainDevice)
        register(RotateChainDevice)
        register(SampleChainDevice)
        register(ShiftChainDevice)
        register(TransmitChainDevice)

        register(AbletonArpeggiatorChainDevice)
        register(AbletonPitcherChainDevice)
    }

    fun register(factory: ChainDeviceFactory<*>) {
        _factories[factory.stateClass] = factory
    }

    @Suppress("UNCHECKED_CAST")
    fun pack(device: GenericChainDevice<*>): DeviceState {
        val factory = _factories[device.state.value::class] as? ChainDeviceFactory<DeviceState>
            ?: error("No factory registered for device state: ${device.state.value::class.simpleName}")
        return factory.pack(device as GenericChainDevice<DeviceState>)
    }

    @Suppress("UNCHECKED_CAST")
    fun unpack(state: DeviceState): GenericChainDevice<*> {
        val factory = _factories[state::class] as? ChainDeviceFactory<DeviceState>
            ?: error("No factory registered for device state: ${state::class.simpleName}")
        return factory.unpack(state)
    }

    fun createFromState(state: DeviceState): GenericChainDevice<*> = unpack(state)
}

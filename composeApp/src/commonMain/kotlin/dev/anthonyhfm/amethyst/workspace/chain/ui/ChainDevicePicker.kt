package dev.anthonyhfm.amethyst.workspace.chain.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.material.icons.twotone.Adjust
import androidx.compose.material.icons.twotone.AudioFile
import androidx.compose.material.icons.twotone.BlurOn
import androidx.compose.material.icons.twotone.Opacity
import androidx.compose.material.icons.twotone.ColorLens
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.Filter
import androidx.compose.material.icons.twotone.FilterTiltShift
import androidx.compose.material.icons.twotone.Flip
import androidx.compose.material.icons.twotone.Gradient
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.LayersClear
import androidx.compose.material.icons.twotone.LineAxis
import androidx.compose.material.icons.twotone.Loop
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.Piano
import androidx.compose.material.icons.twotone.RotateLeft
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.ShapeLine
import androidx.compose.material.icons.twotone.StopCircle
import androidx.compose.material.icons.twotone.Timeline
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Transform
import androidx.compose.material.icons.twotone._123
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.effects.blur.BlurChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.opacity.OpacityChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDevice
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
import dev.anthonyhfm.amethyst.devices.effects.mask.MaskChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDevice
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDevice
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDevice
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDevice
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material.icons.twotone.Diamond
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Contrast
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Preview
import dev.anthonyhfm.amethyst.devices.effects.color_filter.ColorFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.preview.PreviewChainDevice
import dev.anthonyhfm.amethyst.devices.effects.shift.ShiftChainDevice
import dev.anthonyhfm.amethyst.devices.effects.adjust.AdjustChainDevice
import dev.anthonyhfm.amethyst.devices.effects.clear.ClearChainDevice
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDevice
import kotlin.reflect.KClass

@Composable
fun ChainDevicePicker(
    visible: Boolean,
    sampling: Boolean,
    onPickComponent: (GenericChainDevice<*>) -> Unit,
    onDismiss: () -> Unit,
    isDeviceTypeEnabled: (KClass<out GenericChainDevice<*>>) -> Boolean = { true },
) {
    @Composable
    fun pickerItem(
        label: String,
        icon: ImageVector,
        type: KClass<out GenericChainDevice<*>>,
        create: () -> GenericChainDevice<*>,
    ) {
        val enabled = isDeviceTypeEnabled(type)
        ChainContextMenuItem(
            label = label,
            icon = icon,
            enabled = enabled,
            onClick = { if (enabled) onPickComponent(create()) },
        )
    }
    NavigableChainContextMenu(
        expanded = visible,
        onDismissRequest = onDismiss
    ) { onNavigate, _, level ->
        @Composable
        fun submenuItem(
            label: String,
            icon: ImageVector,
            destination: String,
            vararg types: KClass<out GenericChainDevice<*>>,
        ) {
            val enabled = types.any(isDeviceTypeEnabled)
            ChainContextMenuSubmenuItem(
                label = label,
                icon = icon,
                enabled = enabled,
                onClick = { if (enabled) onNavigate(destination) },
            )
        }

        if (!sampling) {
            // Lights Menu
            when (level) {
                "main" -> {
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_container), Icons.TwoTone.Group, "container", GroupChainDevice::class, ChokeChainDevice::class, MultiGroupChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_filter), Icons.TwoTone.Filter, "filter", CoordinateFilterChainDevice::class, LayerFilterChainDevice::class, MacroFilterChainDevice::class, ColorFilterChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_color), Icons.TwoTone.ColorLens, "color", ColorChainDevice::class, GradientChainDevice::class, ShiftChainDevice::class, AdjustChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_shape), Icons.TwoTone.ShapeLine, "shape", CopyChainDevice::class, CompositionChainDevice::class, KeyframesChainDevice::class, PianoRollChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_timing), Icons.TwoTone.Timer, "timing", DelayChainDevice::class, HoldChainDevice::class, LoopChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_transform), Icons.TwoTone.Transform, "transform", OffsetChainDevice::class, LayerChainDevice::class, FlipChainDevice::class, RotateChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_effects), Icons.TwoTone.Science, "effects", BlurChainDevice::class, MaskChainDevice::class, OpacityChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_misc), Icons.TwoTone.Adjust, "misc", ClearChainDevice::class, MacroControlChainDevice::class, PreviewChainDevice::class, TransmitChainDevice::class)
                }
                "container" -> {
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_group), Icons.TwoTone.Group, GroupChainDevice::class, ::GroupChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_choke), Icons.TwoTone.StopCircle, ChokeChainDevice::class, ::ChokeChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_multi), Icons.TwoTone._123, MultiGroupChainDevice::class, ::MultiGroupChainDevice)
                }
                "filter" -> {
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_coordinate_filter), Icons.TwoTone.MyLocation, CoordinateFilterChainDevice::class, ::CoordinateFilterChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_layer_filter), Icons.TwoTone.Layers, LayerFilterChainDevice::class, ::LayerFilterChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_macro_filter), Icons.TwoTone.FilterTiltShift, MacroFilterChainDevice::class, ::MacroFilterChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_color_filter), Icons.TwoTone.ColorLens, ColorFilterChainDevice::class, ::ColorFilterChainDevice)
                }
                "color" -> {
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_color), Icons.TwoTone.ColorLens, ColorChainDevice::class, ::ColorChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_gradient), Icons.TwoTone.Gradient, GradientChainDevice::class, ::GradientChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_shift), Icons.TwoTone.Contrast, ShiftChainDevice::class, ::ShiftChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_adjust), Icons.TwoTone.Tune, AdjustChainDevice::class, ::AdjustChainDevice)
                }
                "shape" -> {
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_copy), Icons.TwoTone.ContentCopy, CopyChainDevice::class, ::CopyChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_composition), Icons.TwoTone.Diamond, CompositionChainDevice::class, ::CompositionChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_keyframes), Icons.TwoTone.Timeline, KeyframesChainDevice::class, ::KeyframesChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_piano_roll), Icons.TwoTone.Piano, PianoRollChainDevice::class, ::PianoRollChainDevice)
                }
                "timing" -> {
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_delay), Icons.TwoTone.Timer, DelayChainDevice::class, ::DelayChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_hold), Icons.TwoTone.Pause, HoldChainDevice::class, ::HoldChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_loop), Icons.TwoTone.Loop, LoopChainDevice::class, ::LoopChainDevice)
                }
                "transform" -> {
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_offset), Icons.TwoTone.LineAxis, OffsetChainDevice::class, ::OffsetChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_layer), Icons.TwoTone.Layers, LayerChainDevice::class, ::LayerChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_flip), Icons.TwoTone.Flip, FlipChainDevice::class, ::FlipChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_rotate), Icons.TwoTone.RotateLeft, RotateChainDevice::class, ::RotateChainDevice)
                }
                "effects" -> {
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_blur), Icons.TwoTone.BlurOn, BlurChainDevice::class, ::BlurChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_mask), Icons.TwoTone.Layers, MaskChainDevice::class, ::MaskChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_opacity), Icons.TwoTone.Opacity, OpacityChainDevice::class, ::OpacityChainDevice)
                }
                "misc" -> {
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_clear), Icons.TwoTone.LayersClear, ClearChainDevice::class, ::ClearChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_macro_control), Icons.TwoTone.Adjust, MacroControlChainDevice::class, ::MacroControlChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_preview), Icons.TwoTone.Preview, PreviewChainDevice::class, ::PreviewChainDevice)
                    pickerItem(stringResource(Res.string.workspace_chain_devicepicker_transmit), Icons.AutoMirrored.TwoTone.Send, TransmitChainDevice::class, ::TransmitChainDevice)
                }
            }
        } else {
            // Sampling Menu
            when (level) {
                "main" -> {
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_container), Icons.TwoTone.Group, "container", GroupChainDevice::class, MultiGroupChainDevice::class)
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_sample), icon = Icons.TwoTone.AudioFile, onClick = { onPickComponent(SampleChainDevice()) })
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_filter), Icons.TwoTone.Filter, "filter", CoordinateFilterChainDevice::class, MacroFilterChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_timing), Icons.TwoTone.Timer, "timing", DelayChainDevice::class, HoldChainDevice::class, LoopChainDevice::class)
                    submenuItem(stringResource(Res.string.workspace_chain_devicepicker_misc), Icons.TwoTone.Adjust, "misc", ClearChainDevice::class, MacroControlChainDevice::class)
                }
                "container" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_group), icon = Icons.TwoTone.Group, onClick = { onPickComponent(GroupChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_multi), icon = Icons.TwoTone._123, onClick = { onPickComponent(MultiGroupChainDevice()) })
                }
                "filter" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_coordinate_filter), icon = Icons.TwoTone.MyLocation, onClick = { onPickComponent(CoordinateFilterChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_macro_filter), icon = Icons.TwoTone.FilterTiltShift, onClick = { onPickComponent(MacroFilterChainDevice()) })
                }
                "timing" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_delay), icon = Icons.TwoTone.Timer, onClick = { onPickComponent(DelayChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_hold), icon = Icons.TwoTone.Pause, onClick = { onPickComponent(HoldChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_loop), icon = Icons.TwoTone.Loop, onClick = { onPickComponent(LoopChainDevice()) })
                }
                "misc" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_clear), icon = Icons.TwoTone.LayersClear, onClick = { onPickComponent(ClearChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_macro_control), icon = Icons.TwoTone.Adjust, onClick = { onPickComponent(MacroControlChainDevice()) })
                }
            }
        }
    }
}

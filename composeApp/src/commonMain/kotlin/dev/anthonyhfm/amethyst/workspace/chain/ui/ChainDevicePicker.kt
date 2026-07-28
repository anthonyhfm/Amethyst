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

@Composable
fun ChainDevicePicker(
    visible: Boolean,
    sampling: Boolean,
    onPickComponent: (GenericChainDevice<*>) -> Unit,
    onDismiss: () -> Unit
) {
    NavigableChainContextMenu(
        expanded = visible,
        onDismissRequest = onDismiss
    ) { onNavigate, _, level ->
        if (!sampling) {
            // Lights Menu
            when (level) {
                "main" -> {
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_container), icon = Icons.TwoTone.Group, onClick = { onNavigate("container") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_filter), icon = Icons.TwoTone.Filter, onClick = { onNavigate("filter") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_color), icon = Icons.TwoTone.ColorLens, onClick = { onNavigate("color") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_shape), icon = Icons.TwoTone.ShapeLine, onClick = { onNavigate("shape") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_timing), icon = Icons.TwoTone.Timer, onClick = { onNavigate("timing") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_transform), icon = Icons.TwoTone.Transform, onClick = { onNavigate("transform") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_effects), icon = Icons.TwoTone.Science, onClick = { onNavigate("effects") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_misc), icon = Icons.TwoTone.Adjust, onClick = { onNavigate("misc") })
                }
                "container" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_group), icon = Icons.TwoTone.Group, onClick = { onPickComponent(GroupChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_choke), icon = Icons.TwoTone.StopCircle, onClick = { onPickComponent(ChokeChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_multi), icon = Icons.TwoTone._123, onClick = { onPickComponent(MultiGroupChainDevice()) })
                }
                "filter" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_coordinate_filter), icon = Icons.TwoTone.MyLocation, onClick = { onPickComponent(CoordinateFilterChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_layer_filter), icon = Icons.TwoTone.Layers, onClick = { onPickComponent(LayerFilterChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_macro_filter), icon = Icons.TwoTone.FilterTiltShift, onClick = { onPickComponent(MacroFilterChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_color_filter), icon = Icons.TwoTone.ColorLens, onClick = { onPickComponent(ColorFilterChainDevice()) })
                }
                "color" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_color), icon = Icons.TwoTone.ColorLens, onClick = { onPickComponent(ColorChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_gradient), icon = Icons.TwoTone.Gradient, onClick = { onPickComponent(GradientChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_shift), icon = Icons.TwoTone.Contrast, onClick = { onPickComponent(ShiftChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_adjust), icon = Icons.TwoTone.Tune, onClick = { onPickComponent(AdjustChainDevice()) })
                }
                "shape" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_copy), icon = Icons.TwoTone.ContentCopy, onClick = { onPickComponent(CopyChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_composition), icon = Icons.TwoTone.Diamond, onClick = { onPickComponent(CompositionChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_keyframes), icon = Icons.TwoTone.Timeline, onClick = { onPickComponent(KeyframesChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_piano_roll), icon = Icons.TwoTone.Piano, onClick = { onPickComponent(PianoRollChainDevice()) })
                }
                "timing" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_delay), icon = Icons.TwoTone.Timer, onClick = { onPickComponent(DelayChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_hold), icon = Icons.TwoTone.Pause, onClick = { onPickComponent(HoldChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_loop), icon = Icons.TwoTone.Loop, onClick = { onPickComponent(LoopChainDevice()) })
                }
                "transform" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_offset), icon = Icons.TwoTone.LineAxis, onClick = { onPickComponent(OffsetChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_layer), icon = Icons.TwoTone.Layers, onClick = { onPickComponent(LayerChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_flip), icon = Icons.TwoTone.Flip, onClick = { onPickComponent(FlipChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_rotate), icon = Icons.TwoTone.RotateLeft, onClick = { onPickComponent(RotateChainDevice()) })
                }
                "effects" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_blur), icon = Icons.TwoTone.BlurOn, onClick = { onPickComponent(BlurChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_opacity), icon = Icons.TwoTone.Opacity, onClick = { onPickComponent(OpacityChainDevice()) })
                }
                "misc" -> {
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_clear), icon = Icons.TwoTone.LayersClear, onClick = { onPickComponent(ClearChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_macro_control), icon = Icons.TwoTone.Adjust, onClick = { onPickComponent(MacroControlChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_preview), icon = Icons.TwoTone.Preview, onClick = { onPickComponent(PreviewChainDevice()) })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_transmit), icon = Icons.AutoMirrored.TwoTone.Send, onClick = { onPickComponent(TransmitChainDevice()) })
                }
            }
        } else {
            // Sampling Menu
            when (level) {
                "main" -> {
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_container), icon = Icons.TwoTone.Group, onClick = { onNavigate("container") })
                    ChainContextMenuItem(stringResource(Res.string.workspace_chain_devicepicker_sample), icon = Icons.TwoTone.AudioFile, onClick = { onPickComponent(SampleChainDevice()) })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_filter), icon = Icons.TwoTone.Filter, onClick = { onNavigate("filter") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_timing), icon = Icons.TwoTone.Timer, onClick = { onNavigate("timing") })
                    ChainContextMenuSubmenuItem(stringResource(Res.string.workspace_chain_devicepicker_misc), icon = Icons.TwoTone.Adjust, onClick = { onNavigate("misc") })
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

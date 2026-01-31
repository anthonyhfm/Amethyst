package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Adjust
import androidx.compose.material.icons.twotone.AudioFile
import androidx.compose.material.icons.twotone.Audiotrack
import androidx.compose.material.icons.twotone.BlurOn
import androidx.compose.material.icons.twotone.ColorLens
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.DataArray
import androidx.compose.material.icons.twotone.Filter
import androidx.compose.material.icons.twotone.FilterTiltShift
import androidx.compose.material.icons.twotone.Flip
import androidx.compose.material.icons.twotone.Functions
import androidx.compose.material.icons.twotone.Gradient
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.LineAxis
import androidx.compose.material.icons.twotone.Loop
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.Piano
import androidx.compose.material.icons.twotone.RotateLeft
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.ShapeLine
import androidx.compose.material.icons.twotone.Stop
import androidx.compose.material.icons.twotone.StopCircle
import androidx.compose.material.icons.twotone.Timeline
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Transform
import androidx.compose.material.icons.twotone._123
import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDevice
import dev.anthonyhfm.amethyst.devices.effects.blur.BlurChainDevice
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
import dev.anthonyhfm.amethyst.devices.effects.switch.SwitchChainDevice
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.AmethystContextMenu
import dev.anthonyhfm.amethyst.ui.components.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.ContextMenuHeader
import dev.anthonyhfm.amethyst.ui.components.ContextMenuSubmenuItem

@Composable
fun ChainDevicePicker(
    visible: Boolean,
    sampling: Boolean,
    onPickComponent: (GenericChainDevice<*>) -> Unit,
    onDismiss: () -> Unit
) {
    val registry = mapOf<String, GenericChainDevice<*>>(
        "device_color" to ColorChainDevice(),
        "device_coordinate_filter" to CoordinateFilterChainDevice(),
        "device_delay" to DelayChainDevice(),
        "device_gradient" to GradientChainDevice(),
        "device_group" to GroupChainDevice(),
        "device_choke" to ChokeChainDevice(),
        "device_multi" to MultiGroupChainDevice(),
        "device_keyframes" to KeyframesChainDevice(),
        "device_pianoroll" to PianoRollChainDevice(),
        "device_layer" to LayerChainDevice(),
        "device_layer_filter" to LayerFilterChainDevice(),
        "device_offset" to OffsetChainDevice(),
        "device_hold" to HoldChainDevice(),
        "device_loop" to LoopChainDevice(),
        "device_flip" to FlipChainDevice(),
        "device_rotate" to RotateChainDevice(),
        "device_copy" to CopyChainDevice(),
        "device_macro_filter" to MacroFilterChainDevice(),
        "device_clip" to ClipChainDevice(),
        "device_switch" to SwitchChainDevice(),
        "device_blur" to BlurChainDevice(),
    )

    AmethystContextMenu(
        expanded = visible,
        onDismissRequest = onDismiss
    ) { onNavigate, _, level ->
        if (!sampling) {
            // Lights Menu
            when (level) {
                "main" -> {
                    ContextMenuSubmenuItem("Container", icon = Icons.TwoTone.Group, onClick = { onNavigate("container") })
                    ContextMenuSubmenuItem("Filter", icon = Icons.TwoTone.Filter, onClick = { onNavigate("filter") })
                    ContextMenuSubmenuItem("Color", icon = Icons.TwoTone.ColorLens, onClick = { onNavigate("color") })
                    ContextMenuSubmenuItem("Shape", icon = Icons.TwoTone.ShapeLine, onClick = { onNavigate("shape") })
                    ContextMenuSubmenuItem("Timing", icon = Icons.TwoTone.Timer, onClick = { onNavigate("timing") })
                    ContextMenuSubmenuItem("Transform", icon = Icons.TwoTone.Transform, onClick = { onNavigate("transform") })
                    ContextMenuSubmenuItem("Fx", icon = Icons.TwoTone.Science, onClick = { onNavigate("fx") })
                    ContextMenuItem("Switch", icon = Icons.TwoTone.Adjust, onClick = { onPickComponent(SwitchChainDevice()); onDismiss() })
                }
                "container" -> {
                    ContextMenuHeader("Container")
                    ContextMenuItem("Group", icon = Icons.TwoTone.Group, onClick = { onPickComponent(GroupChainDevice()); onDismiss() })
                    ContextMenuItem("Choke", icon = Icons.TwoTone.StopCircle, onClick = { onPickComponent(ChokeChainDevice()); onDismiss() })
                    ContextMenuItem("Multi", icon = Icons.TwoTone._123, onClick = { onPickComponent(MultiGroupChainDevice()); onDismiss() })
                }
                "filter" -> {
                    ContextMenuHeader("Filter")
                    ContextMenuItem("Coordinate Filter", icon = Icons.TwoTone.MyLocation, onClick = { onPickComponent(CoordinateFilterChainDevice()); onDismiss() })
                    ContextMenuItem("Layer Filter", icon = Icons.TwoTone.Layers, onClick = { onPickComponent(LayerFilterChainDevice()); onDismiss() })
                    ContextMenuItem("Macro Filter", icon = Icons.TwoTone.FilterTiltShift, onClick = { onPickComponent(MacroFilterChainDevice()); onDismiss() })
                }
                "color" -> {
                    ContextMenuHeader("Color")
                    ContextMenuItem("Color", icon = Icons.TwoTone.ColorLens, onClick = { onPickComponent(ColorChainDevice()); onDismiss() })
                    ContextMenuItem("Gradient", icon = Icons.TwoTone.Gradient, onClick = { onPickComponent(GradientChainDevice()); onDismiss() })
                }
                "shape" -> {
                    ContextMenuHeader("Shape")
                    ContextMenuItem("Copy", icon = Icons.TwoTone.ContentCopy, onClick = { onPickComponent(CopyChainDevice()); onDismiss() })
                    ContextMenuItem("Keyframes", icon = Icons.TwoTone.Timeline, onClick = { onPickComponent(KeyframesChainDevice()); onDismiss() })
                    ContextMenuItem("Piano Roll", icon = Icons.TwoTone.Piano, onClick = { onPickComponent(PianoRollChainDevice()); onDismiss() })
                }
                "timing" -> {
                    ContextMenuHeader("Timing")
                    ContextMenuItem("Delay", icon = Icons.TwoTone.Timer, onClick = { onPickComponent(DelayChainDevice()); onDismiss() })
                    ContextMenuItem("Hold", icon = Icons.TwoTone.Pause, onClick = { onPickComponent(HoldChainDevice()); onDismiss() })
                    ContextMenuItem("Loop", icon = Icons.TwoTone.Loop, onClick = { onPickComponent(LoopChainDevice()); onDismiss() })
                }
                "transform" -> {
                    ContextMenuHeader("Transform")
                    ContextMenuItem("Offset", icon = Icons.TwoTone.LineAxis, onClick = { onPickComponent(OffsetChainDevice()); onDismiss() })
                    ContextMenuItem("Layer", icon = Icons.TwoTone.Layers, onClick = { onPickComponent(LayerChainDevice()); onDismiss() })
                    ContextMenuItem("Flip", icon = Icons.TwoTone.Flip, onClick = { onPickComponent(FlipChainDevice()); onDismiss() })
                    ContextMenuItem("Rotate", icon = Icons.TwoTone.RotateLeft, onClick = { onPickComponent(RotateChainDevice()); onDismiss() })
                }
                "fx" -> {
                    ContextMenuHeader("Fx")
                    ContextMenuItem("Blur", icon = Icons.TwoTone.BlurOn, onClick = { onPickComponent(BlurChainDevice()); onDismiss() })
                }
            }
        } else {
            // Sampling Menu
            when (level) {
                "main" -> {
                    ContextMenuSubmenuItem("Container", icon = Icons.TwoTone.Group, onClick = { onNavigate("container") })
                    ContextMenuItem("Clip", icon = Icons.TwoTone.AudioFile, onClick = { onPickComponent(ClipChainDevice()); onDismiss() })
                    ContextMenuSubmenuItem("Filter", icon = Icons.TwoTone.Filter, onClick = { onNavigate("filter") })
                    ContextMenuSubmenuItem("Timing", icon = Icons.TwoTone.Timer, onClick = { onNavigate("timing") })
                    ContextMenuItem("Switch", icon = Icons.TwoTone.Adjust, onClick = { onPickComponent(SwitchChainDevice()); onDismiss() })
                }
                "container" -> {
                    ContextMenuHeader("Container")
                    ContextMenuItem("Group", icon = Icons.TwoTone.Group, onClick = { onPickComponent(GroupChainDevice()); onDismiss() })
                    ContextMenuItem("Multi", icon = Icons.TwoTone._123, onClick = { onPickComponent(MultiGroupChainDevice()); onDismiss() })
                }
                "filter" -> {
                    ContextMenuHeader("Filter")
                    ContextMenuItem("Coordinate Filter", icon = Icons.TwoTone.MyLocation, onClick = { onPickComponent(CoordinateFilterChainDevice()); onDismiss() })
                    ContextMenuItem("Macro Filter", icon = Icons.TwoTone.FilterTiltShift, onClick = { onPickComponent(MacroFilterChainDevice()); onDismiss() })
                }
                "timing" -> {
                    ContextMenuHeader("Timing")
                    ContextMenuItem("Delay", icon = Icons.TwoTone.Timer, onClick = { onPickComponent(DelayChainDevice()); onDismiss() })
                    ContextMenuItem("Hold", icon = Icons.TwoTone.Pause, onClick = { onPickComponent(HoldChainDevice()); onDismiss() })
                    ContextMenuItem("Loop", icon = Icons.TwoTone.Loop, onClick = { onPickComponent(LoopChainDevice()); onDismiss() })
                }
            }
        }
    }
}

private fun getLightsMenu() {}
private fun getSamplingMenu() {}
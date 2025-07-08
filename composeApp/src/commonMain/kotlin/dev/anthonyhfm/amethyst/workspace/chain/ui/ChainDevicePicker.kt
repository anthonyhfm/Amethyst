package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.twotone.AudioFile
import androidx.compose.material.icons.twotone.Audiotrack
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.ColorLens
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.DataArray
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.FileCopy
import androidx.compose.material.icons.twotone.Filter
import androidx.compose.material.icons.twotone.Flip
import androidx.compose.material.icons.twotone.Gradient
import androidx.compose.material.icons.twotone.Grid3x3
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.LineAxis
import androidx.compose.material.icons.twotone.Loop
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.RotateLeft
import androidx.compose.material.icons.twotone.ShapeLine
import androidx.compose.material.icons.twotone.Timeline
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Transform
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDevice
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
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDevice
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDevice
import io.androidpoet.dropdown.Dropdown
import io.androidpoet.dropdown.Easing
import io.androidpoet.dropdown.EnterAnimation
import io.androidpoet.dropdown.ExitAnimation
import io.androidpoet.dropdown.MenuItem
import io.androidpoet.dropdown.dropDownMenu
import io.androidpoet.dropdown.dropDownMenuColors

fun getLightsMenu(): MenuItem<String> {
    return dropDownMenu {
        item("cat_container", "Container") {
            icon(Icons.TwoTone.DataArray)

            item("device_group", "Group") {
                icon(Icons.TwoTone.Group)
            }
        }
        item("cat_filter", "Filter") {
            icon(Icons.TwoTone.Filter)

            item("device_coordinate_filter", "Coordinate Filter") {
                icon(Icons.TwoTone.MyLocation)
            }

            item("device_layer_filter", "Layer Filter") {
                icon(Icons.TwoTone.Layers)
            }
        }
        item("cat_color", "Color") {
            icon(Icons.TwoTone.ColorLens)

            item("device_color", "Color") {
                icon(Icons.TwoTone.ColorLens)
            }

            item("device_gradient", "Gradient") {
                icon(Icons.TwoTone.Gradient)
            }
        }
        item("cat_shape", "Shape") {
            icon(Icons.TwoTone.ShapeLine)

            item("device_offset", "Offset") {
                icon(Icons.TwoTone.LineAxis)
            }

            item("device_copy", "Copy") {
                icon(Icons.TwoTone.ContentCopy)
            }

            item("device_keyframes", "Keyframes") {
                icon(Icons.TwoTone.Timeline)
            }
        }
        item("cat_timing", "Timing") {
            icon(Icons.TwoTone.Timer)

            item("device_delay", "Delay") {
                icon(Icons.TwoTone.Timeline)
            }

            item("device_hold", "Hold") {
                icon(Icons.TwoTone.Pause)
            }

            item("device_loop", "Loop") {
                icon(Icons.TwoTone.Loop)
            }
        }
        item("cat_transform", "Transform") {
            icon(Icons.TwoTone.Transform)

            item("device_layer", "Layer") {
                icon(Icons.TwoTone.Layers)
            }

            item("device_flip", "Flip") {
                icon(Icons.TwoTone.Flip)
            }

            item("device_rotate", "Rotate") {
                icon(Icons.TwoTone.RotateLeft)
            }
        }
    }
}

fun getSamplingMenu(): MenuItem<String> {
    return dropDownMenu {
        item("cat_audio", "Audio") {
            icon(Icons.TwoTone.Audiotrack)

            item("device_clip", "Clip") {
                icon(Icons.TwoTone.AudioFile)
            }
        }

        item("cat_container", "Container") {
            icon(Icons.TwoTone.DataArray)

            item("device_group", "Group") {
                icon(Icons.TwoTone.Group)
            }
        }
        item("cat_filter", "Filter") {
            icon(Icons.TwoTone.Filter)

            item("device_coordinate_filter", "Coordinate Filter") {
                icon(Icons.TwoTone.MyLocation)
            }
        }
        item("cat_shape", "Shape") {
            icon(Icons.TwoTone.ShapeLine)

            item("device_offset", "Offset") {
                icon(Icons.TwoTone.LineAxis)
            }

            item("device_copy", "Copy") {
                icon(Icons.TwoTone.ContentCopy)
            }
        }
        item("cat_timing", "Timing") {
            icon(Icons.TwoTone.Timer)

            item("device_delay", "Delay") {
                icon(Icons.TwoTone.Timeline)
            }

            item("device_hold", "Hold") {
                icon(Icons.TwoTone.Pause)
            }

            item("device_loop", "Loop") {
                icon(Icons.TwoTone.Loop)
            }
        }
    }
}

@Composable
fun ChainDevicePicker(
    visible: Boolean,
    sampling: Boolean,
    onPickComponent: (ChainDevice<*>) -> Unit,
    onDismiss: () -> Unit
) {
    val menu = if (!sampling) getLightsMenu() else getSamplingMenu()

    val registry = mapOf<String, ChainDevice<*>>(
        "device_color" to ColorChainDevice(),
        "device_coordinate_filter" to CoordinateFilterChainDevice(),
        "device_delay" to DelayChainDevice(),
        "device_gradient" to GradientChainDevice(),
        "device_group" to GroupChainDevice(sampling = sampling),
        "device_keyframes" to KeyframesChainDevice(),
        "device_layer" to LayerChainDevice(),
        "device_layer_filter" to LayerFilterChainDevice(),
        "device_offset" to OffsetChainDevice(),
        "device_hold" to HoldChainDevice(),
        "device_loop" to LoopChainDevice(),
        "device_flip" to FlipChainDevice(),
        "device_rotate" to RotateChainDevice(),
        "device_copy" to CopyChainDevice(),
        "device_clip" to ClipChainDevice(),
    )

    Dropdown(
        isOpen = visible,
        menu = menu,
        onItemSelected = {
            onDismiss()

            registry[it]?.let { device ->
                onPickComponent(device)
            }
        },
        onDismiss = {
            onDismiss()
        },
        enter = EnterAnimation.SharedAxisXForward,
        exit = ExitAnimation.SharedAxisXBackward,
        easing = Easing.FastOutSlowInEasing,
        enterDuration = 400,
        exitDuration = 400
    )
}
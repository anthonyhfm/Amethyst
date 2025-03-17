package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDevice
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDevice

data class PickableComponent(
    val name: String,
    val icon: ImageVector,
    val plugin: ChainDevice<*>
)

@Composable
fun ChainDevicePicker(
    visible: Boolean,
    onPickComponent: (ChainDevice<*>) -> Unit,
    onDismiss: () -> Unit
) {
    val pickableComponents: Array<PickableComponent> = arrayOf(
        PickableComponent(
            name = "Coordinate Filter",
            icon = Icons.Default.Filter,
            plugin = CoordinateFilterChainDevice()
        ),
        PickableComponent(
            name = "Color",
            icon = Icons.Default.ColorLens,
            plugin = ColorChainDevice()
        ),
        PickableComponent(
            name = "Gradient",
            icon = Icons.Default.Gradient,
            plugin = GradientChainDevice()
        ),
        PickableComponent(
            name = "Group",
            icon = Icons.Default.Link,
            plugin = GroupChainDevice()
        ),
        PickableComponent(
            name = "Offset",
            icon = Icons.Default.OpenWith,
            plugin = OffsetChainDevice()
        ),
        PickableComponent(
            name = "Delay",
            icon = Icons.Default.MoreTime,
            plugin = DelayChainDevice()
        ),
    )

    DropdownMenu(
        expanded = visible,
        onDismissRequest = {
            onDismiss()
        }
    ) {
        pickableComponents.forEach {
            DropdownMenuItem(
                text = {
                    Text(it.name)
                },
                leadingIcon = {
                    Icon(
                        imageVector = it.icon,
                        contentDescription = null
                    )
                },
                onClick = {
                    onDismiss()

                    onPickComponent(it.plugin)
                }
            )
        }
    }
}
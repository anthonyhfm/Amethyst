package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Cyclone
import androidx.compose.material.icons.twotone.Flip
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.RotateLeft
import androidx.compose.material.icons.twotone.SettingsEthernet
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Transform
import androidx.compose.material.icons.twotone.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.CompositionNodeDefinition
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.CompositionNodePickerCategory
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.PinchNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.RotateNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.ScannerNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.WaterdropNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.SpiralNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.MirrorNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.SymmetryNode
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainContextMenuItem
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainContextMenuSubmenuItem
import dev.anthonyhfm.amethyst.workspace.chain.ui.NavigableChainContextMenu

@androidx.compose.runtime.Composable
fun CompositionNodePicker(
    visible: Boolean,
    offset: DpOffset,
    selectedNode: CompositionNode?,
    onDismiss: () -> Unit,
    onPickNode: (String) -> Unit,
    onDeleteSelected: () -> Unit,
) {
    NavigableChainContextMenu(
        expanded = visible,
        onDismissRequest = onDismiss,
        offset = offset,
    ) { onNavigate, _, level ->
        when (level) {
            "main" -> {
                CompositionNodePickerCategory.entries.forEach { category ->
                    ChainContextMenuSubmenuItem(
                        label = category.label,
                        icon = categoryIcon(category),
                        onClick = { onNavigate(category.name) },
                    )
                }
                if (selectedNode != null && NodeRegistry.definitionFor(selectedNode)?.isOutput != true) {
                    ContextMenuSeparator()
                    ChainContextMenuItem(
                        label = "Delete ${selectedNode.label}",
                        icon = Icons.TwoTone.Delete,
                        variant = ContextMenuItemVariant.Destructive,
                        onClick = onDeleteSelected,
                    )
                }
            }
            else -> CompositionNodePickerCategory.entries
                .firstOrNull { it.name == level }
                ?.let { category ->
                    NodeRegistry.pickerDefinitions(category).forEach { definition ->
                        ChainContextMenuItem(
                            label = definition.label,
                            icon = nodeIcon(definition),
                            onClick = { onPickNode(definition.type) },
                        )
                    }
                }
        }
    }
}

private fun categoryIcon(category: CompositionNodePickerCategory): ImageVector = when (category) {
    CompositionNodePickerCategory.Generators -> Icons.TwoTone.SettingsEthernet
    CompositionNodePickerCategory.Transform -> Icons.TwoTone.Transform
    CompositionNodePickerCategory.Time -> Icons.TwoTone.Timer
}

private fun nodeIcon(definition: CompositionNodeDefinition): ImageVector = when (definition.type) {
    ScannerNode.type -> Icons.TwoTone.SettingsEthernet
    WaterdropNode.type -> Icons.TwoTone.WaterDrop
    SpiralNode.type -> Icons.TwoTone.Cyclone
    RotateNode.type -> Icons.TwoTone.RotateLeft
    MirrorNode.type -> Icons.TwoTone.Flip
    SymmetryNode.type -> Icons.TwoTone.GridView
    PinchNode.type -> Icons.TwoTone.Timer
    else -> categoryIcon(requireNotNull(definition.pickerCategory))
}

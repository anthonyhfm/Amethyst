package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import com.composables.icons.lucide.Cable
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Move
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.Trash2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.CompositionNodePickerCategory
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainContextMenuItem
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainContextMenuSubmenuItem
import dev.anthonyhfm.amethyst.workspace.chain.ui.NavigableChainContextMenu

@Composable
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
                        icon = Lucide.Trash2,
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
                            icon = definition.icon,
                            onClick = { onPickNode(definition.type) },
                        )
                    }
                }
        }
    }
}

private fun categoryIcon(category: CompositionNodePickerCategory): ImageVector = when (category) {
    CompositionNodePickerCategory.Generators -> Lucide.Cable
    CompositionNodePickerCategory.Transform -> Lucide.Move
    CompositionNodePickerCategory.Color -> Lucide.Palette
    CompositionNodePickerCategory.Time -> Lucide.Timer
}

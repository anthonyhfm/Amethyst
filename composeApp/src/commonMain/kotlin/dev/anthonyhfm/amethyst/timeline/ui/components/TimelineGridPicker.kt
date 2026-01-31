package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.ui.components.AmethystContextMenu
import dev.anthonyhfm.amethyst.ui.components.ContextMenuItem

@Composable
fun TimelineGridPicker() {
    val current by WorkspaceRepository.gridType.collectAsState()
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f), CircleShape)
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.TwoTone.GridView,
                contentDescription = "Grid Type",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    AmethystContextMenu(
        expanded = open,
        onDismissRequest = { open = false }
    ) { _, _, _ ->
        // Flexible
        ContextMenuItem(label = "Flexible: Smallest", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Smallest); open = false })
        ContextMenuItem(label = "Flexible: Small", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Small); open = false })
        ContextMenuItem(label = "Flexible: Medium", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Medium); open = false })
        ContextMenuItem(label = "Flexible: Large", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Large); open = false })
        ContextMenuItem(label = "Flexible: Largest", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Largest); open = false })

        // Fixed
        ContextMenuItem(label = "Fixed: 1 Bar", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed.Bar_1); open = false })
        ContextMenuItem(label = "Fixed: 2 Bars", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed.Bar_2); open = false })
        ContextMenuItem(label = "Fixed: 4 Bars", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed.Bar_4); open = false })
        ContextMenuItem(label = "Fixed: 8 Bars", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed.Bar_8); open = false })
        ContextMenuItem(label = "Fixed: 1/2 Bar", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_2); open = false })
        ContextMenuItem(label = "Fixed: 1/4 Bar", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_4); open = false })
        ContextMenuItem(label = "Fixed: 1/8 Bar", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_8); open = false })
        ContextMenuItem(label = "Fixed: 1/16 Bar", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_16); open = false })
        ContextMenuItem(label = "Fixed: 1/32 Bar", onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_32); open = false })
    }
}
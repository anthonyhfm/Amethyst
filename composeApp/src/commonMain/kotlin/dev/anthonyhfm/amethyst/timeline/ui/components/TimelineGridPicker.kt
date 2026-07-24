package dev.anthonyhfm.amethyst.timeline.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.utils.displayLabel
import dev.anthonyhfm.amethyst.ui.components.AmethystContextMenu
import dev.anthonyhfm.amethyst.ui.components.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceToolbarIconButton
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceToolbarSurface

@Composable
fun TimelineGridPicker() {
    val current by WorkspaceRepository.gridType.collectAsState()
    var open by remember { mutableStateOf(false) }

    WorkspaceToolbarSurface {
        WorkspaceToolbarIconButton(
            onClick = { open = true },
            imageVector = Icons.TwoTone.GridView,
            contentDescription = stringResource(Res.string.timeline_grid_picker_description),
        )

        Separator(
            modifier = Modifier.height(20.dp),
            orientation = SeparatorOrientation.Vertical,
        )

        val interactionSource = remember { MutableInteractionSource() }
        val hovered by interactionSource.collectIsHoveredAsState()
        val contentColor = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]

        UnstyledButton(
            onClick = { open = true },
            shape = SmallShape,
            interactionSource = interactionSource,
            indication = null,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier
                .clip(SmallShape)
                .background(if (hovered) Theme[colors][accent] else Color.Transparent),
        ) {
            Text(
                text = current.displayLabel,
                style = Theme[typography][small].copy(color = contentColor),
            )
        }
    }

    AmethystContextMenu(
        expanded = open,
        onDismissRequest = { open = false },
    ) { _, _, _ ->
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_no_grid), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.NoGrid); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_flex_smallest), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Smallest); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_flex_small), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Small); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_flex_medium), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Medium); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_flex_large), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Large); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_flex_largest), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Flexible.Largest); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_1_bar), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed.Bar_1); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_2_bars), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed.Bar_2); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_4_bars), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed.Bar_4); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_8_bars), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed.Bar_8); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_half_bar), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_2); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_quarter_bar), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_4); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_eighth_bar), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_8); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_sixteenth_bar), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_16); open = false })
        ContextMenuItem(label = stringResource(Res.string.timeline_grid_picker_fixed_thirty_second_bar), onClick = { WorkspaceRepository.setGridType(GridUtils.GridType.Fixed._1_32); open = false })
    }
}

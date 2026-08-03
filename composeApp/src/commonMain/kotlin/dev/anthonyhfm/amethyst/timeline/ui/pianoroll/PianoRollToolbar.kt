package dev.anthonyhfm.amethyst.timeline.ui.pianoroll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorTool
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun PianoRollToolbar(
    activeTool: TimelineEditorTool,
    onToolChange: (TimelineEditorTool) -> Unit,
    gridResolution: GridResolution,
    gridResolutionLocked: Boolean,
    onGridResolutionChange: (GridResolution) -> Unit,
    onToggleGridLock: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomFit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tool Selection
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                icon = Lucide.MousePointer,
                label = "Select",
                selected = activeTool == TimelineEditorTool.SELECT,
                onClick = { onToolChange(TimelineEditorTool.SELECT) }
            )
            ToolButton(
                icon = Lucide.Pencil,
                label = "Draw",
                selected = activeTool == TimelineEditorTool.DRAW,
                onClick = { onToolChange(TimelineEditorTool.DRAW) }
            )
            ToolButton(
                icon = Lucide.Eraser,
                label = "Erase",
                selected = activeTool == TimelineEditorTool.ERASE,
                onClick = { onToolChange(TimelineEditorTool.ERASE) }
            )
        }

        // Grid & Zoom Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grid lock / snap toggle
            Button(
                onClick = onToggleGridLock,
                variant = if (gridResolutionLocked) ButtonVariant.Secondary else ButtonVariant.Ghost,
                size = ButtonSize.Small
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (gridResolutionLocked) Lucide.Lock else Lucide.LockKeyholeOpen,
                        contentDescription = "Snap Lock",
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        gridResolution.label,
                        style = Theme[typography][small]
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Theme[colors][border])
            )

            // Zoom buttons
            Button(
                onClick = onZoomOut,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Icon
            ) {
                Icon(
                    imageVector = Lucide.ZoomOut,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(14.dp)
                )
            }

            Button(
                onClick = onZoomIn,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Icon
            ) {
                Icon(
                    imageVector = Lucide.ZoomIn,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(14.dp)
                )
            }

            Button(
                onClick = onZoomFit,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Icon
            ) {
                Icon(
                    imageVector = Lucide.Maximize2,
                    contentDescription = "Fit Content",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        variant = if (selected) ButtonVariant.Secondary else ButtonVariant.Ghost,
        size = ButtonSize.Small
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(14.dp)
            )
            Text(label, style = Theme[typography][small])
        }
    }
}

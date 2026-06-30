package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.AudioLines
import com.composables.icons.lucide.ChartNoAxesGantt
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composeunstyled.UnstyledButton
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.PerformanceWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LayoutWorkspaceMode

@Composable
fun WorkspaceMode(
    mode: WorkspaceMode,
) {
    val selectableModes = listOf(
        WorkspaceModePickerItem(
            key = "performance",
            mode = PerformanceWorkspaceMode(),
            text = "Performance",
            icon = Lucide.Play,
        ),
        WorkspaceModePickerItem(
            key = "timeline",
            mode = TimelineWorkspaceMode(),
            text = "Timeline",
            icon = Lucide.ChartNoAxesGantt,
        ),
        WorkspaceModePickerItem(
            key = "lights-chain",
            mode = LightsChainWorkspaceMode(),
            text = "Lights",
            icon = Lucide.Lightbulb,
        ),
        WorkspaceModePickerItem(
            key = "sampling-chain",
            mode = SamplingChainWorkspaceMode(),
            text = "Sampling",
            icon = Lucide.AudioLines,
        ),
        WorkspaceModePickerItem(
            key = "layout",
            mode = LayoutWorkspaceMode(),
            text = "Layout",
            icon = Lucide.LayoutGrid,
        ),
    )

    val selectedMode = selectableModes.firstOrNull { modeMatches(mode, it.mode) }

    if (!mode.selectableMode) {
        val variant = ButtonVariant.Destructive
        Button(
            onClick = { WorkspaceRepository.switchToPreviousMode() },
            variant = variant,
            size = ButtonSize.Small,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = mode.displayName,
                tint = workspaceToolbarContentColor(variant),
            )
            Text(mode.displayName)
        }
        return
    }

    Tabs(
        selectedTab = selectedMode?.key ?: selectableModes.first().key,
        tabs = selectableModes.map { it.key },
    ) {
        TabsList(
            modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                !keyEvent.isAltPressed && keyEvent.key in setOf(
                    Key.DirectionLeft,
                    Key.DirectionRight,
                    Key.DirectionUp,
                    Key.DirectionDown,
                )
            },
        ) {
            selectableModes.forEach { item ->
                WorkspaceModeTabButton(
                    item = item,
                    selected = selectedMode?.key == item.key,
                    onClick = { WorkspaceRepository.switchMode(item.mode) },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceModeTabButton(
    item: WorkspaceModePickerItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = Theme[typography][small]
    val labelWidth = remember(item.text, labelStyle) {
        textMeasurer.measure(
            text = AnnotatedString(item.text),
            style = labelStyle,
        ).size.width
    }
    val labelWidthDp = with(density) { labelWidth.toDp() }

    val animatedLabelWidth by animateDpAsState(
        targetValue = if (selected) labelWidthDp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "workspace-mode-label-width",
    )
    val animatedGap by animateDpAsState(
        targetValue = if (selected) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "workspace-mode-label-gap",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "workspace-mode-label-alpha",
    )

    val backgroundColor = when {
        selected -> Theme[colors][background]
        hovered -> Theme[colors][accent]
        else -> Color.Transparent
    }
    val contentColor = when {
        selected -> Theme[colors][foreground]
        hovered -> Theme[colors][accentForeground]
        else -> Theme[colors][mutedForeground]
    }

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        shape = SmallShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
            .clip(SmallShape)
            .background(backgroundColor),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.text,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(animatedGap))
            Box(
                modifier = Modifier.width(animatedLabelWidth),
                contentAlignment = Alignment.CenterStart,
            ) {
                MaterialText(
                    text = item.text,
                    style = labelStyle,
                    color = contentColor,
                    modifier = Modifier.alpha(labelAlpha),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun modeMatches(
    currentMode: WorkspaceMode,
    candidate: WorkspaceMode,
): Boolean {
    return when {
        currentMode is LayoutWorkspaceMode && candidate is LayoutWorkspaceMode -> true
        currentMode is PerformanceWorkspaceMode && candidate is PerformanceWorkspaceMode -> true
        currentMode is LightsChainWorkspaceMode && candidate is LightsChainWorkspaceMode -> true
        currentMode is SamplingChainWorkspaceMode && candidate is SamplingChainWorkspaceMode -> true
        currentMode is TimelineWorkspaceMode && candidate is TimelineWorkspaceMode -> true
        else -> false
    }
}

data class WorkspaceModePickerItem(
    val key: String,
    val mode: WorkspaceMode,
    val text: String,
    val icon: ImageVector,
)

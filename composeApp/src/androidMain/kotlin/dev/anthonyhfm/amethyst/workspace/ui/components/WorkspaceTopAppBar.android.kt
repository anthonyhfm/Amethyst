package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.AudioLines
import com.composables.icons.lucide.ChartNoAxesGantt
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.X
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.settings.SettingsDialog
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.PerformanceWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LayoutWorkspaceMode

import org.jetbrains.compose.resources.StringResource

private data class WorkspaceModeEntry(
    val mode: WorkspaceMode,
    val labelRes: StringResource,
    val icon: ImageVector,
) {
    val label: String @Composable get() = stringResource(labelRes)
}

private val selectableModes = listOf(
    WorkspaceModeEntry(PerformanceWorkspaceMode(), Res.string.workspace_topappbar_mode_performance, Lucide.Play),
    WorkspaceModeEntry(TimelineWorkspaceMode(), Res.string.workspace_topappbar_mode_timeline, Lucide.ChartNoAxesGantt),
    WorkspaceModeEntry(LightsChainWorkspaceMode(), Res.string.workspace_topappbar_mode_lights, Lucide.Lightbulb),
    WorkspaceModeEntry(SamplingChainWorkspaceMode(), Res.string.workspace_topappbar_mode_sampling, Lucide.AudioLines),
    WorkspaceModeEntry(LayoutWorkspaceMode(), Res.string.workspace_topappbar_mode_layout, Lucide.LayoutGrid),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun WorkspaceTopAppBar(
    onBack: () -> Unit,
    mode: WorkspaceMode,
) {
    val automappingState by AutomappingManager.state.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }

    val currentEntry = selectableModes.firstOrNull { modeMatches(mode, it.mode) }

    CenterAlignedTopAppBar(
        navigationIcon = {
            if (mode.selectableMode) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Lucide.ChevronLeft,
                        contentDescription = stringResource(Res.string.workspace_topappbar_back_to_home),
                    )
                }
            } else {
                IconButton(onClick = { WorkspaceRepository.switchToPreviousMode() }) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Close ${mode.displayName}",
                    )
                }
            }
        },
        title = {
            if (mode.selectableMode) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showModePicker = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = currentEntry?.label ?: mode.displayName,
                            style = MaterialTheme.typography.titleMedium,
                        )

                        Icon(
                            imageVector = Lucide.ChevronDown,
                            contentDescription = stringResource(Res.string.workspace_topappbar_switch_mode),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showModePicker,
                        onDismissRequest = { showModePicker = false },
                    ) {
                        selectableModes.forEach { entry ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = entry.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                text = { Text(entry.label) },
                                onClick = {
                                    WorkspaceRepository.switchMode(entry.mode)
                                    showModePicker = false
                                },
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        actions = {
            if (automappingState.isActive) {
                Text(
                    text = stringResource(Res.string.workspace_topappbar_auto),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = { showSettingsDialog = true }) {
                Icon(
                    imageVector = Lucide.Settings,
                    contentDescription = stringResource(Res.string.workspace_topappbar_open_settings),
                )
            }
        },
    )

    SettingsDialog(
        visible = showSettingsDialog,
        onDismiss = { showSettingsDialog = false },
    )
}

private fun modeMatches(
    current: WorkspaceMode,
    candidate: WorkspaceMode,
): Boolean = when {
    current is PerformanceWorkspaceMode && candidate is PerformanceWorkspaceMode -> true
    current is TimelineWorkspaceMode && candidate is TimelineWorkspaceMode -> true
    current is LightsChainWorkspaceMode && candidate is LightsChainWorkspaceMode -> true
    current is SamplingChainWorkspaceMode && candidate is SamplingChainWorkspaceMode -> true
    current is LayoutWorkspaceMode && candidate is LayoutWorkspaceMode -> true
    else -> false
}

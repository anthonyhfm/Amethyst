package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Anchor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.settings.SettingsDialog
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelinePlaybackControls
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelineGridPicker
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons
import dev.anthonyhfm.amethyst.ui.icons.outlined.Metronome
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.components.AmethystCheckbox
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceTopAppBar(
    onBack: () -> Unit,
    mode: WorkspaceContract.WorkspaceMode,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(16.dp)
            .fillMaxWidth(),

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (platform !is Platform.Desktop) {
            BackButton(
                onBack = onBack
            )
        }

        WorkspaceMode(mode)

        Spacer(Modifier.weight(1f))

        if (platform is Platform.Desktop) {
            if (mode is WorkspaceContract.WorkspaceMode.Timeline) {
                TimelinePlaybackControls()
                TimelineGridPicker()
            }

            if (mode is KeyframesWorkspaceMode) {
                KeyframesOptions(mode)
            }

            BPMChanger()

            CleanupButtons()
        }

        FilledIconButton(
            onClick = {
                showSettingsDialog = true
            }
        ) {
            Icon(Icons.Default.Settings, null)
        }
    }

    // Settings dialog - platform implementations handle visibility appropriately
    SettingsDialog(
        visible = showSettingsDialog,
        onDismiss = { showSettingsDialog = false }
    )
}

@Composable
fun KeyframesOptions(mode: KeyframesWorkspaceMode) {
    val state by mode.state.collectAsState()
    var pickingRootKey by remember { mutableStateOf(false) }

    DisposableEffect(pickingRootKey) {
        if (pickingRootKey) {
            val originalOnPaint = mode.onEvent
            val originalOnDragStart = mode.onVirtualDeviceDragStart

            mode.onEvent = { event ->
                if (event is KeyframesChainDeviceContract.Event.OnPaintButton) {
                    val key = event.y * 10 + event.x
                    originalOnPaint?.invoke(KeyframesChainDeviceContract.Event.OnChangeRootKey(key))
                    pickingRootKey = false
                } else {
                    originalOnPaint?.invoke(event)
                }
            }

            mode.onVirtualDeviceDragStart = { x, y ->
                val key = y * 10 + x
                mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeRootKey(key))
                pickingRootKey = false
            }

            onDispose {
                mode.onEvent = originalOnPaint
                mode.onVirtualDeviceDragStart = originalOnDragStart
            }
        } else {
            onDispose {}
        }
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), CircleShape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val rootKeyText = when {
            pickingRootKey -> "Press Pad..."
            state.rootKey != null -> {
                val x = state.rootKey!! % 10
                val y = state.rootKey!! / 10
                "Root: X$x Y$y"
            }
            else -> "Set Root Key"
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .fillMaxHeight()
                .background(
                    if (pickingRootKey) MaterialTheme.colorScheme.primaryContainer
                    else if (state.rootKey != null) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent
                )
                .clickable {
                    if (state.rootKey != null && !pickingRootKey) {
                        mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeRootKey(null))
                    } else {
                        pickingRootKey = !pickingRootKey
                    }
                }
                .rightClickable {
                    mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeRootKey(null))
                    pickingRootKey = false
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Anchor,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (pickingRootKey) MaterialTheme.colorScheme.onPrimaryContainer
                else if (state.rootKey != null) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = rootKeyText,
                style = MaterialTheme.typography.labelLarge,
                color = if (pickingRootKey) MaterialTheme.colorScheme.onPrimaryContainer
                else if (state.rootKey != null) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }

        VerticalDivider(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        // Wrap Toggle
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .fillMaxHeight()
                .clickable {
                    mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeWrap(!state.wrap))
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Wrap",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            AmethystCheckbox(
                checked = state.wrap,
                onCheckedChange = {
                    mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeWrap(it))
                }
            )
        }

        VerticalDivider(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .fillMaxHeight()
                .clickable {
                    mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeIsolate(!state.isolate))
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Isolate",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            AmethystCheckbox(
                checked = state.isolate,
                onCheckedChange = {
                    mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeIsolate(it))
                }
            )
        }
    }
}

@Composable
fun BackButton(
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .size(44.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), CircleShape)
            .clickable {
                onBack()
            }
            .padding(2.dp),

        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.ArrowBackIosNew,
            contentDescription = "Back button directing to home screen",
            modifier = Modifier
                .clip(CircleShape)
                .padding(12.dp)
                .size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
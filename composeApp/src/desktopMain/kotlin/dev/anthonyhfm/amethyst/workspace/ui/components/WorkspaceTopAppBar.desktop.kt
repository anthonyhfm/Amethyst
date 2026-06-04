package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Anchor
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MousePointer
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.rememberDialogState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.network.CollaborationManager
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectionState
import dev.anthonyhfm.amethyst.core.network.user.LocalUserRepository
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.settings.SettingsDialog
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorTool
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelineGridPicker
import dev.anthonyhfm.amethyst.timeline.ui.components.TimelinePlaybackControls
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenuContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenuRadioItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Input
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.selectionForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.launch

@Composable
actual fun WorkspaceTopAppBar(
    onBack: () -> Unit,
    mode: WorkspaceContract.WorkspaceMode,
    onEvent: (WorkspaceContract.Event) -> Unit,
) {
    val automappingState by AutomappingManager.state.collectAsState()
    val collaborationState by CollaborationManager.connectionState.collectAsState()
    val session by CollaborationManager.session.collectAsState()
    val connectLocalUser by CollaborationManager.localUser.collectAsState()
    val localUser by LocalUserRepository.localUser.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    val shareDialogState = rememberDialogState()
    val scope = rememberCoroutineScope()
    val isHosting = CollaborationManager.isHosting
    val isConnected = collaborationState is ConnectionState.Connected
    val isConnecting = collaborationState is ConnectionState.Connecting

    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkspaceMode(mode)

        if (automappingState.isActive) {
            AutomappingStatusIndicator(
                armed = true,
            )
        }

        Spacer(Modifier.weight(1f))

        if (mode is PianoRollWorkspaceMode) {
            PianoRollOptions(mode)
        }

        WorkspaceToolbarSlideFromTopControls(
            visible = mode is WorkspaceContract.WorkspaceMode.Timeline,
        ) {
            TimelinePlaybackControls()
            TimelineGridPicker()
        }

        if (mode is KeyframesWorkspaceMode) {
            KeyframesOptions(mode)
        }

        BPMChanger()
        CleanupButtons()

        WorkspaceToolbarSurface(contentPadding = PaddingValues(4.dp)) {
            if (collaborationState is ConnectionState.Connected) {
                UserRoster(
                    (session?.participants.orEmpty() + listOfNotNull(connectLocalUser))
                        .distinctBy { it.id }
                )
                Separator(
                    modifier = Modifier.height(20.dp),
                    orientation = SeparatorOrientation.Vertical,
                )
            }

            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Theme[colors][accent],
                )
            } else {
                WorkspaceToolbarIconButton(
                    onClick = {
                        if (isHosting) {
                            scope.launch { CollaborationManager.leaveSession() }
                        } else if (isConnected) {
                            scope.launch { CollaborationManager.leaveSession() }
                        } else {
                            shareDialogState.visible = true
                        }
                    },
                    imageVector = Icons.Default.Share,
                    contentDescription = when {
                        isHosting -> "Stop sharing"
                        isConnected -> "Leave shared workspace"
                        else -> "Share workspace"
                    },
                    variant = when {
                        isHosting -> ButtonVariant.Default
                        isConnected -> ButtonVariant.Secondary
                        else -> ButtonVariant.Ghost
                    },
                    modifier = if (isHosting) Modifier.scale(1.04f) else Modifier,
                )
            }

            WorkspaceToolbarIconButton(
                onClick = { showSettingsDialog = true },
                imageVector = Lucide.Settings,
                contentDescription = "Open settings",
            )
        }
    }

    SettingsDialog(
        visible = showSettingsDialog,
        onDismiss = { showSettingsDialog = false },
    )

    ShareWorkspaceDialog(
        visible = shareDialogState.visible,
        defaultSessionName = WorkspaceRepository.projectName.collectAsState().value ?: "Amethyst Workspace",
        initialUserName = localUser.name,
        onDismiss = { shareDialogState.visible = false },
        onShare = { sessionName, userName ->
            scope.launch {
                LocalUserRepository.setUsername(userName)
                CollaborationManager.startHosting(
                    sessionName = sessionName.ifBlank { "Amethyst Workspace" },
                    localUser = LocalUserRepository.localUser.value
                )
                shareDialogState.visible = false
            }
        },
    )
}

@Composable
private fun ShareWorkspaceDialog(
    visible: Boolean,
    defaultSessionName: String,
    initialUserName: String,
    onDismiss: () -> Unit,
    onShare: (sessionName: String, userName: String) -> Unit,
) {
    val dialogState = rememberDialogState()
    var sessionName by remember(defaultSessionName) { mutableStateOf(defaultSessionName) }
    var userName by remember(initialUserName) { mutableStateOf(initialUserName) }

    LaunchedEffect(visible) {
        dialogState.visible = visible
    }

    AlertDialog(
        state = dialogState,
        onDismiss = onDismiss,
    ) {
        AlertDialogHeader {
            AlertDialogTitle("Share workspace")
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Input(
                value = sessionName,
                onValueChange = { sessionName = it },
                placeholder = "Session name",
                modifier = Modifier.fillMaxWidth(),
            )
            Input(
                value = userName,
                onValueChange = { userName = it },
                placeholder = "Your name",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AlertDialogFooter {
            AlertDialogCancel(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(
                onClick = { onShare(sessionName.trim(), userName.trim()) },
                size = ButtonSize.Small,
                enabled = sessionName.isNotBlank() && userName.isNotBlank(),
            ) {
                Text("Share")
            }
        }
    }
}

@Composable
private fun AutomappingStatusIndicator(armed: Boolean) {
    val backgroundColor = if (armed) Theme[colors][selectionSurface] else Theme[colors][secondary]
    val contentColor = if (armed) Theme[colors][selectionForeground] else Theme[colors][foreground]

    Row(
        modifier = Modifier
            .clip(SmallShape)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AUTOMAPPING ACTIVE",
            color = contentColor,
            style = Theme[typography][small],
        )
    }
}

@Composable
private fun KeyframesOptions(mode: KeyframesWorkspaceMode) {
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

            mode.onVirtualDeviceDragStart = { device, localX, localY ->
                val globalX = localX + device.position.value.x.toInt()
                val globalY = localY + device.position.value.y.toInt()
                val key = globalY * 10 + globalX
                mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeRootKey(key))
                pickingRootKey = false
            }

            onDispose {
                mode.onEvent = originalOnPaint
                mode.onVirtualDeviceDragStart = originalOnDragStart
            }
        } else {
            onDispose { }
        }
    }

    val rootKeyText = when {
        pickingRootKey -> "Press Pad..."
        state.rootKey != null -> {
            val x = state.rootKey!! % 10
            val y = state.rootKey!! / 10
            "Root: X$x Y$y"
        }
        else -> "Set Root Key"
    }

    val rootVariant = when {
        pickingRootKey -> ButtonVariant.Default
        state.rootKey != null -> ButtonVariant.Secondary
        else -> ButtonVariant.Ghost
    }

    WorkspaceToolbarSurface {
        Button(
            onClick = {
                if (state.rootKey != null && !pickingRootKey) {
                    mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeRootKey(null))
                } else {
                    pickingRootKey = !pickingRootKey
                }
            },
            variant = rootVariant,
            size = ButtonSize.Small,
            modifier = Modifier.rightClickable {
                mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeRootKey(null))
                pickingRootKey = false
            },
        ) {
            Icon(
                imageVector = Lucide.Anchor,
                contentDescription = null,
                tint = workspaceToolbarContentColor(rootVariant),
                modifier = Modifier.size(16.dp),
            )
            Text(rootKeyText)
        }

        Separator(
            modifier = Modifier.height(20.dp),
            orientation = SeparatorOrientation.Vertical,
        )

        KeyframesToggleOption(
            label = "Wrap",
            checked = state.wrap,
            onCheckedChange = {
                mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeWrap(it))
            },
        )

        Separator(
            modifier = Modifier.height(20.dp),
            orientation = SeparatorOrientation.Vertical,
        )

        KeyframesToggleOption(
            label = "Isolate",
            checked = state.isolate,
            onCheckedChange = {
                mode.onEvent?.invoke(KeyframesChainDeviceContract.Event.OnChangeIsolate(it))
            },
        )
    }
}

@Composable
private fun KeyframesToggleOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val variant = if (checked) ButtonVariant.Secondary else ButtonVariant.Ghost

    Button(
        onClick = { onCheckedChange(!checked) },
        variant = variant,
        size = ButtonSize.Small,
    ) {
        Text(label)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PianoRollOptions(mode: PianoRollWorkspaceMode) {
    WorkspaceToolbarSurface {
        listOf(
            TimelineEditorTool.SELECT to Lucide.MousePointer,
            TimelineEditorTool.DRAW   to Lucide.Pencil,
            TimelineEditorTool.ERASE  to Lucide.Eraser,
        ).forEach { (tool, icon) ->
            val variant = if (mode.activeTool == tool) ButtonVariant.Default else ButtonVariant.Ghost
            WorkspaceToolbarIconButton(
                onClick = { mode.activeTool = tool },
                imageVector = icon,
                contentDescription = tool.name.lowercase().replaceFirstChar { it.uppercase() },
                variant = variant,
            )
        }
    }

    var gridMenuExpanded by remember { mutableStateOf(false) }
    val gridLabel = when {
        !mode.gridResolutionLocked -> "Auto"
        mode.gridResolution == GridResolution.Quarter      -> "1/4"
        mode.gridResolution == GridResolution.Eighth       -> "1/8"
        mode.gridResolution == GridResolution.Sixteenth    -> "1/16"
        mode.gridResolution == GridResolution.ThirtySecond -> "1/32"
        else -> "Auto"
    }

    WorkspaceToolbarSurface {
        DropdownMenu(
            expanded = gridMenuExpanded,
            onExpandRequest = { gridMenuExpanded = true },
            onDismissRequest = { gridMenuExpanded = false },
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val hovered by interactionSource.collectIsHoveredAsState()
            val contentColor = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]

            UnstyledButton(
                onClick = { gridMenuExpanded = !gridMenuExpanded },
                shape = SmallShape,
                interactionSource = interactionSource,
                indication = null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .clip(SmallShape)
                    .background(if (hovered) Theme[colors][accent] else Color.Transparent),
            ) {
                Text(gridLabel, style = Theme[typography][small].copy(color = contentColor))
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Lucide.ChevronDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(10.dp),
                )
            }

            DropdownMenuContent(
                expanded = gridMenuExpanded,
                onDismissRequest = { gridMenuExpanded = false },
            ) {
                DropdownMenuRadioItem(
                    selected = !mode.gridResolutionLocked,
                    onClick = { mode.gridResolutionLocked = false; gridMenuExpanded = false },
                ) { Text("Auto") }

                listOf(
                    GridResolution.Quarter      to "1/4",
                    GridResolution.Eighth       to "1/8",
                    GridResolution.Sixteenth    to "1/16",
                    GridResolution.ThirtySecond to "1/32",
                ).forEach { (res, label) ->
                    DropdownMenuRadioItem(
                        selected = mode.gridResolutionLocked && mode.gridResolution == res,
                        onClick = {
                            mode.gridResolution = res
                            mode.gridResolutionLocked = true
                            gridMenuExpanded = false
                        },
                    ) { Text(label) }
                }
            }
        }
    }
}

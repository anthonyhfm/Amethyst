package dev.anthonyhfm.amethyst.workspace.chain.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.util.fastForEachReversed
import com.composables.icons.lucide.BookOpenText
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Expand
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Replace
import com.composables.icons.lucide.Shrink
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.clipboard.extractDevicesFromChainEffectEntry
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.help.GetHelpWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode

@Composable
fun ChainDeviceContextMenu(
    chain: Chain,
    device: GenericChainDevice<*>,
    visible: Boolean,
    offset: DpOffset,
    onDismiss: () -> Unit
) {
    val currentClipboard by ClipboardManager.clipboardData.collectAsState()

    ChainContextMenu(
        expanded = visible,
        onDismissRequest = onDismiss,
        offset = offset
    ) {
        if (device.isMuted) {
            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_unmute),
                icon = Lucide.Volume2,
                onClick = {
                    device.setMuted(false)
                    onDismiss()
                }
            )
        } else {
            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_mute),
                icon = Lucide.VolumeX,
                onClick = {
                    device.setMuted(true)
                    onDismiss()
                }
            )
        }

        if (device.isCollapsed) {
            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_expand),
                icon = Lucide.Expand,
                onClick = {
                    device.setCollapsed(false)
                    onDismiss()
                }
            )
        } else {
            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_collapse),
                icon = Lucide.Shrink,
                onClick = {
                    device.setCollapsed(true)
                    onDismiss()
                }
            )
        }

        ChainContextMenuItem(
            label = stringResource(Res.string.workspace_chain_chaineditor_copy),
            icon = Lucide.Copy,
            onClick = {
                ClipboardManager.setClipboardData(
                    ClipboardData.ChainDevice(
                        states = listOf(device.state.value),
                        type = when (WorkspaceRepository.mode.value) {
                            is SamplingChainWorkspaceMode -> ClipboardData.ChainDevice.ChainType.Sampling
                            else -> ClipboardData.ChainDevice.ChainType.Lights
                        }
                    )
                )
                onDismiss()
            }
        )

        ChainContextMenuItem(
            label = stringResource(Res.string.workspace_chain_chaineditor_duplicate),
            icon = Lucide.CopyPlus,
            onClick = {
                val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }

                chain.add(
                    device = StateChain.unpackDevice(StateChain.packDevice(device)),
                    atIndex = index + 1
                )
                onDismiss()
            }
        )

        if (device.helpRef != null) {
            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_get_help),
                icon = Lucide.BookOpenText,
                onClick = {
                    WorkspaceRepository.switchMode(
                        GetHelpWorkspaceMode(helpRef = device.helpRef!!),
                        undoable = false
                    )
                    onDismiss()
                }
            )
        }

        if (currentClipboard is ClipboardData.ChainDevice) {
            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_paste),
                icon = Lucide.ClipboardPaste,
                onClick = {
                    val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }

                    (currentClipboard as ClipboardData.ChainDevice).states.map {
                        StateChain.unpackDevice(it)
                    }.fastForEachReversed {
                        chain.add(
                            device = it,
                            atIndex = index + 1
                        )
                    }
                    onDismiss()
                }
            )

            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_paste_replace),
                icon = Lucide.Replace,
                onClick = {
                    val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }

                    chain.remove(device.selectionUUID)

                    (currentClipboard as ClipboardData.ChainDevice).states.map {
                        StateChain.unpackDevice(it)
                    }.fastForEachReversed {
                        chain.add(
                            device = it,
                            atIndex = index
                        )
                    }
                    onDismiss()
                }
            )
        }

        if (currentClipboard is ClipboardData.TimelineChainEffects) {
            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_paste),
                icon = Lucide.ClipboardPaste,
                onClick = {
                    val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }
                    val entries = (currentClipboard as ClipboardData.TimelineChainEffects).entries
                    var offset = 1
                    entries.forEach { entry ->
                        val devices = extractDevicesFromChainEffectEntry(entry)
                        devices.forEach { dev ->
                            chain.add(device = dev, atIndex = index + offset)
                            offset++
                        }
                    }
                    onDismiss()
                }
            )

            ChainContextMenuItem(
                label = stringResource(Res.string.workspace_chain_chaineditor_paste_replace),
                icon = Lucide.Replace,
                onClick = {
                    val index = chain.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID }
                    chain.remove(device.selectionUUID)
                    val entries = (currentClipboard as ClipboardData.TimelineChainEffects).entries
                    var offset = 0
                    entries.forEach { entry ->
                        val devices = extractDevicesFromChainEffectEntry(entry)
                        devices.forEach { dev ->
                            chain.add(device = dev, atIndex = index + offset)
                            offset++
                        }
                    }
                    onDismiss()
                }
            )
        }

        ContextMenuSeparator()

        ChainContextMenuItem(
            label = stringResource(Res.string.workspace_chain_chaineditor_delete),
            icon = Lucide.Trash2,
            variant = ContextMenuItemVariant.Destructive,
            onClick = {
                chain.remove(device.selectionUUID)
                onDismiss()
            }
        )
    }
}

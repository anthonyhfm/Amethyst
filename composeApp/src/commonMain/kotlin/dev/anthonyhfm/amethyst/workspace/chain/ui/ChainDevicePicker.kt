package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.material.icons.twotone.Adjust
import androidx.compose.material.icons.twotone.AudioFile
import androidx.compose.material.icons.twotone.BlurOn
import androidx.compose.material.icons.twotone.Opacity
import androidx.compose.material.icons.twotone.ColorLens
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.Filter
import androidx.compose.material.icons.twotone.FilterTiltShift
import androidx.compose.material.icons.twotone.Flip
import androidx.compose.material.icons.twotone.Gradient
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.LineAxis
import androidx.compose.material.icons.twotone.Loop
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.Pause
import androidx.compose.material.icons.twotone.Piano
import androidx.compose.material.icons.twotone.RotateLeft
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.ShapeLine
import androidx.compose.material.icons.twotone.StopCircle
import androidx.compose.material.icons.twotone.Timeline
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Transform
import androidx.compose.material.icons.twotone._123
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.effects.blur.BlurChainDevice
import dev.anthonyhfm.amethyst.devices.effects.opacity.OpacityChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.copy.CopyChainDevice
import dev.anthonyhfm.amethyst.devices.effects.delay.DelayChainDevice
import dev.anthonyhfm.amethyst.devices.effects.flip.FlipChainDevice
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.layer.LayerChainDevice
import dev.anthonyhfm.amethyst.devices.effects.layer_filter.LayerFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.loop.LoopChainDevice
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.offset.OffsetChainDevice
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDevice
import dev.anthonyhfm.amethyst.devices.effects.rotate.RotateChainDevice
import dev.anthonyhfm.amethyst.devices.effects.switch.MacroControlChainDevice
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material.icons.twotone.Diamond
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Contrast
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Preview
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.devices.effects.color_filter.ColorFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.preview.PreviewChainDevice
import dev.anthonyhfm.amethyst.devices.effects.shift.ShiftChainDevice
import dev.anthonyhfm.amethyst.devices.effects.adjust.AdjustChainDevice
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDevice
import dev.anthonyhfm.amethyst.devices.gem.GemChainDevice
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.host.GemDeviceState
import dev.anthonyhfm.amethyst.gem.ui.editor.GemEditorWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun ChainDevicePicker(
    visible: Boolean,
    sampling: Boolean,
    onPickComponent: (GenericChainDevice<*>) -> Unit,
    onDismiss: () -> Unit
) {
    val gemAssets by WorkspaceRepository.gemAssets.collectAsState()
    val gemHostDomain = if (sampling) null else GemSignalDomain.LED
    val compatibleGemAssets = gemAssets
        .filter { gemHostDomain == null || gemHostDomain in it.definition.host.supportedDomains }
        .sortedWith(compareBy({ it.metadata.name.lowercase() }, { it.metadata.id.lowercase() }))

    NavigableChainContextMenu(
        expanded = visible,
        onDismissRequest = onDismiss
    ) { onNavigate, _, level ->
        if (!sampling) {
            // Lights Menu
            when (level) {
                "main" -> {
                    ChainContextMenuSubmenuItem("Container", icon = Icons.TwoTone.Group, onClick = { onNavigate("container") })
                    ChainContextMenuSubmenuItem("Filter", icon = Icons.TwoTone.Filter, onClick = { onNavigate("filter") })
                    ChainContextMenuSubmenuItem("Color", icon = Icons.TwoTone.ColorLens, onClick = { onNavigate("color") })
                    ChainContextMenuSubmenuItem("Shape", icon = Icons.TwoTone.ShapeLine, onClick = { onNavigate("shape") })
                    ChainContextMenuSubmenuItem("Timing", icon = Icons.TwoTone.Timer, onClick = { onNavigate("timing") })
                    ChainContextMenuSubmenuItem("Transform", icon = Icons.TwoTone.Transform, onClick = { onNavigate("transform") })
                    ChainContextMenuSubmenuItem("Effects", icon = Icons.TwoTone.Science, onClick = { onNavigate("effects") })
                    ChainContextMenuSubmenuItem("Misc", icon = Icons.TwoTone.Adjust, onClick = { onNavigate("misc") })

                    if (WorkspaceRepository.mode.value is WorkspaceMode.LightsChain && GlobalSettings.experimentalExtensions) {
                        ChainContextMenuSubmenuItem("Gems", icon = Icons.TwoTone.Diamond, onClick = { onNavigate("gems") })
                    }
                }
                "gems" -> {
                    ChainContextMenuItem(
                        "New Gem",
                        icon = Icons.TwoTone.Add,
                        onClick = {
                            val asset = WorkspaceRepository.createGemAsset(gemHostDomain)
                            val device = GemChainDevice(
                                initialState = GemDeviceState.fromAsset(
                                    asset = asset,
                                    hostDomain = gemHostDomain ?: GemSignalDomain.LED
                                )
                            )
                            onPickComponent(device)
                            WorkspaceRepository.switchMode(
                                GemEditorWorkspaceMode(
                                    initialAssetId = asset.metadata.id,
                                    entryContext = GemEditorWorkspaceMode.EntryContext.HostDevice(
                                        preferredHostDomain = gemHostDomain ?: GemSignalDomain.LED,
                                        referencedAssetId = asset.metadata.id,
                                        referencedAssetName = asset.metadata.name
                                    )
                                )
                            )
                        }
                    )
                    if (compatibleGemAssets.isEmpty()) {
                        ChainContextMenuItem(
                            label = "No compatible Gems",
                            icon = Icons.TwoTone.Diamond,
                            enabled = false,
                            onClick = {}
                        )
                    } else {
                        compatibleGemAssets.forEach { asset ->
                            ChainContextMenuItem(
                                label = asset.metadata.name.ifBlank { asset.metadata.id.ifBlank { "Unnamed Gem" } },
                                icon = Icons.TwoTone.Diamond,
                                onClick = {
                                    onPickComponent(
                                        GemChainDevice(
                                            initialState = GemDeviceState.fromAsset(
                                                asset = asset,
                                                hostDomain = gemHostDomain ?: GemSignalDomain.LED
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
                "container" -> {
                    ChainContextMenuItem("Group", icon = Icons.TwoTone.Group, onClick = { onPickComponent(GroupChainDevice()) })
                    ChainContextMenuItem("Choke", icon = Icons.TwoTone.StopCircle, onClick = { onPickComponent(ChokeChainDevice()) })
                    ChainContextMenuItem("Multi", icon = Icons.TwoTone._123, onClick = { onPickComponent(MultiGroupChainDevice()) })
                }
                "filter" -> {
                    ChainContextMenuItem("Coordinate Filter", icon = Icons.TwoTone.MyLocation, onClick = { onPickComponent(CoordinateFilterChainDevice()) })
                    ChainContextMenuItem("Layer Filter", icon = Icons.TwoTone.Layers, onClick = { onPickComponent(LayerFilterChainDevice()) })
                    ChainContextMenuItem("Macro Filter", icon = Icons.TwoTone.FilterTiltShift, onClick = { onPickComponent(MacroFilterChainDevice()) })
                    ChainContextMenuItem("Color Filter", icon = Icons.TwoTone.ColorLens, onClick = { onPickComponent(ColorFilterChainDevice()) })
                }
                "color" -> {
                    ChainContextMenuItem("Color", icon = Icons.TwoTone.ColorLens, onClick = { onPickComponent(ColorChainDevice()) })
                    ChainContextMenuItem("Gradient", icon = Icons.TwoTone.Gradient, onClick = { onPickComponent(GradientChainDevice()) })
                    ChainContextMenuItem("Shift", icon = Icons.TwoTone.Contrast, onClick = { onPickComponent(ShiftChainDevice()) })
                    ChainContextMenuItem("Adjust", icon = Icons.TwoTone.Tune, onClick = { onPickComponent(AdjustChainDevice()) })
                }
                "shape" -> {
                    ChainContextMenuItem("Copy", icon = Icons.TwoTone.ContentCopy, onClick = { onPickComponent(CopyChainDevice()) })
                    ChainContextMenuItem("Keyframes", icon = Icons.TwoTone.Timeline, onClick = { onPickComponent(KeyframesChainDevice()) })
                    ChainContextMenuItem("Piano Roll", icon = Icons.TwoTone.Piano, onClick = { onPickComponent(PianoRollChainDevice()) })
                }
                "timing" -> {
                    ChainContextMenuItem("Delay", icon = Icons.TwoTone.Timer, onClick = { onPickComponent(DelayChainDevice()) })
                    ChainContextMenuItem("Hold", icon = Icons.TwoTone.Pause, onClick = { onPickComponent(HoldChainDevice()) })
                    ChainContextMenuItem("Loop", icon = Icons.TwoTone.Loop, onClick = { onPickComponent(LoopChainDevice()) })
                }
                "transform" -> {
                    ChainContextMenuItem("Offset", icon = Icons.TwoTone.LineAxis, onClick = { onPickComponent(OffsetChainDevice()) })
                    ChainContextMenuItem("Layer", icon = Icons.TwoTone.Layers, onClick = { onPickComponent(LayerChainDevice()) })
                    ChainContextMenuItem("Flip", icon = Icons.TwoTone.Flip, onClick = { onPickComponent(FlipChainDevice()) })
                    ChainContextMenuItem("Rotate", icon = Icons.TwoTone.RotateLeft, onClick = { onPickComponent(RotateChainDevice()) })
                }
                "effects" -> {
                    ChainContextMenuItem("Blur", icon = Icons.TwoTone.BlurOn, onClick = { onPickComponent(BlurChainDevice()) })
                    ChainContextMenuItem("Opacity", icon = Icons.TwoTone.Opacity, onClick = { onPickComponent(OpacityChainDevice()) })
                }
                "misc" -> {
                    ChainContextMenuItem("Macro Control", icon = Icons.TwoTone.Adjust, onClick = { onPickComponent(MacroControlChainDevice()) })
                    ChainContextMenuItem("Preview", icon = Icons.TwoTone.Preview, onClick = { onPickComponent(PreviewChainDevice()) })
                    ChainContextMenuItem("Transmit", icon = Icons.AutoMirrored.TwoTone.Send, onClick = { onPickComponent(TransmitChainDevice()) })
                }
            }
        } else {
            // Sampling Menu
            when (level) {
                "main" -> {
                    ChainContextMenuSubmenuItem("Container", icon = Icons.TwoTone.Group, onClick = { onNavigate("container") })
                    ChainContextMenuItem("Sample", icon = Icons.TwoTone.AudioFile, onClick = { onPickComponent(SampleChainDevice()) })
                    ChainContextMenuSubmenuItem("Filter", icon = Icons.TwoTone.Filter, onClick = { onNavigate("filter") })
                    ChainContextMenuSubmenuItem("Timing", icon = Icons.TwoTone.Timer, onClick = { onNavigate("timing") })
                    ChainContextMenuSubmenuItem("Gems", icon = Icons.TwoTone.Diamond, onClick = { onNavigate("gems") })
                    ChainContextMenuSubmenuItem("Misc", icon = Icons.TwoTone.Adjust, onClick = { onNavigate("misc") })
                }
                "gems" -> {
                    ChainContextMenuItem(
                        "New Gem",
                        icon = Icons.TwoTone.Add,
                        onClick = {
                            WorkspaceRepository.switchMode(
                                GemEditorWorkspaceMode(
                                    entryContext = GemEditorWorkspaceMode.EntryContext.Workspace(
                                        sourceLabel = "Sampling Chain",
                                        preferredHostDomain = gemHostDomain
                                    ),
                                    createNewAsset = true
                                )
                            )
                        }
                    )
                    ChainContextMenuSubmenuItem(
                        "Open Gem Editor",
                        icon = Icons.TwoTone.Diamond,
                        onClick = { onNavigate("gems-editor") }
                    )
                    if (compatibleGemAssets.isEmpty()) {
                        ChainContextMenuItem(
                            label = "No compatible Gems",
                            icon = Icons.TwoTone.Diamond,
                            enabled = false,
                            onClick = {}
                        )
                    } else {
                        compatibleGemAssets.forEach { asset ->
                            ChainContextMenuItem(
                                label = asset.metadata.name.ifBlank { asset.metadata.id.ifBlank { "Unnamed Gem" } },
                                icon = Icons.TwoTone.Diamond,
                                onClick = {
                                    onPickComponent(
                                        GemChainDevice(
                                            initialState = GemDeviceState.fromAsset(
                                                asset = asset,
                                                hostDomain = gemHostDomain ?: GemSignalDomain.LED
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
                "gems-editor" -> {
                    if (compatibleGemAssets.isEmpty()) {
                        ChainContextMenuItem(
                            label = "No compatible Gems",
                            icon = Icons.TwoTone.Diamond,
                            enabled = false,
                            onClick = {}
                        )
                    } else {
                        compatibleGemAssets.forEach { asset ->
                            ChainContextMenuItem(
                                label = asset.metadata.name.ifBlank { asset.metadata.id.ifBlank { "Unnamed Gem" } },
                                icon = Icons.TwoTone.Diamond,
                                onClick = {
                                    WorkspaceRepository.switchMode(
                                        GemEditorWorkspaceMode(
                                            initialAssetId = asset.metadata.id,
                                            entryContext = GemEditorWorkspaceMode.EntryContext.Workspace(
                                                sourceLabel = "Sampling Chain",
                                                preferredHostDomain = gemHostDomain
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
                "container" -> {
                    ChainContextMenuItem("Group", icon = Icons.TwoTone.Group, onClick = { onPickComponent(GroupChainDevice()) })
                    ChainContextMenuItem("Multi", icon = Icons.TwoTone._123, onClick = { onPickComponent(MultiGroupChainDevice()) })
                }
                "filter" -> {
                    ChainContextMenuItem("Coordinate Filter", icon = Icons.TwoTone.MyLocation, onClick = { onPickComponent(CoordinateFilterChainDevice()) })
                    ChainContextMenuItem("Macro Filter", icon = Icons.TwoTone.FilterTiltShift, onClick = { onPickComponent(MacroFilterChainDevice()) })
                }
                "timing" -> {
                    ChainContextMenuItem("Delay", icon = Icons.TwoTone.Timer, onClick = { onPickComponent(DelayChainDevice()) })
                    ChainContextMenuItem("Hold", icon = Icons.TwoTone.Pause, onClick = { onPickComponent(HoldChainDevice()) })
                    ChainContextMenuItem("Loop", icon = Icons.TwoTone.Loop, onClick = { onPickComponent(LoopChainDevice()) })
                }
                "misc" -> {
                    ChainContextMenuItem("Macro Control", icon = Icons.TwoTone.Adjust, onClick = { onPickComponent(MacroControlChainDevice()) })
                }
            }
        }
    }
}

private fun getLightsMenu() {}
private fun getSamplingMenu() {}

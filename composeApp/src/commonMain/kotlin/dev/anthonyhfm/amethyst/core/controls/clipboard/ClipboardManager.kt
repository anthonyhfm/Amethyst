package dev.anthonyhfm.amethyst.core.controls.clipboard

import androidx.compose.ui.util.fastForEachReversed
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ClipboardManager {
    private val _clipboardData: MutableStateFlow<ClipboardData?> = MutableStateFlow(null)
    val clipboardData = _clipboardData.asStateFlow()

    fun setClipboardData(data: ClipboardData) {
        _clipboardData.update { data }
    }

    fun copy(data: List<Selectable>) {
        if (data.isEmpty()) return

        when {
            data.any { it is Selectable.ChainDevice } -> {
                setClipboardData(
                    data = ClipboardData.ChainDevice(
                        states = data.filterIsInstance<Selectable.ChainDevice>().map { it.device.state.value },
                        type = if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.LightsChain) {
                            ClipboardData.ChainDevice.ChainType.Lights
                        } else {
                            ClipboardData.ChainDevice.ChainType.Sampling
                        }
                    )
                )
            }

            data.any { it is Selectable.KeyframeItem } -> {
                val keyframeItems = data.filterIsInstance<Selectable.KeyframeItem>()
                val frames = keyframeItems.map { keyframeItem ->
                    keyframeItem.parent.state.value.frames[keyframeItem.frameIndex]
                }

                setClipboardData(
                    data = ClipboardData.Keyframe(
                        frames = frames
                    )
                )
            }

            data.any { it is Selectable.GroupChainItem } -> {
                val groupItems = data.filterIsInstance<Selectable.GroupChainItem>()
                val groups = groupItems.map { groupItem ->
                    when (groupItem.parent) {
                        is GroupChainDevice -> groupItem.parent.state.value.groups[groupItem.groupIndex]
                        is MultiGroupChainDevice -> groupItem.parent.state.value.groups[groupItem.groupIndex]
                        else -> throw IllegalStateException("Unsupported parent type for GroupChainItem")
                    }
                }

                setClipboardData(
                    data = ClipboardData.GroupChainItem(
                        groups = groups
                    )
                )
            }

            data.any { it is Selectable.GradientStep } -> {
                val step = data.filterIsInstance<Selectable.GradientStep>().first()

                setClipboardData(
                    data = ClipboardData.GradientStep(step)
                )
            }

            data.any { it is Selectable.VirtualViewportDevice } -> {
                println("Copying Virtual Viewport Devices is currently not supported")
            }
        }
    }

    fun paste() {
        val mode = WorkspaceRepository.mode.value

        when (clipboardData.value) {
            is ClipboardData.ChainDevice -> {
                val index: Int? = SelectionManager.selections.value.filterIsInstance<Selectable.ChainDevice>().maxOfOrNull { device ->
                    device.parent.devices.value.indexOfFirst { it.selectionUUID == device.selectionUUID } + 1
                }

                if (mode is WorkspaceContract.WorkspaceMode.LightsChain) {
                    if ((clipboardData.value as ClipboardData.ChainDevice).type != ClipboardData.ChainDevice.ChainType.Lights) return

                    (clipboardData.value as ClipboardData.ChainDevice).states.forEach {
                        WorkspaceRepository.lightsChain.heavenChain.add(
                            device = StateChain.unpackDevice(it),
                            atIndex = index
                        )
                    }
                } else if (mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                    if ((clipboardData.value as ClipboardData.ChainDevice).type != ClipboardData.ChainDevice.ChainType.Sampling) return

                    (clipboardData.value as ClipboardData.ChainDevice).states.forEach {
                        WorkspaceRepository.samplingChain.heavenChain.add(
                            device = StateChain.unpackDevice(it),
                            atIndex = index
                        )
                    }
                }
            }

            is ClipboardData.Keyframe -> {
                if (mode is dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode) {
                    val keyframeData = clipboardData.value as ClipboardData.Keyframe
                    val targetIndex = SelectionManager.selections.value
                        .filterIsInstance<Selectable.KeyframeItem>()
                        .maxOfOrNull { it.frameIndex + 1 }

                    mode.parentDevice?.pasteFrames(keyframeData.frames, targetIndex)
                }
            }

            is ClipboardData.GroupChainItem -> {
                val groupData = clipboardData.value as ClipboardData.GroupChainItem
                val selectedGroups = SelectionManager.selections.value.filterIsInstance<Selectable.GroupChainItem>()

                if (selectedGroups.isNotEmpty()) {
                    val firstSelected = selectedGroups.first()
                    val targetIndex = selectedGroups.maxOfOrNull { it.groupIndex + 1 }

                    when (firstSelected.parent) {
                        is GroupChainDevice -> {
                            firstSelected.parent.pasteGroups(groupData.groups, targetIndex)
                        }
                        is MultiGroupChainDevice -> {
                            firstSelected.parent.pasteGroups(groupData.groups, targetIndex)
                        }
                    }
                }
            }

            else -> {
               println("You cannot copy this right now")
            }
        }
    }
}
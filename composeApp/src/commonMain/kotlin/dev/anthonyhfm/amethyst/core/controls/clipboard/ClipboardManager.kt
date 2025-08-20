package dev.anthonyhfm.amethyst.core.controls.clipboard

import androidx.compose.ui.util.fastForEachReversed
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
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
                        states = data.filterIsInstance<Selectable.ChainDevice>().map { it.device.state.value }
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
                    (clipboardData.value as ClipboardData.ChainDevice).states.forEach {
                        WorkspaceRepository.lightsChain.heavenChain.add(
                            device = StateChain.unpackDevice(it),
                            atIndex = index
                        )
                    }
                } else if (mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                    (clipboardData.value as ClipboardData.ChainDevice).states.forEach {
                        WorkspaceRepository.samplingChain.heavenChain.add(
                            device = StateChain.unpackDevice(it),
                            atIndex = index
                        )
                    }
                }
            }

            else -> {
               println("You cannot copy this right now")
            }
        }
    }
}
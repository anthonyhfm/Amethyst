package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiManager
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncCoordinator
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadIdealised
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LayoutWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val amethystMidiManager: AmethystMidiManager = AmethystMidiManager()
) : ViewModel() {
    val state = MutableStateFlow(
        WorkspaceContract.State(
            mode = WorkspaceRepository.mode.value
        )
    )

    init {
        viewModelScope.launch {
            ViewportRepository.devices.collect { devices ->
                devices.forEach { device ->
                    device.onEvent = { onEvent(it) }
                }

                if (devices.isNotEmpty()) {
                    amethystMidiManager.startAutoDetectLoop()
                } else {
                    amethystMidiManager.stopAutoDetectLoop()
                }
            }
        }

        viewModelScope.launch {
            WorkspaceRepository.mode.collect { newMode ->
                when (newMode) {
                    is KeyframesWorkspaceMode -> {
                        Heaven.clear()

                        newMode.wake()
                    }

                    is CoordinateFilterWorkspaceMode -> {
                        Heaven.clear()

                        newMode.wake()
                    }

                    else -> {
                        if (state.value.mode is CoordinateFilterWorkspaceMode) {
                            Heaven.clear()

                            (state.value.mode as CoordinateFilterWorkspaceMode).close()
                        }

                        if (state.value.mode is KeyframesWorkspaceMode) {
                            Heaven.clear()

                            (state.value.mode as KeyframesWorkspaceMode).close()
                        }

                        if (state.value.mode is LayoutWorkspaceMode) {
                            WorkspaceRepository.updateWorkspaceBounds()

                            if (SelectionManager.selections.value.any { it is Selectable.VirtualViewportDevice }) {
                                SelectionManager.clear()
                            }
                        }
                    }
                }

                state.update {
                    it.copy(
                        mode = newMode
                    )
                }
            }
        }

        viewModelScope.launch {
            SelectionManager.selections
                .map { selections -> selections.firstOrNull()?.selectionUUID }
                .distinctUntilChanged()
                .collect { focusedElementId ->
                    CollaborationPresence.sendFocusedElement(focusedElementId)
                }
        }
    }

    fun onEvent(event: WorkspaceContract.Event) {
        when (event) {
            is WorkspaceContract.Event.OpenVirtualDevicePicker -> {
                state.update {
                    it.copy(showDevicePicker = true)
                }
            }

            is WorkspaceContract.Event.DismissVirtualDevicePicker -> {
                state.update {
                    it.copy(showDevicePicker = false)
                }
            }

            is WorkspaceContract.Event.AddDeviceToViewport -> {
                val device = when (event.device) {
                    is ViewportLaunchpadPro -> ViewportLaunchpadPro()
                    is ViewportLaunchpadIdealised -> ViewportLaunchpadIdealised()
                    is ViewportLaunchpadMk2 -> ViewportLaunchpadMk2()
                    is ViewportLaunchpadProMk3 -> ViewportLaunchpadProMk3()
                    is ViewportLaunchpadX -> ViewportLaunchpadX()
                    is ViewportMystrix -> ViewportMystrix()
                    is ViewportMidiFighter64 -> ViewportMidiFighter64()

                    else -> return
                }

                device.onEvent = { onEvent(it) }

                state.update {
                    it.copy(showDevicePicker = false)
                }

                viewModelScope.launch {
                    WorkspaceRepository.addVirtualDevice(device)
                }
            }

            is WorkspaceContract.Event.ChangeViewportElementPosition -> {
                if (state.value.mode !is LayoutWorkspaceMode) return

                val elements = ViewportRepository.devices.value
                if (event.index in elements.indices) {
                    val element = elements[event.index]
                    viewModelScope.launch {
                        WorkspaceRepository.moveVirtualDevice(element.launchpadId, event.offset)
                    }
                }
            }

            is WorkspaceContract.Event.OnViewportElementMoveFinished -> {
                WorkspaceRepository.updateWorkspaceBounds()
            }

            is WorkspaceContract.Event.OnClickDeviceConfigure -> {
                if (state.value.mode is LayoutWorkspaceMode) {
                    state.update {
                        it.copy(
                            showDeviceConfigurator = event.uuid
                        )
                    }
                }
            }

            is WorkspaceContract.Event.OnDismissDeviceConfigure -> {
                state.update {
                    it.copy(
                        showDeviceConfigurator = null
                    )
                }
            }

            is WorkspaceContract.Event.OnChangeDeviceConfig -> {
                if (state.value.mode is LayoutWorkspaceMode) {
                    amethystMidiManager.changeDeviceConfig(event)
                }
            }

            is WorkspaceContract.Event.OnDeleteDevice -> {
                SelectionManager.clear()
                viewModelScope.launch {
                    WorkspaceRepository.removeVirtualDeviceById(event.uuid)
                }
            }

            is WorkspaceContract.Event.AddChainDevice -> {
                if (state.value.mode is LightsChainWorkspaceMode) {
                    WorkspaceRepository.lightsChain.add(event.device, event.atIndex)
                } else if (state.value.mode is SamplingChainWorkspaceMode) {
                    WorkspaceRepository.samplingChain.add(event.device, event.atIndex)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        amethystMidiManager.close()
    }
}

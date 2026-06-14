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
            WorkspaceRepository.deviceRefresh.collect {
                val devices = Heaven.devices.map { device ->
                    device.onEvent = { onEvent(it) }

                    return@map device
                }

                state.update {
                    it.copy(
                        viewportElements = devices
                    )
                }
            }
        }

        viewModelScope.launch {
            WorkspaceRepository.deviceRefresh.emit(Unit)
        }

        viewModelScope.launch {
            state
                .map { it.viewportElements }
                .distinctUntilChanged()
                .collect { viewportElements ->
                    Heaven.devices = viewportElements

                    if (viewportElements.isNotEmpty()) {
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

                        if (state.value.mode is WorkspaceContract.WorkspaceMode.Layout) {
                            WorkspaceRepository.updateWorkspaceBounds()

                            if (SelectionManager.selections.value.any { it is Selectable.VirtualViewportDevice }) {
                                SelectionManager.clear()
                            }
                        }
                    }
                }

                state.update {
                    it.copy(
                        mode = newMode,
                        viewportElements = Heaven.devices
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
                    it.copy(
                        showDevicePicker = false,
                        viewportElements = it.viewportElements.plus(device)
                    )
                }

                DeviceSyncCoordinator.onDevicePlaced(device)
            }

            is WorkspaceContract.Event.ChangeViewportElementPosition -> {
                if (state.value.mode !is WorkspaceContract.WorkspaceMode.Layout) return

                var movedElement: dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement? = null
                state.update {
                    it.copy(
                        viewportElements = it.viewportElements.toMutableList().apply {
                            this[event.index].position.value = event.offset
                            movedElement = this[event.index]
                        }
                    )
                }

                movedElement?.let { DeviceSyncCoordinator.onDeviceMoved(it) }
                WorkspaceRepository.updateWorkspaceBounds()
            }

            is WorkspaceContract.Event.OnViewportElementMoveFinished -> {
                WorkspaceRepository.updateWorkspaceBounds()
            }

            is WorkspaceContract.Event.OnPanViewport -> {
                state.update {
                    it.copy(
                        viewportState = it.viewportState.copy(
                            offset = it.viewportState.offset + event.offset
                        )
                    )
                }
            }

            is WorkspaceContract.Event.OnZoomViewport -> {
                state.update {
                    val currentZoom = it.viewportState.zoom
                    val isMobile = platform is Platform.Android || platform is Platform.iOS
                    val isSingleVirtualDeviceMode = isMobile && it.viewportElements.size == 1
                    val minZoom = if (isSingleVirtualDeviceMode) 0.1f else 0.5f
                    val maxZoom = if (isSingleVirtualDeviceMode) 4.0f else 2.0f
                    val newZoom = (currentZoom + event.zoomDelta).coerceIn(minZoom, maxZoom)

                    val zoomChange = newZoom / currentZoom
                    val newOffset = Offset(
                        x = event.zoomCenter.x + (it.viewportState.offset.x - event.zoomCenter.x) * zoomChange,
                        y = event.zoomCenter.y + (it.viewportState.offset.y - event.zoomCenter.y) * zoomChange
                    )

                    it.copy(
                        viewportState = it.viewportState.copy(
                            offset = newOffset,
                            zoom = newZoom
                        )
                    )
                }
            }

            is WorkspaceContract.Event.OnClickDeviceConfigure -> {
                if (state.value.mode is WorkspaceContract.WorkspaceMode.Layout) {
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
                if (state.value.mode is WorkspaceContract.WorkspaceMode.Layout) {
                    amethystMidiManager.changeDeviceConfig(event)
                }
            }

            is WorkspaceContract.Event.OnDeleteDevice -> {
                val element = state.value.viewportElements.find { it.selectionUUID == event.uuid }

                element?.deviceConfig?.launchpadDevice?.midiOutput?.close()
                element?.deviceConfig?.input?.close()

                element?.let {
                    DeviceSyncCoordinator.onDeviceRemoved(it.launchpadId)
                }

                SelectionManager.clear()

                state.update {
                    it.copy(
                        viewportElements = it.viewportElements.filter { e -> e.selectionUUID != event.uuid }
                    )
                }

                WorkspaceRepository.updateWorkspaceBounds()
            }

            is WorkspaceContract.Event.AddChainDevice -> {
                if (state.value.mode is WorkspaceContract.WorkspaceMode.LightsChain) {
                    WorkspaceRepository.lightsChain.add(event.device, event.atIndex)
                } else if (state.value.mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
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

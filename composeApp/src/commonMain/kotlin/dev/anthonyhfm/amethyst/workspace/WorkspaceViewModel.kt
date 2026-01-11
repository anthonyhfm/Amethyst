package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiManager
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import kotlinx.coroutines.flow.MutableStateFlow
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
            state.collect { state ->
                Heaven.devices = state.viewportElements
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
                    it.copy(mode = newMode)
                }
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
            }

            is WorkspaceContract.Event.ChangeViewportElementPosition -> {
                if (state.value.mode !is WorkspaceContract.WorkspaceMode.Layout) return

                state.update {
                    it.copy(
                        viewportElements = it.viewportElements.toMutableList().apply {
                            this[event.index].position.value = event.offset
                        }
                    )
                }

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
                    val newZoom = (currentZoom + event.zoomDelta).coerceIn(0.5f, 2.0f)

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

            is WorkspaceContract.Event.AddChainDevice -> {
                if (state.value.mode is WorkspaceContract.WorkspaceMode.LightsChain) {
                    WorkspaceRepository.lightsChain.add(event.device, event.atIndex)
                } else if (state.value.mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                    WorkspaceRepository.samplingChain.add(event.device, event.atIndex)
                }
            }
        }
    }
}

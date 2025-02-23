package dev.anthonyhfm.amethyst.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.midi.data.getMidiInputData
import dev.anthonyhfm.amethyst.ui.launchpad.viewport_launchpads.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport_launchpads.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.chain.WorkspaceChain
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val midiAccess: MidiAccess
) : ViewModel() {
    val state = MutableStateFlow(WorkspaceContract.State())

    val chain: WorkspaceChain = WorkspaceChain()

    init {
        viewModelScope.launch {
            state.collect { state ->
                chain.launchpadElements.update {
                    state.viewportElements
                }
            }
        }
    }

    fun onEvent(event: WorkspaceContract.Event) {
        when (event) {
            is WorkspaceContract.Event.AddDeviceToViewport -> {
                val device = ViewportLaunchpadPro()

                device.onEvent = { onEvent(it) }

                state.update {
                    it.copy(
                        viewportElements = it.viewportElements.plus(device)
                    )
                }
            }

            is WorkspaceContract.Event.ChangeWorkspaceMode -> {
                state.update {
                    it.copy(mode = event.mode)
                }
            }

            is WorkspaceContract.Event.ChangeViewportElementPosition -> {
                if (state.value.mode != WorkspaceContract.WorkspaceMode.LAYOUT) return

                state.update {
                    it.copy(
                        viewportElements = it.viewportElements.toMutableList().apply {
                            this[event.index].position.value = event.offset
                        }
                    )
                }
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

            is WorkspaceContract.Event.OnClickDeviceConfigure -> {
                state.update {
                    it.copy(
                        showDeviceConfigurator = event.index
                    )
                }
            }

            is WorkspaceContract.Event.OnDismissDeviceConfigure -> {
                state.update {
                    it.copy(
                        showDeviceConfigurator = null
                    )
                }
            }

            is WorkspaceContract.Event.OnSelectDevice -> {
                state.update {
                    it.copy(
                        viewportState = it.viewportState.copy(
                            selectedElement = event.index
                        )
                    )
                }
            }

            is WorkspaceContract.Event.OnChangeDeviceConfig -> {
                state.value.viewportElements[event.index].apply {
                    deviceConfig.input?.close()
                    deviceConfig.output?.close()

                    CoroutineScope(Dispatchers.IO).launch {
                        var inputDevice: MidiInput? = null
                        var outputDevice: MidiOutput? = null

                        event.inputPort?.let { input ->
                            inputDevice = midiAccess.openInput(input.id)

                            inputDevice.setMessageReceivedListener { bytes, _, _, _ ->
                                getMidiInputData(bytes)?.let {
                                    chain.onMidiInput(
                                        inputData = it,
                                        offset = this@apply.position.value
                                    )
                                }
                            }
                        }

                        event.outputPort?.let { output ->
                            outputDevice = midiAccess.openOutput(output.id)
                        }

                        deviceConfig = deviceConfig.copy(
                            input = inputDevice,
                            output = outputDevice,
                            type = event.deviceType
                        )
                    }
                }
            }

            is WorkspaceContract.Event.AddChainDevice -> {
                chain.addDevice(event.device, event.atIndex)
            }
        }
    }

    fun triggerEffect(effect: WorkspaceContract.Effect) { }
}
package dev.anthonyhfm.amethyst.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.midi.IO_COROUTINE
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.core.midi.data.getMidiInputData
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevice
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceMystrix
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceProMk3
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.workspace.chain.WorkspaceChain
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val midiAccess: MidiAccess,
    private val controller: WorkspaceController
) : ViewModel() {
    val state = MutableStateFlow(
        WorkspaceContract.State(
            mode = controller.mode.value
        )
    )

    val chain: WorkspaceChain = WorkspaceChain()

    init {
        viewModelScope.launch {
            state.collect { state ->
                Heaven.devices = state.viewportElements

                chain.launchpadElements.update {
                    state.viewportElements
                }
            }
        }

        viewModelScope.launch {
            controller.mode.collect { newMode ->
                state.update {
                    it.copy(mode = newMode)
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
                controller.switchMode(event.mode)
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
                changeDeviceConfig(event)
            }

            is WorkspaceContract.Event.AddChainDevice -> {
                chain.addDevice(event.device, event.atIndex)
            }

            is WorkspaceContract.Event.OnPressVirtualDevice -> {
                if (state.value.mode !is WorkspaceContract.WorkspaceMode.Layout) {
                    chain.onMidiInput(
                        inputData = MidiInputData(event.y * 10 + event.x, 127),
                        offset = event.offset
                    )
                }
            }

            is WorkspaceContract.Event.OnReleaseVirtualDevice -> {
                if (state.value.mode !is WorkspaceContract.WorkspaceMode.Layout) {
                    chain.onMidiInput(
                        inputData = MidiInputData(event.y * 10 + event.x, 0),
                        offset = event.offset
                    )
                }
            }
        }
    }

    fun changeDeviceConfig(event: WorkspaceContract.Event.OnChangeDeviceConfig) {
        state.value.viewportElements[event.index].apply {
            deviceConfig.input?.close()
            deviceConfig.launchpadDevice?.midiOutput?.close()

            IO_COROUTINE.launch {
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
                    launchpadDevice = outputDevice?.let { output ->
                        event.deviceType?.mapLaunchpadDevice(output)
                    },
                )
            }
        }
    }
}

private fun LaunchpadDeviceType.mapLaunchpadDevice(output: MidiOutput): LaunchpadDevice? {
    return when (this) {
        LaunchpadDeviceType.LAUNCHPAD_PRO_MK3 -> LaunchpadDeviceProMk3(output)
        LaunchpadDeviceType.LAUNCHPAD_X -> LaunchpadDeviceX(output)
        LaunchpadDeviceType.MYSTRIX -> LaunchpadDeviceMystrix(output)

        else -> null
    }
}
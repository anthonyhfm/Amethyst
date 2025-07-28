package dev.anthonyhfm.amethyst.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiManager
import dev.anthonyhfm.amethyst.core.midi.IO_COROUTINE
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.core.midi.data.getMidiInputData
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevice
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceMK2
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceMystrix
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevicePro
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceProMk3
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevicePush2
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceX
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.chain.WorkspaceChain
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceViewModel(
    private val midiAccess: MidiAccess,
    private val amethystMidiManager: AmethystMidiManager
) : ViewModel() {
    val state = MutableStateFlow(
        WorkspaceContract.State(
            mode = WorkspaceRepository.mode.value
        )
    )

    init {
        Heaven.devices.forEach { device ->
            device.onEvent = { onEvent(it) }

            state.update {
                it.copy(
                    viewportElements = it.viewportElements.plus(device)
                )
            }
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
                    }
                }

                state.update {
                    it.copy(mode = newMode,)
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

            is WorkspaceContract.Event.ChangeWorkspaceMode -> {
                WorkspaceRepository.switchMode(event.mode)
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

            is WorkspaceContract.Event.OnClickDeviceConfigure -> {
                if (state.value.mode is WorkspaceContract.WorkspaceMode.Layout) {
                    state.update {
                        it.copy(
                            showDeviceConfigurator = event.index
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
                    changeDeviceConfig(event)
                }
            }

            is WorkspaceContract.Event.AddChainDevice -> {
                if (state.value.mode is WorkspaceContract.WorkspaceMode.LightsChain) {
                    WorkspaceRepository.lightsChain.addDevice(event.device, event.atIndex)
                } else if (state.value.mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                    WorkspaceRepository.samplingChain.addDevice(event.device, event.atIndex)
                }
            }

            is WorkspaceContract.Event.OnPressVirtualDevice -> {
                when (state.value.mode) {
                    is KeyframesWorkspaceMode -> {
                        (state.value.mode as KeyframesWorkspaceMode).virtualDevicePress(event.x, event.y - event.layout.offsetY, event.offset)
                    }

                    is CoordinateFilterWorkspaceMode -> {
                        (state.value.mode as CoordinateFilterWorkspaceMode).virtualDevicePress(event.x, event.y - event.layout.offsetY, event.offset)
                    }

                    is WorkspaceContract.WorkspaceMode.Layout -> { }

                    else -> {
                        WorkspaceRepository.lightsChain.onMidiInput(
                            inputData = MidiInputData((event.y + event.layout.offsetY) * 10 + event.x, 127),
                            offset = event.offset
                        )

                        WorkspaceRepository.samplingChain.onMidiInput(
                            inputData = MidiInputData((event.y + event.layout.offsetY) * 10 + event.x, 127),
                            offset = event.offset
                        )
                    }
                }
            }

            is WorkspaceContract.Event.OnReleaseVirtualDevice -> {
                if (state.value.mode !is WorkspaceContract.WorkspaceMode.Layout) {
                    WorkspaceRepository.lightsChain.onMidiInput(
                        inputData = MidiInputData((event.y + event.layout.offsetY) * 10 + event.x, 0),
                        offset = event.offset
                    )

                    WorkspaceRepository.samplingChain.onMidiInput(
                        inputData = MidiInputData((event.y + event.layout.offsetY) * 10 + event.x, 0),
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
                }

                event.outputPort?.let { output ->
                    outputDevice = midiAccess.openOutput(output.id)
                }

                var deviceType: LaunchpadDeviceType? = null

                if (inputDevice != null && outputDevice != null) {
                    deviceType = amethystMidiManager.detect(inputDevice, outputDevice)
                }

                inputDevice?.setMessageReceivedListener { bytes, _, _, _ ->
                    getMidiInputData(bytes)?.let {
                        if (WorkspaceRepository.mode.value.claimInputs) {
                            WorkspaceRepository.mode.value.onMidiInput(it)
                        } else {
                            WorkspaceRepository.lightsChain.onMidiInput(it, this@apply.position.value)
                            WorkspaceRepository.samplingChain.onMidiInput(it, this@apply.position.value)
                        }
                    }
                }

                deviceConfig = deviceConfig.copy(
                    input = inputDevice,
                    launchpadDevice = outputDevice?.let { output ->
                        deviceType?.mapLaunchpadDevice(output)
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
        LaunchpadDeviceType.LAUNCHPAD_PRO -> LaunchpadDevicePro(output)
        LaunchpadDeviceType.LAUNCHPAD_PRO_CFW -> LaunchpadDevicePro(output, true)
        LaunchpadDeviceType.LAUNCHPAD_MK2 -> LaunchpadDeviceMK2(output)
        LaunchpadDeviceType.MYSTRIX -> LaunchpadDeviceMystrix(output)
        LaunchpadDeviceType.ABLETON_PUSH_2 -> LaunchpadDevicePush2(output)
    }
}

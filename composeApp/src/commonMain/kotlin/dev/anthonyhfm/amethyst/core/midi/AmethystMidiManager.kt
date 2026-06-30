package dev.anthonyhfm.amethyst.core.midi

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.getMidiInputData
import dev.anthonyhfm.amethyst.core.midi.data.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevice
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceMK2
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceMiniMk3
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceMystrix
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevicePro
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceProMk3
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceX
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.AutoPlayState
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.rotateMidiCoordinate
import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import dev.atsushieno.ktmidi.MidiPortDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * # Amethyst Midi Manager
 *
 * The Amethyst Midi Manager should be able to recognize device types based on input and output device.
 */
class AmethystMidiManager {
    private val midiAccess: MidiAccess = platformMidiAccess ?: EmptyMidiAccess()

    val midiInScope = CoroutineScope(Dispatchers.IO.limitedParallelism(4))
    private var autoDetectJob: Job? = null
    private var autoConfigureJob: Job? = null
    private var monitorJob: Job? = null
    private val deviceConfigMutex = Mutex()

    fun close() {
        stopAutoDetectLoop()
        autoConfigureJob?.cancel()
        autoDetectJob?.cancel()
        midiInScope.cancel()
    }

    private data class DetectedLaunchpad(
        val inputPort: MidiPortDetails,
        val outputPort: MidiPortDetails,
        val deviceType: LaunchpadDeviceType
    )

    @OptIn(ExperimentalUnsignedTypes::class)
    val inquiryTests: Map<LaunchpadDeviceType, (UByteArray) -> Boolean> = mapOf(
        LaunchpadDeviceType.LAUNCHPAD_PRO_MK3 to { LaunchpadDeviceProMk3.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_X to { LaunchpadDeviceX.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_MINI_MK3 to { LaunchpadDeviceMiniMk3.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_PRO_CFW to { LaunchpadDevicePro.identifyCFW(it) },
        LaunchpadDeviceType.LAUNCHPAD_PRO to { LaunchpadDevicePro.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_MK2 to { LaunchpadDeviceMK2.identify(it) },
        LaunchpadDeviceType.MYSTRIX to { LaunchpadDeviceMystrix.identify(it) },
    )

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun detect(input: MidiInput, output: MidiOutput): LaunchpadDeviceType? {
        val deviceInquiry = ubyteArrayOf(
            240u,
            126u,
            127u,
            6u,
            1u,
            247u
        )

        var deviceType: LaunchpadDeviceType? = null

        input.setMessageReceivedListener { data, _, _, _ ->
            val detectedType = getDeviceTypeByInquiry(data)
            if (detectedType != null) {
                deviceType = detectedType
            }
        }

        output.send(
            mevent = deviceInquiry.toByteArray(),
            offset = 0,
            length = deviceInquiry.size,
            timestampInNanoseconds = 0,
        )

        withTimeoutOrNull(2000) {
            while (deviceType == null) {
                delay(5)
            }
        }

        return deviceType
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun getDeviceTypeByInquiry(data: ByteArray): LaunchpadDeviceType? {
        println(data.toUByteArray().contentToString())

        val convertedData = data.toUByteArray()
        val messageStart = data.toUByteArray().indexOf(240u)
        val messageEnd = data.toUByteArray().indexOf(247u)

        if (messageStart == -1 || messageEnd == -1) return null

        val sysex = convertedData.copyOfRange(messageStart, messageEnd + 1)

        if (sysex[1] == 126.toUByte()) {
            return inquiryTests.entries.find {
                it.value(sysex)
            }?.key
        }

        return null
    }

    fun changeDeviceConfig(event: WorkspaceContract.Event.OnChangeDeviceConfig) {
        Heaven.devices.find { it.selectionUUID == event.uuid }?.apply {
            deviceConfig.input?.close()
            deviceConfig.launchpadDevice?.midiOutput?.close()

            savedInputPortId = event.inputPort?.id
            savedInputPortName = event.inputPort?.name
            savedOutputPortId = event.outputPort?.id
            savedOutputPortName = event.outputPort?.name

            midiInScope.launch {
                var inputDevice: MidiInput? = null
                var outputDevice: MidiOutput? = null

                event.inputPort?.let { input ->
                    inputDevice = runCatching { midiAccess.openInput(input.id) }.getOrNull()
                }

                event.outputPort?.let { output ->
                    outputDevice = runCatching { midiAccess.openOutput(output.id) }.getOrNull()
                }

                var deviceType: LaunchpadDeviceType? = null

                if (inputDevice != null && outputDevice != null) {
                    deviceType = detect(inputDevice, outputDevice)
                }

                inputDevice?.setMessageReceivedListener { bytes, _, _, _ ->
                    val msgCopy = bytes.copyOf()

                    if (platform is Platform.iOS) {
                        msgCopy.toList().chunked(3).forEach {
                            onMidiMessage(it.toByteArray())
                        }
                    } else {
                        onMidiMessage(msgCopy)
                    }
                }

                deviceConfigMutex.withLock {
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

    fun startAutoDetectLoop() {
        if (monitorJob?.isActive == true) return

        monitorJob = midiInScope.launch {
            while (isActive) {
                autoConfigureDevices()
                delay(2000)
            }
        }
    }

    fun stopAutoDetectLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun LaunchpadViewportElement.onMidiMessage(msg: ByteArray) {
        midiInScope.launch {
            val data = getMidiInputData(msg)

            data?.let {
                if (WorkspaceRepository.mode.value.claimMidiInputs) {
                    val offset = position.value.copy(
                        x = position.value.x - layout.offsetX,
                        y = position.value.y
                    )

                    WorkspaceRepository.mode.value.onMidiInput(it, offset)
                } else {
                    val offset = position.value.copy(
                        x = position.value.x - layout.offsetX,
                        y = position.value.y
                    )

                    val x = it.pitch % 10
                    val y = it.pitch / 10

                    val (visX, visY) = rotateMidiCoordinate(x, y, layout, rotationDegrees.floatValue)

                    val posX = offset.x.toInt()
                    val posY = offset.y.toInt()

                    val globalX = posX + visX
                    val globalY = posY + (9 - visY)

                    if (AutomappingManager.isMappingActive()) {
                        if (it.velocity != 0) {
                            AutomappingManager.tryCommitPadMapping(
                                device = this@onMidiMessage,
                                globalX = globalX,
                                globalY = globalY,
                            )
                        }
                        return@let
                    }

                    val midiSignals = listOf(
                        Signal.Midi(
                            origin = null,
                            x = globalX,
                            y = globalY,
                            velocity = it.velocity
                        )
                    )

                    WorkspaceRepository.samplingChain.signalEnter(midiSignals)
                    AutoPlayRepository.onMidiInput(midiSignals)

                    WorkspaceRepository.lightsChain.signalEnter(
                        Signal.LED(
                            origin = null,
                            x = globalX,
                            y = globalY,
                            color = if (it.velocity == 0) Color.Black else Color.White,
                            layer = 0
                        )
                    )
                }
            }
        }
    }

    private fun LaunchpadDeviceType.mapLaunchpadDevice(output: MidiOutput): LaunchpadDevice? {
        return when (this) {
            LaunchpadDeviceType.LAUNCHPAD_PRO_MK3 -> LaunchpadDeviceProMk3(output)
            LaunchpadDeviceType.LAUNCHPAD_X -> LaunchpadDeviceX(output)
            LaunchpadDeviceType.LAUNCHPAD_MINI_MK3 -> LaunchpadDeviceMiniMk3(output)
            LaunchpadDeviceType.LAUNCHPAD_PRO -> LaunchpadDevicePro(output)
            LaunchpadDeviceType.LAUNCHPAD_PRO_CFW -> LaunchpadDevicePro(output, true)
            LaunchpadDeviceType.LAUNCHPAD_MK2 -> LaunchpadDeviceMK2(output)
            LaunchpadDeviceType.MYSTRIX -> LaunchpadDeviceMystrix(output)
        }
    }

    fun autoConfigureDevices() {
        if (autoConfigureJob?.isActive == true) return

        val allDevices = Heaven.devices
        if (allDevices.isEmpty()) return

        val inputs = midiAccess.inputs.toList()
        val outputs = midiAccess.outputs.toList()

        for (device in allDevices) {
            val inputValid = device.deviceConfig.input?.details?.let { details ->
                inputs.any { it.id == details.id || (details.name != null && it.name == details.name) }
            } ?: false
            val outputValid = device.deviceConfig.launchpadDevice?.midiOutput?.details?.let { details ->
                outputs.any { it.id == details.id || (details.name != null && it.name == details.name) }
            } ?: false

            val incomplete = (device.deviceConfig.input != null && device.deviceConfig.launchpadDevice == null) ||
                             (device.deviceConfig.input == null && device.deviceConfig.launchpadDevice != null)

            if ((device.deviceConfig.input != null && !inputValid) ||
                (device.deviceConfig.launchpadDevice != null && !outputValid) ||
                incomplete) {
                device.deviceConfig.input?.close()
                device.deviceConfig.launchpadDevice?.midiOutput?.close()
                device.deviceConfig = ProjectDeviceConfig()
            }
        }

        val claimedInputs = allDevices.mapNotNull { it.deviceConfig.input?.details?.id }.toSet()
        val claimedOutputs = allDevices.mapNotNull { it.deviceConfig.launchpadDevice?.midiOutput?.details?.id }.toSet()

        val disconnectedDevices = allDevices.filter { device ->
            device.deviceConfig.input == null || device.deviceConfig.launchpadDevice == null
        }
        if (disconnectedDevices.isEmpty()) return

        autoConfigureJob = midiInScope.launch {
            val availableInputs = inputs.filterNot { it.id in claimedInputs }.toMutableList()
            val availableOutputs = outputs.filterNot { it.id in claimedOutputs }.toMutableList()

            for (device in disconnectedDevices) {
                val targetInputId = device.savedInputPortId
                val targetInputName = device.savedInputPortName
                val targetOutputId = device.savedOutputPortId
                val targetOutputName = device.savedOutputPortName

                if (targetInputId == null && targetInputName == null &&
                    targetOutputId == null && targetOutputName == null) {
                    continue
                }

                val matchedInput = if (targetInputId != null) {
                    availableInputs.firstOrNull { it.id == targetInputId }
                } else null
                val matchedInputFinal = matchedInput ?: if (targetInputName != null) {
                    availableInputs.firstOrNull { it.name == targetInputName }
                } else null

                val matchedOutput = if (targetOutputId != null) {
                    availableOutputs.firstOrNull { it.id == targetOutputId }
                } else null
                val matchedOutputFinal = matchedOutput ?: if (targetOutputName != null) {
                    availableOutputs.firstOrNull { it.name == targetOutputName }
                } else null

                if (matchedInputFinal != null || matchedOutputFinal != null) {
                    val inputDevice = matchedInputFinal?.let { runCatching { midiAccess.openInput(it.id) }.getOrNull() }
                    val outputDevice = matchedOutputFinal?.let { runCatching { midiAccess.openOutput(it.id) }.getOrNull() }

                    var deviceType: LaunchpadDeviceType? = null
                    if (inputDevice != null && outputDevice != null) {
                        deviceType = detect(inputDevice, outputDevice)
                    }

                    inputDevice?.setMessageReceivedListener { bytes, _, _, _ ->
                        val msgCopy = bytes.copyOf()
                        if (platform is Platform.iOS) {
                            msgCopy.toList().chunked(3).forEach {
                                device.onMidiMessage(it.toByteArray())
                            }
                        } else {
                            device.onMidiMessage(msgCopy)
                        }
                    }

                    deviceConfigMutex.withLock {
                        device.deviceConfig.input?.close()
                        device.deviceConfig.launchpadDevice?.midiOutput?.close()

                        device.deviceConfig = device.deviceConfig.copy(
                            input = inputDevice,
                            launchpadDevice = outputDevice?.let { output ->
                                deviceType?.mapLaunchpadDevice(output)
                            }
                        )
                    }

                    matchedInputFinal?.let { availableInputs.remove(it) }
                    matchedOutputFinal?.let { availableOutputs.remove(it) }
                }
            }

            if (allDevices.size == 1 &&
                allDevices[0].deviceConfig.input == null &&
                allDevices[0].deviceConfig.launchpadDevice == null) {

                val device = allDevices[0]
                val targetInputId = device.savedInputPortId
                val targetInputName = device.savedInputPortName
                val targetOutputId = device.savedOutputPortId
                val targetOutputName = device.savedOutputPortName

                if (targetInputId == null && targetInputName == null &&
                    targetOutputId == null && targetOutputName == null) {

                    if (availableInputs.isNotEmpty() && availableOutputs.isNotEmpty()) {
                        val detection = shotgunDetectLaunchpad()
                        detection?.let {
                            changeDeviceConfig(
                                WorkspaceContract.Event.OnChangeDeviceConfig(
                                    uuid = device.selectionUUID,
                                    inputPort = it.inputPort,
                                    outputPort = it.outputPort,
                                )
                            )
                        }
                    }
                }
            }
        }.apply {
            invokeOnCompletion { autoConfigureJob = null }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun shotgunDetectLaunchpad(): DetectedLaunchpad? {
        val inputs = midiAccess.inputs.toList()
        val outputs = midiAccess.outputs.toList()
        if (inputs.isEmpty() || outputs.isEmpty()) return null

        for (outputPort in outputs) {
            val openedInputs = mutableListOf<Pair<MidiPortDetails, MidiInput>>()
            val output = runCatching { midiAccess.openOutput(outputPort.id) }.getOrNull() ?: continue
            var detected: DetectedLaunchpad? = null

            try {
                inputs.forEach { inputPort ->
                    val input = runCatching { midiAccess.openInput(inputPort.id) }.getOrNull() ?: return@forEach

                    input.setMessageReceivedListener { data, _, _, _ ->
                        val type = getDeviceTypeByInquiry(data) ?: return@setMessageReceivedListener

                        if (detected == null) {
                            detected = DetectedLaunchpad(inputPort, outputPort, type)
                        }
                    }

                    openedInputs.add(inputPort to input)
                }

                output.send(
                    mevent = ubyteArrayOf(240u, 126u, 127u, 6u, 1u, 247u).toByteArray(),
                    offset = 0,
                    length = 6,
                    timestampInNanoseconds = 0,
                )

                withTimeoutOrNull(2000) {
                    while (isActive && detected == null) {
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                println("Shotgun auto-detection failed for output ${outputPort.name}: ${e.message}")
            } finally {
                openedInputs.forEach { it.second.close() }
                output.close()
            }

            if (detected != null) return detected
        }

        return null
    }
}

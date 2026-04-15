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
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevicePush2
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceX
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import dev.atsushieno.ktmidi.MidiPortDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * # Amethyst Midi Manager
 *
 * The Amethyst Midi Manager should be able to recognize device types based on input and output device.
 */
class AmethystMidiManager {
    private val midiAccess: MidiAccess = platformMidiAccess ?: EmptyMidiAccess()

    val midiInScope = CoroutineScope(Dispatchers.IO.limitedParallelism(4))
    private var autoDetectJob: Job? = null
    private var monitorJob: Job? = null

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
        LaunchpadDeviceType.ABLETON_PUSH_2 to { LaunchpadDevicePush2.identify(it) },
    )

    @OptIn(ExperimentalUnsignedTypes::class)
    fun detect(input: MidiInput, output: MidiOutput): LaunchpadDeviceType? {
        val deviceInquiry = ubyteArrayOf(
            240u,
            126u,
            127u,
            6u,
            1u,
            247u
        )

        var deviceType: LaunchpadDeviceType? = null
        var wait = true
        var waitCounter = 0
        val timeout = 2000
        
        val listener: (ByteArray, Int, Long, Long) -> Unit = { data, _, _, _ ->
            val detectedType = getDeviceTypeByInquiry(data)
            if (detectedType != null) {
                deviceType = detectedType
                wait = false
            }
        }

        input.setMessageReceivedListener(
            listener = { data, _, _, _ ->
                listener(data, 0, 0, 0)
            }
        )

        output.send(
            mevent = deviceInquiry.toByteArray(),
            offset = 0,
            length = deviceInquiry.size,
            timestampInNanoseconds = 0,
        )

        runBlocking {
            while (wait && waitCounter < timeout) {
                waitCounter += 1
                delay(1)
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

            midiInScope.launch {
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
                    deviceType = detect(inputDevice, outputDevice)
                }

                inputDevice?.setMessageReceivedListener { bytes, _, _, _ ->
                    val msgCopy = bytes.copyOf()

                    if (platform is Platform.iOS) {
                        msgCopy.toList().chunked(3).forEach {
                            onMidiMessage(it.toByteArray(), deviceType)
                        }
                    } else {
                        onMidiMessage(msgCopy, deviceType)
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

    fun startAutoDetectLoop() {
        if (monitorJob?.isActive == true) return

        monitorJob = midiInScope.launch {
            while (isActive) {
                autoConfigureSingleDeviceIfPossible()
                delay(2000)
            }
        }
    }

    fun stopAutoDetectLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun LaunchpadViewportElement.onMidiMessage(msg: ByteArray, deviceType: LaunchpadDeviceType?) {
        midiInScope.launch {
            val data = if (deviceType == LaunchpadDeviceType.ABLETON_PUSH_2) {
                LaunchpadDevicePush2.getMidiInputData(msg)
            } else {
                getMidiInputData(msg)
            }

            data?.let {
                if (WorkspaceRepository.mode.value.claimInputs) {
                    val offset = position.value.copy(
                        x = position.value.x - layout.offsetX,
                        y = position.value.y - layout.offsetY
                    )

                    WorkspaceRepository.mode.value.onMidiInput(it, offset).invoke()
                } else {
                    val offset = position.value.copy(
                        x = position.value.x - layout.offsetX,
                        y = position.value.y - layout.offsetY
                    )

                    val x = it.pitch % 10
                    val y = it.pitch / 10
                    val posX = offset.x.toInt()
                    val posY = offset.y.toInt()
                    val globalX = posX + x
                    val globalY = posY + (9 - y)

                    if (it.velocity != 0) {
                        AutomappingManager.tryCommitPadMapping(
                            device = this@onMidiMessage,
                            globalX = globalX,
                            globalY = globalY,
                        )
                    }

                    WorkspaceRepository.samplingChain.signalEnter(
                        Signal.Midi(
                            origin = null,
                            x = globalX,
                            y = globalY,
                            velocity = it.velocity
                        )
                    )

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
            LaunchpadDeviceType.ABLETON_PUSH_2 -> LaunchpadDevicePush2(output)
        }
    }

    fun autoConfigureSingleDeviceIfPossible() {
        if (autoDetectJob?.isActive == true) return

        val device = Heaven.devices.singleOrNull() ?: return

        val inputValid = device.deviceConfig.input?.details?.id?.let { id ->
            midiAccess.inputs.any { it.id == id }
        } ?: false
        val outputValid = device.deviceConfig.launchpadDevice?.midiOutput?.details?.id?.let { id ->
            midiAccess.outputs.any { it.id == id }
        } ?: false

        if (!inputValid || !outputValid) {
            device.deviceConfig.input?.close()
            device.deviceConfig.launchpadDevice?.midiOutput?.close()
            device.deviceConfig = ProjectDeviceConfig()
        }

        if (device.deviceConfig.input != null && device.deviceConfig.launchpadDevice != null) return
        if (midiAccess.inputs.count() == 0 || midiAccess.outputs.count() == 0) return

        autoDetectJob = midiInScope.launch {
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
        }.apply {
            invokeOnCompletion { autoDetectJob = null }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun shotgunDetectLaunchpad(): DetectedLaunchpad? {
        val inputs = midiAccess.inputs
        val outputs = midiAccess.outputs
        if (inputs.count() == 0 || outputs.count() == 0) return null

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

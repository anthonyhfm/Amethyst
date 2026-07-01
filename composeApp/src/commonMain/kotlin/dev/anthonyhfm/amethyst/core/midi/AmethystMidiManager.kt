package dev.anthonyhfm.amethyst.core.midi

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.getMidiInputData
import dev.anthonyhfm.amethyst.core.midi.devices.*
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.rotateMidiCoordinate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AmethystMidiDeviceDetails(
    val id: String,
    val friendlyName: String,
    val type: LaunchpadDeviceType
)

class AmethystMidiManager {
    private val midiAccess: AmethystMidiAccess? = platformMidiAccess

    val midiInScope = CoroutineScope(Dispatchers.IO.limitedParallelism(4))
    private var monitorJob: Job? = null
    private val rescanMutex = Mutex()
    private val elementCollectorJobs = mutableMapOf<String, Job>()
    private val activeConnections = mutableMapOf<String, ActiveDeviceConnection>()

    private class ActiveDeviceConnection(
        val device: AmethystMidiDevice,
        val input: AmethystMidiInput?,
        val output: AmethystMidiOutput?,
        val detectedType: LaunchpadDeviceType?,
        var friendlyName: String
    )

    companion object {
        private val _detectedDevices = MutableStateFlow<List<AmethystMidiDeviceDetails>>(emptyList())
        val detectedDevices: StateFlow<List<AmethystMidiDeviceDetails>> = _detectedDevices.asStateFlow()
    }

    fun close() {
        stopAutoDetectLoop()
        midiInScope.cancel()
        activeConnections.values.forEach { conn ->
            conn.input?.close()
            conn.output?.close()
        }
        activeConnections.clear()
        elementCollectorJobs.values.forEach { it.cancel() }
        elementCollectorJobs.clear()
    }

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
    fun getDeviceTypeByInquiry(data: ByteArray): LaunchpadDeviceType? {
        val convertedData = data.toUByteArray()
        val messageStart = convertedData.indexOf(240u)
        val messageEnd = convertedData.indexOf(247u)

        if (messageStart == -1 || messageEnd == -1) return null

        val sysex = convertedData.copyOfRange(messageStart, messageEnd + 1)

        if (sysex.size > 1 && sysex[1] == 126.toUByte()) {
            return inquiryTests.entries.find {
                it.value(sysex)
            }?.key
        }

        return null
    }

    private data class DetectedTypeAndPorts(
        val type: LaunchpadDeviceType,
        val inputConnection: AmethystMidiInput,
        val outputConnection: AmethystMidiOutput
    )

    private suspend fun detectDeviceType(device: AmethystMidiDevice): DetectedTypeAndPorts? {
        val inputs = device.inputPorts
        val outputs = device.outputPorts
        if (inputs.isEmpty() || outputs.isEmpty()) return null

        var detected: DetectedTypeAndPorts? = null

        for (outputPort in outputs) {
            val openedInputs = mutableListOf<AmethystMidiInput>()
            val outputConnection = runCatching { midiAccess?.openOutput(outputPort.id) }.getOrNull() ?: continue

            try {
                for (inputPort in inputs) {
                    val inputConnection = runCatching { midiAccess?.openInput(inputPort.id) }.getOrNull() ?: continue
                    openedInputs.add(inputConnection)
                }

                val jobs = openedInputs.map { conn ->
                    CoroutineScope(Dispatchers.IO).launch {
                        conn.messages.collect { msg ->
                            val type = getDeviceTypeByInquiry(msg)
                            if (type != null && detected == null) {
                                detected = DetectedTypeAndPorts(type, conn, outputConnection)
                            }
                        }
                    }
                }

                outputConnection.sendDeviceInquiry()

                withTimeoutOrNull(1000) {
                    while (detected == null && isActive) {
                        delay(10)
                    }
                }

                jobs.forEach { it.cancel() }
            } catch (e: Exception) {
                println("Error during shotgun detection on output ${outputPort.name}: ${e.message}")
            } finally {
                for (conn in openedInputs) {
                    if (detected == null || detected.inputConnection.portId != conn.portId) {
                        conn.close()
                    }
                }
                if (detected == null || detected.outputConnection.portId != outputConnection.portId) {
                    outputConnection.close()
                }
            }

            if (detected != null) {
                return detected
            }
        }

        return null
    }

    private fun updateDetectedDevicesList() {
        val list = mutableListOf<AmethystMidiDeviceDetails>()
        val groups = activeConnections.values.groupBy { it.detectedType }
        for ((type, conns) in groups) {
            if (type == null) continue
            val sortedConns = conns.sortedBy { it.device.id }
            if (sortedConns.size > 1) {
                sortedConns.forEachIndexed { index, conn ->
                    val name = "${type.label} #${index + 1}"
                    conn.friendlyName = name
                    list.add(AmethystMidiDeviceDetails(conn.device.id, name, type))
                }
            } else if (sortedConns.size == 1) {
                val conn = sortedConns.first()
                val name = type.label
                conn.friendlyName = name
                list.add(AmethystMidiDeviceDetails(conn.device.id, name, type))
            }
        }
        _detectedDevices.value = list
    }

    private fun autoConnectDevice(active: ActiveDeviceConnection) {
        val element = Heaven.devices.find {
            it.savedInputPortId == active.device.id ||
            (it.savedInputPortId == null && it.savedInputPortName == active.friendlyName)
        } ?: if (Heaven.devices.size == 1 && activeConnections.size == 1) {
            val single = Heaven.devices.first()
            if (single.savedInputPortId == null && single.savedInputPortName == null) {
                single
            } else null
        } else null

        if (element == null) return
        if (element.launchpadDevice?.connection?.input?.portId == active.input?.portId) return

        connectElement(element, active)
    }

    private fun connectElement(element: LaunchpadViewportElement, active: ActiveDeviceConnection) {
        elementCollectorJobs[element.selectionUUID]?.cancel()

        val input = active.input
        val output = active.output
        val type = active.detectedType

        if (input != null && output != null && type != null) {
            val conn = AmethystMidiDeviceConnection(active.device, input, output)
            val launchpadDevice = type.mapLaunchpadDevice(conn)

            val job = midiInScope.launch {
                input.messages.collect { msg ->
                    val msgCopy = msg.copyOf()
                    if (platform is Platform.iOS) {
                        msgCopy.toList().chunked(3).forEach {
                            element.onMidiMessage(it.toByteArray())
                        }
                    } else {
                        element.onMidiMessage(msgCopy)
                    }
                }
            }

            elementCollectorJobs[element.selectionUUID] = job
            element.launchpadDevice = launchpadDevice
            element.savedInputPortId = active.device.id
            element.savedInputPortName = active.friendlyName
        }
    }

    fun changeDeviceConfig(event: WorkspaceContract.Event.OnChangeDeviceConfig) {
        val element = Heaven.devices.find { it.selectionUUID == event.uuid } ?: return

        elementCollectorJobs[element.selectionUUID]?.cancel()
        elementCollectorJobs.remove(element.selectionUUID)
        element.launchpadDevice = null

        val deviceId = event.deviceId
        if (deviceId == null) {
            element.savedInputPortId = null
            element.savedInputPortName = null
            element.savedOutputPortId = null
            element.savedOutputPortName = null
            return
        }

        val active = activeConnections[deviceId]
        if (active != null) {
            connectElement(element, active)
        } else {
            element.savedInputPortId = deviceId
            element.savedInputPortName = null
        }
    }

    fun startAutoDetectLoop() {
        if (monitorJob?.isActive == true) return

        val access = midiAccess ?: return
        monitorJob = midiInScope.launch {
            val nativeChangeJob = launch {
                access.deviceChanges.collect {
                    runCatching { rescanDevicesSerially() }
                }
            }

            try {
                runCatching { rescanDevicesSerially() }

                while (isActive) {
                    delay(2000)
                    runCatching { rescanDevicesSerially() }
                }
            } finally {
                nativeChangeJob.cancel()
            }
        }
    }

    fun stopAutoDetectLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun rescanDevicesSerially() {
        rescanMutex.withLock {
            rescanDevices()
        }
    }

    private suspend fun rescanDevices() {
        val access = midiAccess ?: return
        val discovered = access.discoverDevices()
        val discoveredIds = discovered.map { it.id }.toSet()

        val deadDeviceIds = activeConnections.filter { it.value.input?.isOpen == false || it.value.output?.isOpen == false }.keys
        val disconnectedIds = activeConnections.keys.filter { it !in discoveredIds } + deadDeviceIds
        for (id in disconnectedIds.distinct()) {
            val conn = activeConnections.remove(id)
            if (conn != null) {
                conn.input?.close()
                conn.output?.close()
                Heaven.devices.forEach { element ->
                    if (element.launchpadDevice?.connection?.input?.portId == conn.input?.portId) {
                        elementCollectorJobs[element.selectionUUID]?.cancel()
                        elementCollectorJobs.remove(element.selectionUUID)
                        element.launchpadDevice = null
                    }
                }
            }
        }

        val newDevices = discovered.filter { it.id !in activeConnections }
        for (device in newDevices) {
            val detection = detectDeviceType(device)
            if (detection != null) {
                val conn = ActiveDeviceConnection(
                    device = device,
                    input = detection.inputConnection,
                    output = detection.outputConnection,
                    detectedType = detection.type,
                    friendlyName = detection.type.label
                )
                activeConnections[device.id] = conn
            }
        }

        updateDetectedDevicesList()

        for (conn in activeConnections.values) {
            autoConnectDevice(conn)
        }
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

    private fun LaunchpadDeviceType.mapLaunchpadDevice(connection: AmethystMidiDeviceConnection): LaunchpadDevice? {
        return when (this) {
            LaunchpadDeviceType.LAUNCHPAD_PRO_MK3 -> LaunchpadDeviceProMk3(connection)
            LaunchpadDeviceType.LAUNCHPAD_X -> LaunchpadDeviceX(connection)
            LaunchpadDeviceType.LAUNCHPAD_MINI_MK3 -> LaunchpadDeviceMiniMk3(connection)
            LaunchpadDeviceType.LAUNCHPAD_PRO -> LaunchpadDevicePro(connection)
            LaunchpadDeviceType.LAUNCHPAD_PRO_CFW -> LaunchpadDevicePro(connection, true)
            LaunchpadDeviceType.LAUNCHPAD_MK2 -> LaunchpadDeviceMK2(connection)
            LaunchpadDeviceType.MYSTRIX -> LaunchpadDeviceMystrix(connection)
        }
    }
}

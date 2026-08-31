package dev.anthonyhfm.amethyst.core.midi

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.midi.devices.*
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.ViewportRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.rotateMidiCoordinate
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class AmethystMidiDeviceDetails(
    val id: String,
    val friendlyName: String,
    val type: LaunchpadDeviceType
)

data class LaunchpadDeviceIdentification(
    val type: LaunchpadDeviceType,
    val firmware: LaunchpadFirmware,
)

class AmethystMidiManager(
    private val midiAccess: AmethystMidiAccess? = platformMidiAccess,
    private val closeMidiAccessOnClose: Boolean = false,
) {

    val midiInScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(4))
    private var monitorJob: Job? = null
    private val rescanMutex = Mutex()
    private val elementCollectorJobs = mutableMapOf<String, Job>()
    private val activeConnections = mutableMapOf<String, ActiveDeviceConnection>()
    private val pendingSingleCoverUuid = atomic<String?>(null)

    private class ActiveDeviceConnection(
        val device: AmethystMidiDevice,
        val input: AmethystMidiInput?,
        val output: AmethystMidiOutput?,
        val detectedType: LaunchpadDeviceType?,
        val detectedFirmware: LaunchpadFirmware,
        var friendlyName: String
    )

    companion object {
        private const val RESCAN_INTERVAL_MS = 1_000L

        private val _detectedDevices = MutableStateFlow<List<AmethystMidiDeviceDetails>>(emptyList())
        val detectedDevices: StateFlow<List<AmethystMidiDeviceDetails>> = _detectedDevices.asStateFlow()
    }

    private fun workspaceDevices(): List<LaunchpadViewportElement> =
        ViewportRepository.devices.value

    fun close() {
        stopAutoDetectLoop()
        detachAllWorkspaceDevices()
        activeConnections.values.forEach { conn ->
            printConnectionState("Disconnected", conn)
            conn.input?.close()
            conn.output?.close()
        }
        activeConnections.clear()
        if (closeMidiAccessOnClose) {
            midiAccess?.close()
        }
        midiInScope.cancel()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    val inquiryTests: Map<LaunchpadDeviceType, (UByteArray) -> Boolean> = mapOf(
        LaunchpadDeviceType.LAUNCHPAD_PRO_MK3 to { LaunchpadDeviceProMk3.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_X to { LaunchpadDeviceX.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_MINI_MK3 to { LaunchpadDeviceMiniMk3.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_PRO to { LaunchpadDevicePro.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_MK2 to { LaunchpadDeviceMK2.identify(it) },
        LaunchpadDeviceType.MYSTRIX to { LaunchpadDeviceMystrix.identify(it) },
        LaunchpadDeviceType.MIDI_FIGHTER_64 to { LaunchpadDeviceMidiFighter.identify(it) },
    )

    @OptIn(ExperimentalUnsignedTypes::class)
    fun getDeviceIdentificationByInquiry(data: ByteArray): LaunchpadDeviceIdentification? {
        val convertedData = data.toUByteArray()
        val messageStart = convertedData.indexOf(240u)
        if (messageStart == -1) return null
        val messageEnd = (messageStart + 1 until convertedData.size)
            .firstOrNull { convertedData[it] == 247.toUByte() }
            ?: return null

        val sysex = convertedData.copyOfRange(messageStart, messageEnd + 1)
        if (sysex.size <= 1 || sysex[1] != 126.toUByte()) return null

        val type = inquiryTests.entries.firstOrNull { it.value(sysex) }?.key ?: return null
        val revision = sysex.copyOfRange(sysex.lastIndex - 4, sysex.lastIndex)
        return LaunchpadDeviceIdentification(type, identifyFirmware(type, revision))
    }

    fun getDeviceTypeByInquiry(data: ByteArray): LaunchpadDeviceType? {
        return getDeviceIdentificationByInquiry(data)?.type
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun identifyFirmware(
        type: LaunchpadDeviceType,
        revision: UByteArray,
    ): LaunchpadFirmware {
        // CoreFW's standard Device Inquiry marker is documented in references/firmware.md.
        if (
            type != LaunchpadDeviceType.MYSTRIX &&
            revision.contentEquals(ubyteArrayOf(0u, 9u, 9u, 9u))
        ) {
            return LaunchpadFirmware.CoreFW
        }

        val isMat1jaczyyy = when (type) {
            LaunchpadDeviceType.LAUNCHPAD_X ->
                revision.contentEquals(ubyteArrayOf(0u, 3u, 5u, 2u))
            LaunchpadDeviceType.LAUNCHPAD_MINI_MK3 ->
                revision.contentEquals(ubyteArrayOf(0u, 4u, 0u, 8u))
            LaunchpadDeviceType.LAUNCHPAD_MK2 ->
                revision.contentEquals(ubyteArrayOf(0u, 1u, 7u, 2u))
            LaunchpadDeviceType.LAUNCHPAD_PRO ->
                revision.contentEquals(ubyteArrayOf(0u, 99u, 102u, 121u))
            LaunchpadDeviceType.MIDI_FIGHTER_64 -> true
            else -> false
        }

        return if (isMat1jaczyyy) {
            LaunchpadFirmware.Mat1jaczyyy
        } else {
            LaunchpadFirmware.Original
        }
    }

    private data class DetectedTypeAndPorts(
        val type: LaunchpadDeviceType,
        val firmware: LaunchpadFirmware,
        val inputConnection: AmethystMidiInput,
        val outputConnection: AmethystMidiOutput
    )

    private suspend fun detectDeviceType(device: AmethystMidiDevice): DetectedTypeAndPorts? {
        val inputs = device.inputPorts
        val outputs = device.outputPorts
        if (inputs.isEmpty() || outputs.isEmpty()) return null

        for (outputPort in outputs) {
            val openedInputs = mutableListOf<AmethystMidiInput>()
            val outputConnection = runCatching { midiAccess?.openOutput(outputPort.id) }.getOrNull() ?: continue
            var detected: DetectedTypeAndPorts? = null

            try {
                for (inputPort in inputs) {
                    val inputConnection = runCatching { midiAccess?.openInput(inputPort.id) }.getOrNull() ?: continue
                    openedInputs.add(inputConnection)
                }

                detected = coroutineScope {
                    val response = CompletableDeferred<DetectedTypeAndPorts>()
                    val jobs = openedInputs.map { conn ->
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            conn.messages.collect { msg ->
                                val identification = getDeviceIdentificationByInquiry(msg)
                                if (identification != null) {
                                    response.complete(
                                        DetectedTypeAndPorts(
                                            identification.type,
                                            identification.firmware,
                                            conn,
                                            outputConnection,
                                        )
                                    )
                                }
                            }
                        }
                    }

                    try {
                        outputConnection.sendDeviceInquiry()
                        withTimeoutOrNull(1000) { response.await() }
                    } finally {
                        jobs.forEach { it.cancelAndJoin() }
                    }
                }
            } catch (e: CancellationException) {
                throw e
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

            val detection = detected ?: continue
            return if (detection.firmware == LaunchpadFirmware.CoreFW) {
                rebindCoreFwToSecondPorts(device, detection)
            } else {
                detection
            }
        }

        return null
    }

    private suspend fun rebindCoreFwToSecondPorts(
        device: AmethystMidiDevice,
        detection: DetectedTypeAndPorts,
    ): DetectedTypeAndPorts? {
        val access = midiAccess ?: return null
        val inputPort = device.inputPorts.sortedBy { it.portNumber }.getOrNull(1)
        val outputPort = device.outputPorts.sortedBy { it.portNumber }.getOrNull(1)

        if (inputPort == null || outputPort == null) {
            detection.inputConnection.close()
            detection.outputConnection.close()
            println("CoreFW device ${device.name} does not expose a second MIDI input and output")
            return null
        }

        val inputConnection = if (detection.inputConnection.portId == inputPort.id) {
            detection.inputConnection
        } else {
            runCatching { access.openInput(inputPort.id) }.getOrNull()
        }
        if (inputConnection == null) {
            detection.inputConnection.close()
            detection.outputConnection.close()
            println("Could not open CoreFW second MIDI input ${inputPort.name}")
            return null
        }

        val outputConnection = if (detection.outputConnection.portId == outputPort.id) {
            detection.outputConnection
        } else {
            runCatching { access.openOutput(outputPort.id) }.getOrNull()
        }
        if (outputConnection == null) {
            if (inputConnection !== detection.inputConnection) inputConnection.close()
            detection.inputConnection.close()
            detection.outputConnection.close()
            println("Could not open CoreFW second MIDI output ${outputPort.name}")
            return null
        }

        if (inputConnection !== detection.inputConnection) detection.inputConnection.close()
        if (outputConnection !== detection.outputConnection) detection.outputConnection.close()

        return detection.copy(
            inputConnection = inputConnection,
            outputConnection = outputConnection,
        )
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

    private fun autoConnectDevice(
        active: ActiveDeviceConnection,
        elements: List<LaunchpadViewportElement> = workspaceDevices(),
    ) {
        val element = elements.find {
            it.savedMidiDeviceId == active.device.id ||
                it.savedInputPortId == active.device.id ||
                it.savedInputPortId == active.input?.portId ||
                it.savedOutputPortId == active.output?.portId ||
                (
                    it.savedMidiDeviceId == null &&
                        it.savedInputPortId == null &&
                        it.savedInputPortName == active.friendlyName
                )
        } ?: if (elements.size == 1 && activeConnections.size == 1) {
            val single = elements.first()
            if (
                single.savedMidiDeviceId == null &&
                single.savedInputPortId == null &&
                single.savedInputPortName == null
            ) {
                single
            } else null
        } else null

        if (element == null) return
        val current = element.launchpadDevice?.connection
        if (
            current != null &&
            current.input === active.input &&
            current.output === active.output &&
            current.input.isOpen &&
            current.output.isOpen
        ) {
            return
        }

        connectElement(element, active)
    }

    private fun connectElement(element: LaunchpadViewportElement, active: ActiveDeviceConnection) {
        detachElement(element)

        val input = active.input
        val output = active.output
        val type = active.detectedType

        if (input != null && output != null && type != null) {
            val conn = AmethystMidiDeviceConnection(active.device, input, output)
            val launchpadDevice = type.mapLaunchpadDevice(conn, active.detectedFirmware)

            val job = midiInScope.launch {
                input.messages.collect { msg ->
                    element.onMidiMessage(msg.copyOf())
                }
            }

            elementCollectorJobs[element.selectionUUID] = job
            element.launchpadDevice = launchpadDevice
            element.savedMidiDeviceId = active.device.id
            element.savedInputPortId = input.portId
            element.savedOutputPortId = output.portId
            element.savedInputPortName = active.friendlyName
            element.savedOutputPortName = active.friendlyName
            if (workspaceDevices().singleOrNull() === element) {
                pendingSingleCoverUuid.value = null
            }
            element.sendFullMidiSnapshot()
        }
    }

    fun changeDeviceConfig(uuid: String, deviceId: String?) {
        val elements = workspaceDevices()
        val element = elements.find { it.selectionUUID == uuid } ?: return

        if (elements.singleOrNull() === element) {
            pendingSingleCoverUuid.value = null
        }
        detachElement(element)

        if (deviceId == null) {
            element.savedMidiDeviceId = null
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
            element.savedMidiDeviceId = deviceId
            element.savedInputPortId = null
            element.savedInputPortName = null
            element.savedOutputPortId = null
            element.savedOutputPortName = null
        }
    }

    fun detachElement(element: LaunchpadViewportElement) {
        if (pendingSingleCoverUuid.value == element.selectionUUID) {
            pendingSingleCoverUuid.value = null
        }
        elementCollectorJobs.remove(element.selectionUUID)?.cancel()
        element.launchpadDevice?.close()
        element.launchpadDevice = null
    }

    fun detachAllWorkspaceDevices() {
        pendingSingleCoverUuid.value = null
        workspaceDevices().forEach(::detachElement)
        elementCollectorJobs.values.forEach { it.cancel() }
        elementCollectorJobs.clear()
    }

    fun refreshConnections() {
        if (!midiInScope.isActive) return
        midiInScope.launch {
            rescanAndReport("MIDI rescan failed")
        }
    }

    fun startAutoDetectLoop() {
        if (monitorJob?.isActive == true) return

        val access = midiAccess ?: return
        monitorJob = midiInScope.launch {
            val nativeChangeJob = launch(start = CoroutineStart.UNDISPATCHED) {
                while (isActive) {
                    try {
                        access.deviceChanges.conflate().collect {
                            rescanAndReport("MIDI hotplug rescan failed")
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        println("MIDI hotplug monitor failed: ${exception.message}")
                    }
                    if (isActive) delay(RESCAN_INTERVAL_MS)
                }
            }

            try {
                while (isActive) {
                    rescanAndReport("MIDI health-check failed")
                    delay(RESCAN_INTERVAL_MS)
                }
            } finally {
                nativeChangeJob.cancelAndJoin()
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

    private suspend fun rescanAndReport(failureMessage: String) {
        try {
            rescanDevicesSerially()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("$failureMessage: ${exception.message}")
        }
    }

    private suspend fun rescanDevices() {
        val access = midiAccess ?: return
        val elements = workspaceDevices()
        val discovered = access.discoverDevices()
        val discoveredById = discovered.associateBy { it.id }
        val discoveredIds = discoveredById.keys
        val connectedDevices = mutableListOf<ActiveDeviceConnection>()

        val deadDeviceIds = activeConnections.filter { (id, active) ->
            val current = discoveredById[id]
            val activePortIds = active.device.ports.map { it.id }.toSet()
            val currentPortIds = current?.ports?.map { it.id }?.toSet()
            active.input?.isOpen == false ||
                active.output?.isOpen == false ||
                currentPortIds != null && currentPortIds != activePortIds
        }.keys
        val disconnectedIds = activeConnections.keys.filter { it !in discoveredIds } + deadDeviceIds
        for (id in disconnectedIds.distinct()) {
            val conn = activeConnections.remove(id)
            if (conn != null) {
                val attachedElements = elements.filter { element ->
                    val connection = element.launchpadDevice?.connection
                    connection?.device?.id == conn.device.id ||
                        connection?.input?.portId == conn.input?.portId ||
                        connection?.output?.portId == conn.output?.portId
                }
                val waitForSingleCoverReplacement =
                    elements.size == 1 &&
                    attachedElements.singleOrNull() === elements.first()

                printConnectionState("Disconnected", conn)
                attachedElements.forEach(::detachElement)
                if (waitForSingleCoverReplacement) {
                    pendingSingleCoverUuid.value = elements.first().selectionUUID
                }
                runCatching { conn.input?.close() }
                    .onFailure { println("MIDI input close failed: ${it.message}") }
                runCatching { conn.output?.close() }
                    .onFailure { println("MIDI output close failed: ${it.message}") }
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
                    detectedFirmware = detection.firmware,
                    friendlyName = detection.type.label
                )
                activeConnections[device.id] = conn
                connectedDevices += conn
            }
        }

        updateDetectedDevicesList()
        connectedDevices.forEach { printConnectionState("Connected", it) }

        for (conn in activeConnections.values) {
            autoConnectDevice(conn, elements)
        }

        val singleCover = elements.singleOrNull()
        val replacement = connectedDevices.firstOrNull()
        if (
            singleCover != null &&
            pendingSingleCoverUuid.value == singleCover.selectionUUID &&
            singleCover.launchpadDevice == null &&
            replacement != null
        ) {
            connectElement(singleCover, replacement)
        }
    }

    private fun printConnectionState(
        state: String,
        connection: ActiveDeviceConnection,
    ) {
        println("[$state] ${connection.friendlyName} - ${connection.detectedFirmware.label}")
    }

    suspend fun LaunchpadViewportElement.onMidiMessage(msg: ByteArray) {
        val input = launchpadDevice?.handleMidiInput(msg) ?: return
        val offset = position.value.copy(
            x = position.value.x - layout.offsetX,
            y = position.value.y,
        )

        if (WorkspaceRepository.mode.value.claimMidiInputs) {
            WorkspaceRepository.mode.value.onMidiInput(input, offset)
            return
        }

        val x = input.pitch % 10
        val y = input.pitch / 10
        val (visX, visY) = rotateMidiCoordinate(x, y, layout, rotationDegrees.floatValue)
        val globalX = offset.x.toInt() + visX
        val globalY = offset.y.toInt() + (9 - visY)

        if (AutomappingManager.isMappingActive()) {
            if (input.velocity != 0) {
                AutomappingManager.tryCommitPadMapping(
                    device = this,
                    globalX = globalX,
                    globalY = globalY,
                )
            }
            return
        }

        val midiSignals = listOf(
            Signal.Midi(
                origin = null,
                x = globalX,
                y = globalY,
                velocity = input.velocity,
            )
        )

        WorkspaceRepository.samplingChain.signalEnter(midiSignals)
        AutoPlayRepository.onMidiInput(midiSignals)
        WorkspaceRepository.lightsChain.signalEnter(
            Signal.LED(
                origin = null,
                x = globalX,
                y = globalY,
                color = if (input.velocity == 0) Color.Black else Color.White,
                layer = 0,
            )
        )
    }

    private fun LaunchpadDeviceType.mapLaunchpadDevice(
        connection: AmethystMidiDeviceConnection,
        firmware: LaunchpadFirmware,
    ): LaunchpadDevice {
        return when (this) {
            LaunchpadDeviceType.LAUNCHPAD_PRO_MK3 -> LaunchpadDeviceProMk3(connection, firmware)
            LaunchpadDeviceType.LAUNCHPAD_X -> LaunchpadDeviceX(connection, firmware)
            LaunchpadDeviceType.LAUNCHPAD_MINI_MK3 -> LaunchpadDeviceMiniMk3(connection, firmware)
            LaunchpadDeviceType.LAUNCHPAD_PRO -> LaunchpadDevicePro(connection, firmware)
            LaunchpadDeviceType.LAUNCHPAD_MK2 -> LaunchpadDeviceMK2(connection, firmware)
            LaunchpadDeviceType.MYSTRIX -> LaunchpadDeviceMystrix(connection, firmware)
            LaunchpadDeviceType.MIDI_FIGHTER_64 -> LaunchpadDeviceMidiFighter(connection, firmware)
        }
    }
}

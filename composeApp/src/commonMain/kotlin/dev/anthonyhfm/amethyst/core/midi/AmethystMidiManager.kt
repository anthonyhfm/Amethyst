package dev.anthonyhfm.amethyst.core.midi

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.currentSignalMacroValues
import dev.anthonyhfm.amethyst.core.midi.devices.*
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.ViewportRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.rotateMidiCoordinate
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class AmethystMidiDeviceDetails(
    val id: String,
    val friendlyName: String,
    val type: LaunchpadDeviceType
)

data class LaunchpadDeviceIdentification(
    val type: LaunchpadDeviceType,
    val firmware: LaunchpadFirmware,
)

/**
 * Owns MIDI device discovery/identification and binds discovered Launchpad-family
 * hardware to [LaunchpadViewportElement]s in the workspace.
 *
 * Thread-safety: [activeConnections], [elementCollectorJobs] and [probeBackoff] are
 * mutated both from rescan coroutines running on [midiInScope] and synchronously from
 * whatever thread calls [changeDeviceConfig]/[detachElement]/[detachAllWorkspaceDevices]/
 * [close]. All of that state is guarded by [stateLock]. Critical sections guarded by
 * [stateLock] must never suspend - any suspending work (device discovery, device
 * inquiry probing) is performed on snapshots outside the lock, then results are
 * committed back by re-entering the lock.
 */
@OptIn(ExperimentalTime::class)
class AmethystMidiManager(
    private val midiAccess: AmethystMidiAccess? = platformMidiAccess,
    private val closeMidiAccessOnClose: Boolean = false,
    private val elementsProvider: () -> List<LaunchpadViewportElement> = { ViewportRepository.devices.value },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    val midiInScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(MIDI_SCAN_PARALLELISM))
    private var monitorJob: Job? = null

    /** Serializes full rescans so overlapping triggers (timer + hotplug + manual refresh) don't race each other. */
    private val rescanMutex = Mutex()

    /** Guards [activeConnections], [elementCollectorJobs] and [probeBackoff]. Never suspend while holding it. */
    private val stateLock = SynchronizedObject()
    private val activeConnections = mutableMapOf<String, ActiveDeviceConnection>()
    private val elementCollectorJobs = mutableMapOf<String, Job>()
    private val probeBackoff = mutableMapOf<String, ProbeBackoffState>()

    private class ActiveDeviceConnection(
        val device: AmethystMidiDevice,
        val input: AmethystMidiInput?,
        val output: AmethystMidiOutput?,
        val detectedType: LaunchpadDeviceType?,
        val detectedFirmware: LaunchpadFirmware,
        var friendlyName: String
    )

    /** Per-device probe backoff state, keyed by [AmethystMidiDevice.id]. */
    private class ProbeBackoffState(
        val fingerprint: Set<String>,
        val attempt: Int,
        val nextAttemptAtMillis: Long,
    )

    private class ProbeOutcome(
        val device: AmethystMidiDevice,
        val detection: DetectedTypeAndPorts?,
    )

    companion object {
        /** Fallback full rescan (discovery + probing + binding) cadence when hotplug events don't arrive. */
        private const val FULL_RESCAN_INTERVAL_MS = 2_000L

        /** Cheap check of `isOpen` on already-active connections only (no discovery); forces a full rescan on death. */
        private const val LIVENESS_CHECK_INTERVAL_MS = 500L

        /** Retry delay if the platform's `deviceChanges` flow throws or completes unexpectedly. */
        private const val HOTPLUG_MONITOR_RETRY_DELAY_MS = 1_000L

        /** Per-output timeout while shotgun-probing a candidate device for a Device Inquiry response. */
        private const val PROBE_TIMEOUT_MS = 1_000L

        /** Max number of candidate devices probed concurrently per rescan. */
        private const val PROBE_CONCURRENCY_LIMIT = 4

        /** Non-responding-device probe backoff schedule: 1s, 2s, 4s, 8s, then capped here. */
        private const val PROBE_BACKOFF_MAX_MS = 10_000L

        private const val MIDI_SCAN_PARALLELISM = 4

        private val _detectedDevices = MutableStateFlow<List<AmethystMidiDeviceDetails>>(emptyList())
        val detectedDevices: StateFlow<List<AmethystMidiDeviceDetails>> = _detectedDevices.asStateFlow()

        private fun backoffDelayForAttempt(attempt: Int): Long = when {
            attempt <= 1 -> 1_000L
            attempt == 2 -> 2_000L
            attempt == 3 -> 4_000L
            attempt == 4 -> 8_000L
            else -> PROBE_BACKOFF_MAX_MS
        }
    }

    fun close() {
        stopAutoDetectLoop()
        detachAllWorkspaceDevices()
        val toClose = synchronized(stateLock) {
            val list = activeConnections.values.toList()
            activeConnections.clear()
            probeBackoff.clear()
            list
        }
        toClose.forEach { conn ->
            printConnectionState("Disconnected", conn)
            conn.input?.close()
            conn.output?.close()
        }
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
                        withTimeoutOrNull(PROBE_TIMEOUT_MS) { response.await() }
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

    // -- State mutation helpers. Every one of these must be called while holding [stateLock] --

    private fun updateDetectedDevicesListLocked() {
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

    private fun connectElementLocked(element: LaunchpadViewportElement, active: ActiveDeviceConnection) {
        detachElementLocked(element)

        val input = active.input
        val output = active.output
        val type = active.detectedType
        if (input == null || output == null || type == null) return

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
        element.sendFullMidiSnapshot()
    }

    private fun detachElementLocked(element: LaunchpadViewportElement) {
        elementCollectorJobs.remove(element.selectionUUID)?.cancel()
        element.launchpadDevice?.close()
        element.launchpadDevice = null
    }

    /**
     * Binds workspace elements to unclaimed active connections.
     *
     * - A single Launchpad element always ends up on a compatible connection when one
     *   exists: preferring a match on its saved ids/name, otherwise the first unclaimed
     *   connection (ordered as in [detectedDevices]) - even with stale saved ids.
     * - With multiple elements, only preference matches are used, each against an
     *   unclaimed connection; two elements never share one connection.
     * - An element with a live binding to a still-active connection keeps it.
     */
    private fun bindElementsLocked(elements: List<LaunchpadViewportElement>) {
        if (elements.isEmpty()) return

        val claimed = mutableSetOf<String>()

        for (element in elements) {
            val device = element.launchpadDevice ?: continue
            val conn = device.connection
            val active = activeConnections[conn.device.id]
            val isLive = active != null &&
                active.input?.portId == conn.input.portId &&
                active.output?.portId == conn.output.portId &&
                conn.input.isOpen &&
                conn.output.isOpen
            if (isLive) {
                claimed += conn.device.id
            } else {
                detachElementLocked(element)
            }
        }

        val orderedConnectionIds = _detectedDevices.value.map { it.id }
        fun unclaimedConnections(): List<ActiveDeviceConnection> =
            orderedConnectionIds.mapNotNull { id -> if (id !in claimed) activeConnections[id] else null }

        fun preferredMatch(
            element: LaunchpadViewportElement,
            pool: List<ActiveDeviceConnection>,
        ): ActiveDeviceConnection? = pool.find { conn ->
            element.savedMidiDeviceId == conn.device.id ||
                element.savedInputPortId == conn.input?.portId ||
                element.savedOutputPortId == conn.output?.portId ||
                (
                    element.savedMidiDeviceId == null &&
                        element.savedInputPortId == null &&
                        element.savedOutputPortId == null &&
                        element.savedInputPortName == conn.friendlyName
                    )
        }

        if (elements.size == 1) {
            val single = elements.first()
            if (single.launchpadDevice == null) {
                val pool = unclaimedConnections()
                val target = preferredMatch(single, pool) ?: pool.firstOrNull()
                if (target != null) {
                    connectElementLocked(single, target)
                    claimed += target.device.id
                }
            }
        } else {
            for (element in elements) {
                if (element.launchpadDevice != null) continue
                val target = preferredMatch(element, unclaimedConnections()) ?: continue
                connectElementLocked(element, target)
                claimed += target.device.id
            }
        }
    }

    private fun isConnectionDead(
        conn: ActiveDeviceConnection,
        discoveredById: Map<String, AmethystMidiDevice>,
    ): Boolean {
        if (conn.input?.isOpen == false) return true
        if (conn.output?.isOpen == false) return true
        val device = discoveredById[conn.device.id] ?: return true
        val currentPortIds = device.ports.map { it.id }.toSet()
        if (conn.input != null && conn.input.portId !in currentPortIds) return true
        if (conn.output != null && conn.output.portId !in currentPortIds) return true
        return false
    }

    /** Reads and mutates [probeBackoff]; must be called while holding [stateLock]. */
    private fun selectProbeCandidatesLocked(
        discovered: List<AmethystMidiDevice>,
        discoveredIds: Set<String>,
        now: Long,
    ): List<AmethystMidiDevice> {
        probeBackoff.keys.retainAll(discoveredIds)

        return discovered.filter { device ->
            if (device.id in activeConnections) return@filter false
            if (device.inputPorts.isEmpty() || device.outputPorts.isEmpty()) return@filter false

            val fingerprint = device.ports.map { it.id }.toSet()
            val backoff = probeBackoff[device.id]
            when {
                backoff == null -> true
                backoff.fingerprint != fingerprint -> {
                    probeBackoff.remove(device.id)
                    true
                }
                else -> now >= backoff.nextAttemptAtMillis
            }
        }
    }

    /** Commits probe outcomes; must be called while holding [stateLock]. Returns newly connected devices and log lines to print outside the lock. */
    private fun commitProbeResultsLocked(
        results: List<ProbeOutcome>,
        now: Long,
    ): Pair<List<ActiveDeviceConnection>, List<String>> {
        val connected = mutableListOf<ActiveDeviceConnection>()
        val logs = mutableListOf<String>()

        for (outcome in results) {
            val device = outcome.device
            val detection = outcome.detection
            if (detection != null) {
                val conn = ActiveDeviceConnection(
                    device = device,
                    input = detection.inputConnection,
                    output = detection.outputConnection,
                    detectedType = detection.type,
                    detectedFirmware = detection.firmware,
                    friendlyName = detection.type.label,
                )
                activeConnections[device.id] = conn
                connected += conn
                probeBackoff.remove(device.id)
            } else {
                val fingerprint = device.ports.map { it.id }.toSet()
                val prior = probeBackoff[device.id]
                val attempt = if (prior != null && prior.fingerprint == fingerprint) prior.attempt + 1 else 1
                val delayMs = backoffDelayForAttempt(attempt)
                probeBackoff[device.id] = ProbeBackoffState(fingerprint, attempt, now + delayMs)
                logs += "[Probe] ${device.displayName} did not respond to device inquiry, backing off ${delayMs}ms (attempt $attempt)"
            }
        }

        return connected to logs
    }

    // -- Public API --

    fun changeDeviceConfig(uuid: String, deviceId: String?) {
        synchronized(stateLock) {
            val elements = elementsProvider()
            val element = elements.find { it.selectionUUID == uuid } ?: return@synchronized

            detachElementLocked(element)

            if (deviceId == null) {
                element.savedMidiDeviceId = null
                element.savedInputPortId = null
                element.savedInputPortName = null
                element.savedOutputPortId = null
                element.savedOutputPortName = null
                return@synchronized
            }

            val active = activeConnections[deviceId]
            if (active != null) {
                // The user explicitly chose this device; steal it from whoever else has it.
                val stolenFrom = elements.firstOrNull { other ->
                    other !== element && other.launchpadDevice?.connection?.let { c ->
                        c.device.id == active.device.id ||
                            (c.input.portId == active.input?.portId && c.output.portId == active.output?.portId)
                    } == true
                }
                if (stolenFrom != null) detachElementLocked(stolenFrom)
                connectElementLocked(element, active)
            } else {
                element.savedMidiDeviceId = deviceId
                element.savedInputPortId = null
                element.savedInputPortName = null
                element.savedOutputPortId = null
                element.savedOutputPortName = null
            }
        }
    }

    fun detachElement(element: LaunchpadViewportElement) {
        synchronized(stateLock) { detachElementLocked(element) }
    }

    fun detachAllWorkspaceDevices() {
        synchronized(stateLock) {
            elementsProvider().forEach(::detachElementLocked)
            elementCollectorJobs.values.forEach { it.cancel() }
            elementCollectorJobs.clear()
        }
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
            rescanAndReport("Initial MIDI rescan failed")

            val hotplugJob = launch(start = CoroutineStart.UNDISPATCHED) {
                while (isActive) {
                    try {
                        access.deviceChanges.conflate().collect {
                            // A physical change happened; every backed-off device deserves an immediate retry.
                            clearProbeBackoff()
                            rescanAndReport("MIDI hotplug rescan failed")
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        println("MIDI hotplug monitor failed: ${exception.message}")
                    }
                    if (isActive) delay(HOTPLUG_MONITOR_RETRY_DELAY_MS)
                }
            }

            val livenessJob = launch {
                while (isActive) {
                    delay(LIVENESS_CHECK_INTERVAL_MS)
                    if (hasDeadConnections()) {
                        rescanAndReport("MIDI liveness rescan failed")
                    }
                }
            }

            try {
                while (isActive) {
                    delay(FULL_RESCAN_INTERVAL_MS)
                    rescanAndReport("MIDI periodic rescan failed")
                }
            } finally {
                hotplugJob.cancel()
                livenessJob.cancel()
            }
        }
    }

    fun stopAutoDetectLoop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun clearProbeBackoff() {
        synchronized(stateLock) { probeBackoff.clear() }
    }

    private fun hasDeadConnections(): Boolean = synchronized(stateLock) {
        activeConnections.values.any { it.input?.isOpen == false || it.output?.isOpen == false }
    }

    private suspend fun rescanAndReport(failureMessage: String) {
        try {
            rescanMutex.withLock { rescanDevices() }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("$failureMessage: ${exception.message}")
        }
    }

    private suspend fun rescanDevices() {
        val access = midiAccess ?: return
        val elements = elementsProvider()
        val discovered = access.discoverDevices()
        val discoveredById = discovered.associateBy { it.id }

        // 1) Drop dead connections (closed ports, vanished device, or the connection's own
        //    ports no longer present on the device) and close their native resources.
        val removedConnections = synchronized(stateLock) {
            val deadIds = activeConnections.filterValues { isConnectionDead(it, discoveredById) }.keys.toList()
            deadIds.mapNotNull { activeConnections.remove(it) }
        }
        removedConnections.forEach { conn ->
            printConnectionState("Disconnected", conn)
            runCatching { conn.input?.close() }
                .onFailure { println("MIDI input close failed: ${it.message}") }
            runCatching { conn.output?.close() }
                .onFailure { println("MIDI output close failed: ${it.message}") }
        }

        // 2) Snapshot which discovered devices are worth probing right now (not already
        //    connected, has both directions, and its backoff window - if any - has elapsed).
        val now = nowMillis()
        val candidates = synchronized(stateLock) {
            selectProbeCandidatesLocked(discovered, discoveredById.keys, now)
        }

        // 3) Probe candidates concurrently (bounded), each with the existing per-output timeout.
        //    This suspending work runs entirely outside stateLock.
        val probeResults = if (candidates.isEmpty()) {
            emptyList()
        } else {
            coroutineScope {
                val semaphore = Semaphore(PROBE_CONCURRENCY_LIMIT)
                candidates.map { device ->
                    async {
                        semaphore.withPermit {
                            val detection = try {
                                detectDeviceType(device)
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                println("[Probe] ${device.displayName} failed: ${exception.message}")
                                null
                            }
                            ProbeOutcome(device, detection)
                        }
                    }
                }.awaitAll()
            }
        }

        // 4) Commit probe results, refresh the published device list, and bind elements -
        //    all synchronously, re-entering the lock.
        val (newlyConnected, backoffLogs) = synchronized(stateLock) {
            val (connected, logs) = commitProbeResultsLocked(probeResults, now)
            updateDetectedDevicesListLocked()
            bindElementsLocked(elements)
            connected to logs
        }

        backoffLogs.forEach(::println)
        newlyConnected.forEach { printConnectionState("Connected", it) }
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

        val macroSnapshot = currentSignalMacroValues()
        val midiSignals = listOf(
            Signal.Midi(
                origin = null,
                x = globalX,
                y = globalY,
                velocity = input.velocity,
                macroValues = macroSnapshot,
            )
        )
        val ledSignals = listOf(
            Signal.LED(
                origin = null,
                x = globalX,
                y = globalY,
                color = if (input.velocity == 0) Color.Black else Color.White,
                layer = 0,
                macroValues = macroSnapshot,
            )
        )

        WorkspaceRepository.samplingChain.signalEnter(midiSignals)
        AutoPlayRepository.onMidiInput(midiSignals)
        WorkspaceRepository.lightsChain.signalEnter(ledSignals)
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

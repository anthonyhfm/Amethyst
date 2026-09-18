package dev.anthonyhfm.amethyst.core.midi

import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Coverage for [AmethystMidiManager]'s thread-safe state handling, probe backoff and
 * element-binding rules. Runs against a fake [AmethystMidiAccess] so it never touches
 * real MIDI hardware/backends; a manually-advanced clock is injected so backoff timing
 * doesn't need real sleeps.
 */
class AmethystMidiManagerTest {

    private val managersToClose = mutableListOf<AmethystMidiManager>()

    @AfterTest
    fun tearDown() {
        managersToClose.forEach { runCatching { it.close() } }
        managersToClose.clear()
    }

    private fun newManager(
        access: FakeMidiAccess,
        elementsProvider: () -> List<LaunchpadViewportElement>,
        clock: TestClock = TestClock(),
    ): AmethystMidiManager {
        val manager = AmethystMidiManager(
            midiAccess = access,
            closeMidiAccessOnClose = false,
            elementsProvider = elementsProvider,
            nowMillis = { clock.value },
        )
        managersToClose += manager
        return manager
    }

    // ---------------------------------------------------------------------
    // Detection
    // ---------------------------------------------------------------------

    @Test
    fun `responding Launchpad X device is detected`(): Unit = runBlocking {
        val access = FakeMidiAccess()
        val device = FakeMidiDevice(id = "dev-1", name = "Launchpad X").apply {
            inquiryResponse = launchpadXInquiryResponse(MAT1JACZYYY_LPX_REVISION)
        }
        access.setDevices(listOf(device))

        val manager = newManager(access, elementsProvider = { emptyList() })
        manager.refreshConnections()

        waitUntil("device to be detected") {
            AmethystMidiManager.detectedDevices.value.any { it.id == device.id }
        }

        val detail = AmethystMidiManager.detectedDevices.value.first { it.id == device.id }
        assertEquals(dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType.LAUNCHPAD_X, detail.type)
    }

    // ---------------------------------------------------------------------
    // Probe backoff
    // ---------------------------------------------------------------------

    @Test
    fun `non-responding device is probed once then backed off, retried only after the delay`(): Unit = runBlocking {
        val access = FakeMidiAccess()
        val foreignDevice = FakeMidiDevice(id = "foreign-1", name = "Foreign Gear")
        // inquiryResponse left null => never answers.
        access.setDevices(listOf(foreignDevice))

        val clock = TestClock(0L)
        val manager = newManager(access, elementsProvider = { emptyList() }, clock = clock)

        manager.refreshConnections()
        waitUntil("first probe attempt", timeoutMs = 5_000) { foreignDevice.probeAttempts.get() == 1 }

        // Still within the 1s backoff window: further rescans must not re-probe.
        manager.refreshConnections()
        delay(300)
        assertEquals(1, foreignDevice.probeAttempts.get(), "device should still be backed off")

        // Advance past the first backoff window (1s) -> attempt #2.
        clock.value = 1_000
        manager.refreshConnections()
        waitUntil("second probe attempt") { foreignDevice.probeAttempts.get() == 2 }

        // Not yet past the second window (2s from the 2nd attempt) -> no retry.
        manager.refreshConnections()
        delay(300)
        assertEquals(2, foreignDevice.probeAttempts.get())

        // Advance past it -> attempt #3.
        clock.value = 3_000
        manager.refreshConnections()
        waitUntil("third probe attempt") { foreignDevice.probeAttempts.get() == 3 }
    }

    // ---------------------------------------------------------------------
    // Single-element auto binding
    // ---------------------------------------------------------------------

    @Test
    fun `single element auto-binds to the first compatible device even with stale saved ids`(): Unit = runBlocking {
        val access = FakeMidiAccess()
        val device = FakeMidiDevice(id = "dev-lpx", name = "Launchpad X").apply {
            inquiryResponse = launchpadXInquiryResponse(MAT1JACZYYY_LPX_REVISION)
        }
        access.setDevices(listOf(device))

        val element = ViewportLaunchpadX().apply {
            savedMidiDeviceId = "stale-device-id"
            savedInputPortId = "stale-input-id"
            savedInputPortName = "Some Old Device"
        }

        val manager = newManager(access, elementsProvider = { listOf(element) })
        manager.refreshConnections()

        waitUntil("element to auto-bind") { element.launchpadDevice != null }
        assertEquals(device.id, element.savedMidiDeviceId)
    }

    @Test
    fun `single element re-binds after its device disappears and a different one appears`(): Unit = runBlocking {
        val access = FakeMidiAccess()
        val deviceA = FakeMidiDevice(id = "dev-a", name = "Launchpad X A").apply {
            inquiryResponse = launchpadXInquiryResponse(MAT1JACZYYY_LPX_REVISION)
        }
        access.setDevices(listOf(deviceA))

        val element = ViewportLaunchpadX()
        val manager = newManager(access, elementsProvider = { listOf(element) })

        manager.refreshConnections()
        waitUntil("element to bind to device A") { element.savedMidiDeviceId == deviceA.id }

        val deviceB = FakeMidiDevice(id = "dev-b", name = "Launchpad X B").apply {
            inquiryResponse = launchpadXInquiryResponse(ORIGINAL_LPX_REVISION)
        }
        access.setDevices(listOf(deviceB)) // device A unplugged, device B plugged in

        manager.refreshConnections()
        waitUntil("element to re-bind to device B", timeoutMs = 5_000) {
            element.savedMidiDeviceId == deviceB.id
        }
        assertNotNull(element.launchpadDevice)
    }

    // ---------------------------------------------------------------------
    // Multi-element binding
    // ---------------------------------------------------------------------

    @Test
    fun `two elements bind by saved device id and never share one connection`(): Unit = runBlocking {
        val access = FakeMidiAccess()
        val device1 = FakeMidiDevice(id = "dev-1", name = "Launchpad X #1").apply {
            inquiryResponse = launchpadXInquiryResponse(MAT1JACZYYY_LPX_REVISION)
        }
        val device2 = FakeMidiDevice(id = "dev-2", name = "Launchpad X #2").apply {
            inquiryResponse = launchpadXInquiryResponse(MAT1JACZYYY_LPX_REVISION)
        }
        access.setDevices(listOf(device1, device2))

        val element1 = ViewportLaunchpadX().apply { savedMidiDeviceId = device2.id }
        val element2 = ViewportLaunchpadX().apply { savedMidiDeviceId = device1.id }
        // A third, unrelated element with no saved preference must NOT be bound in multi-element mode.
        val element3 = ViewportLaunchpadX()

        val manager = newManager(access, elementsProvider = { listOf(element1, element2, element3) })
        manager.refreshConnections()

        waitUntil("both preferred elements to bind") {
            element1.launchpadDevice != null && element2.launchpadDevice != null
        }

        assertEquals(device2.id, element1.launchpadDevice?.connection?.device?.id)
        assertEquals(device1.id, element2.launchpadDevice?.connection?.device?.id)
        assertNull(element3.launchpadDevice, "unbound element without a saved preference must stay unbound in multi-element mode")
    }

    // ---------------------------------------------------------------------
    // Dead connection handling
    // ---------------------------------------------------------------------

    @Test
    fun `a dead connection is dropped and the device is re-detected`(): Unit = runBlocking {
        val access = FakeMidiAccess()
        val device = FakeMidiDevice(id = "dev-1", name = "Launchpad X").apply {
            inquiryResponse = launchpadXInquiryResponse(MAT1JACZYYY_LPX_REVISION)
        }
        access.setDevices(listOf(device))

        val element = ViewportLaunchpadX()
        val manager = newManager(access, elementsProvider = { listOf(element) })

        manager.refreshConnections()
        waitUntil("initial bind") { element.launchpadDevice != null }
        val originalInput = element.launchpadDevice!!.connection.input

        // Simulate the OS port vanishing without a discovery change.
        (originalInput as FakeMidiInput).isOpen = false

        manager.refreshConnections()
        waitUntil("device to be re-detected with a fresh connection", timeoutMs = 5_000) {
            val current = element.launchpadDevice
            current != null && current.connection.input !== originalInput
        }
    }

    // ---------------------------------------------------------------------
    // changeDeviceConfig
    // ---------------------------------------------------------------------

    @Test
    fun `changeDeviceConfig steals a connection from another element`(): Unit = runBlocking {
        val access = FakeMidiAccess()
        val device = FakeMidiDevice(id = "dev-1", name = "Launchpad X").apply {
            inquiryResponse = launchpadXInquiryResponse(MAT1JACZYYY_LPX_REVISION)
        }
        access.setDevices(listOf(device))

        val elementA = ViewportLaunchpadX().apply { savedMidiDeviceId = device.id }
        val elementB = ViewportLaunchpadX()

        val manager = newManager(access, elementsProvider = { listOf(elementA, elementB) })
        manager.refreshConnections()
        waitUntil("element A to bind first") { elementA.launchpadDevice != null }
        assertNull(elementB.launchpadDevice)

        manager.changeDeviceConfig(elementB.selectionUUID, device.id)

        assertNull(elementA.launchpadDevice, "previous owner must be detached")
        assertNotNull(elementB.launchpadDevice, "new owner must be connected")
        assertEquals(device.id, elementB.savedMidiDeviceId)
    }

    // ---------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------

    private suspend fun waitUntil(
        description: String,
        timeoutMs: Long = 3_000,
        intervalMs: Long = 20,
        predicate: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(intervalMs)
        }
        if (!predicate()) fail("Timed out waiting for: $description")
    }
}

class TestClock(@Volatile var value: Long = 0L)

// ---------------------------------------------------------------------------
// Fake AmethystMidiAccess backend
// ---------------------------------------------------------------------------

/** Builds a Device Inquiry response SysEx matching a real Launchpad X's family/member bytes. */
fun launchpadXInquiryResponse(revision: ByteArray): ByteArray = byteArrayOf(
    0xF0.toByte(), 0x7E, 0x00, 0x06, 0x02, 0x00, 0x20, 0x29, 0x03, 0x01, 0x00, 0x00,
    revision[0], revision[1], revision[2], revision[3],
    0xF7.toByte(),
)

val MAT1JACZYYY_LPX_REVISION = byteArrayOf(0, 3, 5, 2)
val ORIGINAL_LPX_REVISION = byteArrayOf(0, 1, 0, 0)

class FakeMidiPort(
    override val id: String,
    override val name: String,
    override val direction: AmethystMidiPortDirection,
    override val portNumber: UInt,
    override val isAvailable: Boolean = true,
) : AmethystMidiPort

class FakeMidiDevice(
    override val id: String,
    override val name: String,
    inputPortCount: Int = 1,
    outputPortCount: Int = 1,
    override val manufacturer: String? = null,
    override val model: String? = null,
    override val serialNumber: String? = null,
    override val usbVendorId: UShort? = null,
    override val usbProductId: UShort? = null,
) : AmethystMidiDevice {
    override val inputPorts: List<FakeMidiPort> = (0 until inputPortCount).map {
        FakeMidiPort("$id-in-$it", "$name In $it", AmethystMidiPortDirection.INPUT, it.toUInt())
    }
    override val outputPorts: List<FakeMidiPort> = (0 until outputPortCount).map {
        FakeMidiPort("$id-out-$it", "$name Out $it", AmethystMidiPortDirection.OUTPUT, it.toUInt())
    }
    override val ports: List<AmethystMidiPort> get() = inputPorts + outputPorts
    override val displayName: String get() = name
    override val isBidirectional: Boolean get() = inputPorts.isNotEmpty() && outputPorts.isNotEmpty()

    /** Test knob: the Device Inquiry response to answer with, or null to never respond (foreign gear). */
    var inquiryResponse: ByteArray? = null

    /** Which of this device's input ports receives the inquiry response, if any. */
    var respondingInputPortId: String? = inputPorts.firstOrNull()?.id

    /** How many times a Device Inquiry has been sent to one of this device's outputs. */
    val probeAttempts = AtomicInteger(0)
}

class FakeMidiInput(override val portId: String) : AmethystMidiInput {
    private val flow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val messages: Flow<ByteArray> = flow

    @Volatile
    override var isOpen: Boolean = true

    fun emit(bytes: ByteArray) {
        flow.tryEmit(bytes)
    }

    override fun close() {
        isOpen = false
    }
}

class FakeMidiOutput(
    override val portId: String,
    private val device: FakeMidiDevice,
    private val access: FakeMidiAccess,
) : AmethystMidiOutput {

    @Volatile
    override var isOpen: Boolean = true

    override fun send(data: ByteArray) { /* no-op: not exercised by these tests */ }

    override fun sendSysEx(data: ByteArray) { /* no-op: not exercised by these tests */ }

    override fun sendDeviceInquiry() {
        device.probeAttempts.incrementAndGet()
        val response = device.inquiryResponse ?: return
        val inputPortId = device.respondingInputPortId ?: return
        access.openInputFor(device.id, inputPortId)?.emit(response)
    }

    override fun close() {
        isOpen = false
    }
}

class FakeMidiAccess : AmethystMidiAccess {
    override val backendName: String = "fake"

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val deviceChanges: Flow<Unit> = changes

    private val devicesLock = Any()
    private var devices: List<FakeMidiDevice> = emptyList()

    private val openInputsByDevice = ConcurrentHashMap<String, ConcurrentHashMap<String, FakeMidiInput>>()

    fun setDevices(newDevices: List<FakeMidiDevice>) {
        synchronized(devicesLock) { devices = newDevices }
    }

    suspend fun triggerHotplug() {
        changes.emit(Unit)
    }

    override suspend fun discoverDevices(): List<AmethystMidiDevice> =
        synchronized(devicesLock) { devices.toList() }

    override fun getCachedDevices(): List<AmethystMidiDevice> =
        synchronized(devicesLock) { devices.toList() }

    override suspend fun openInput(portId: String): AmethystMidiInput {
        val device = findDeviceForPort(portId) ?: error("No fake device exposes input port $portId")
        val input = FakeMidiInput(portId)
        openInputsByDevice.getOrPut(device.id) { ConcurrentHashMap() }[portId] = input
        return input
    }

    override suspend fun openOutput(portId: String): AmethystMidiOutput {
        val device = findDeviceForPort(portId) ?: error("No fake device exposes output port $portId")
        return FakeMidiOutput(portId, device, this)
    }

    fun openInputFor(deviceId: String, portId: String): FakeMidiInput? =
        openInputsByDevice[deviceId]?.get(portId)

    override fun close() { /* no-op */ }

    private fun findDeviceForPort(portId: String): FakeMidiDevice? =
        synchronized(devicesLock) { devices.find { d -> d.ports.any { it.id == portId } } }
}

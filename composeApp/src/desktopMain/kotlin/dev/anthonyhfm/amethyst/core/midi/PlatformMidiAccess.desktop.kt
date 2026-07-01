package dev.anthonyhfm.amethyst.core.midi

import dev.anthonyhfm.amethyst.nativeengine.AmethystNativeEngine
import dev.anthonyhfm.amethyst.nativeengine.midi.NativeMidiAccess
import dev.anthonyhfm.amethyst.nativeengine.midi.NativeMidiDevice
import dev.anthonyhfm.amethyst.nativeengine.midi.NativeMidiPort
import dev.anthonyhfm.amethyst.nativeengine.midi.NativeMidiInput
import dev.anthonyhfm.amethyst.nativeengine.midi.NativeMidiOutput
import dev.anthonyhfm.amethyst.nativeengine.MidiPortDirection
import kotlinx.coroutines.flow.Flow

class DesktopMidiAccess(private val delegate: NativeMidiAccess) : AmethystMidiAccess {
    override val backendName: String get() = delegate.backendName
    override val deviceChanges: Flow<Unit> get() = delegate.deviceChanges
    override suspend fun discoverDevices(): List<AmethystMidiDevice> = delegate.discoverDevices().map { DesktopMidiDevice(it) }
    override fun getCachedDevices(): List<AmethystMidiDevice> = delegate.getCachedDevices().map { DesktopMidiDevice(it) }
    override suspend fun openInput(portId: String): AmethystMidiInput = DesktopMidiInput(delegate.openInput(portId))
    override suspend fun openOutput(portId: String): AmethystMidiOutput = DesktopMidiOutput(delegate.openOutput(portId))
    override fun close() = delegate.close()
}

class DesktopMidiDevice(private val delegate: NativeMidiDevice) : AmethystMidiDevice {
    override val id: String get() = delegate.id
    override val name: String get() = delegate.name
    override val manufacturer: String? get() = delegate.manufacturer
    override val model: String? get() = delegate.model
    override val serialNumber: String? get() = delegate.serialNumber
    override val usbVendorId: UShort? get() = delegate.usbVendorId
    override val usbProductId: UShort? get() = delegate.usbProductId
    override val ports: List<AmethystMidiPort> get() = delegate.ports.map { DesktopMidiPort(it) }
    override val inputPorts: List<AmethystMidiPort> get() = delegate.inputPorts.map { DesktopMidiPort(it) }
    override val outputPorts: List<AmethystMidiPort> get() = delegate.outputPorts.map { DesktopMidiPort(it) }
    override val displayName: String get() = delegate.displayName
    override val isBidirectional: Boolean get() = delegate.isBidirectional
}

class DesktopMidiPort(private val delegate: NativeMidiPort) : AmethystMidiPort {
    override val id: String get() = delegate.id
    override val name: String get() = delegate.name
    override val direction: AmethystMidiPortDirection get() = when(delegate.direction) {
        MidiPortDirection.INPUT -> AmethystMidiPortDirection.INPUT
        MidiPortDirection.OUTPUT -> AmethystMidiPortDirection.OUTPUT
    }
    override val portNumber: UInt get() = delegate.portNumber
    override val isAvailable: Boolean get() = delegate.isAvailable
}

class DesktopMidiInput(private val delegate: NativeMidiInput) : AmethystMidiInput {
    override val portId: String get() = delegate.portId
    override val messages: Flow<ByteArray> get() = delegate.messages
    private var _isOpen = true
    override val isOpen: Boolean get() = _isOpen
    override fun close() {
        _isOpen = false
        delegate.close()
    }
}

class DesktopMidiOutput(private val delegate: NativeMidiOutput) : AmethystMidiOutput {
    override val portId: String get() = delegate.portId
    override fun send(data: ByteArray) = delegate.send(data)
    override fun sendSysEx(data: ByteArray) = delegate.sendSysEx(data)
    override fun sendDeviceInquiry() = delegate.sendDeviceInquiry()
    private var _isOpen = true
    override val isOpen: Boolean get() = _isOpen
    override fun close() {
        _isOpen = false
        delegate.close()
    }
}

actual val platformMidiAccess: AmethystMidiAccess? by lazy {
    DesktopMidiAccess(AmethystNativeEngine.createMidiAccess())
}

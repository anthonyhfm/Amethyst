package dev.anthonyhfm.amethyst.core.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Bundle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val PORT_ID_PREFIX = "android-midi"

class AndroidMidiAccess(
    context: Context,
    private val onClosed: (AndroidMidiAccess) -> Unit,
) : AmethystMidiAccess {
    private val midiManager = context.getSystemService(MidiManager::class.java)
    private val closed = AtomicBoolean(false)
    private val deviceChangesMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val connectionsByDevice = ConcurrentHashMap<Int, MutableSet<AndroidMidiConnection>>()

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) = notifyDeviceChange()

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            connectionsByDevice.remove(device.id)?.toList()?.forEach { it.close() }
            notifyDeviceChange()
        }

        override fun onDeviceStatusChanged(status: android.media.midi.MidiDeviceStatus) = notifyDeviceChange()
    }

    init {
        midiManager.registerDeviceCallback(deviceCallback, null)
    }

    override val backendName: String = "Android MIDI"
    override val deviceChanges: Flow<Unit> = deviceChangesMutable.asSharedFlow()

    override suspend fun discoverDevices(): List<AmethystMidiDevice> {
        ensureOpen()
        return mapDevices(midiManager.devices)
    }

    override fun getCachedDevices(): List<AmethystMidiDevice> {
        if (closed.get()) return emptyList()
        return mapDevices(midiManager.devices)
    }

    override suspend fun openInput(portId: String): AmethystMidiInput {
        ensureOpen()
        val address = AndroidMidiPortAddress.parse(portId, AndroidMidiPortDirection.INPUT)
        val device = openDevice(address.deviceId)
        try {
            val port = device.openOutputPort(address.portNumber)
                ?: throw IllegalStateException("MIDI input port $portId is unavailable")
            return AndroidMidiInput(address.deviceId, portId, device, port, ::untrackConnection).also(::trackConnection)
        } catch (error: Throwable) {
            device.close()
            throw error
        }
    }

    override suspend fun openOutput(portId: String): AmethystMidiOutput {
        ensureOpen()
        val address = AndroidMidiPortAddress.parse(portId, AndroidMidiPortDirection.OUTPUT)
        val device = openDevice(address.deviceId)
        try {
            val port = device.openInputPort(address.portNumber)
                ?: throw IllegalStateException("MIDI output port $portId is unavailable")
            return AndroidMidiOutput(address.deviceId, portId, device, port, ::untrackConnection).also(::trackConnection)
        } catch (error: Throwable) {
            device.close()
            throw error
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        midiManager.unregisterDeviceCallback(deviceCallback)
        connectionsByDevice.values.flatMap { it.toList() }.forEach { it.close() }
        connectionsByDevice.clear()
        onClosed(this)
    }

    private suspend fun openDevice(deviceId: Int): MidiDevice = suspendCancellableCoroutine { continuation ->
        val deviceInfo = midiManager.devices.firstOrNull { it.id == deviceId }
        if (deviceInfo == null) {
            continuation.resumeWithException(IllegalStateException("Android MIDI device $deviceId is no longer connected"))
            return@suspendCancellableCoroutine
        }
        midiManager.openDevice(deviceInfo, { device ->
            if (device == null) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Could not open Android MIDI device $deviceId"))
                }
            } else if (continuation.isActive) {
                continuation.resume(device) { _, openedDevice, _ -> openedDevice.close() }
            } else {
                device.close()
            }
        }, null)
    }

    private fun trackConnection(connection: AndroidMidiConnection) {
        connectionsByDevice.computeIfAbsent(connection.deviceId) { ConcurrentHashMap.newKeySet() }.add(connection)
    }

    private fun untrackConnection(connection: AndroidMidiConnection) {
        connectionsByDevice[connection.deviceId]?.let { connections ->
            connections.remove(connection)
            if (connections.isEmpty()) connectionsByDevice.remove(connection.deviceId, connections)
        }
    }

    private fun notifyDeviceChange() {
        if (!closed.get()) deviceChangesMutable.tryEmit(Unit)
    }

    private fun ensureOpen() {
        check(!closed.get()) { "Android MIDI access is closed" }
    }
}

private data class AndroidMidiDeviceDescriptor(
    val systemId: Int,
    val fingerprint: String,
    val info: MidiDeviceInfo,
)

private fun mapDevices(infos: Array<MidiDeviceInfo>): List<AmethystMidiDevice> {
    val descriptors = infos.map { info ->
        AndroidMidiDeviceDescriptor(info.id, androidDeviceFingerprint(info), info)
    }
    val collisionCounts = descriptors.groupingBy { it.fingerprint }.eachCount()

    return descriptors.sortedBy { it.systemId }.map { descriptor ->
        val id = if (collisionCounts.getValue(descriptor.fingerprint) == 1) {
            descriptor.fingerprint
        } else {
            "${descriptor.fingerprint}:instance:${descriptor.systemId}"
        }
        AndroidMidiDevice(id, descriptor.systemId, descriptor.info)
    }
}

private class AndroidMidiDevice(
    override val id: String,
    private val systemId: Int,
    info: MidiDeviceInfo,
) : AmethystMidiDevice {
    private val properties = info.properties

    override val manufacturer: String? = properties.string(MidiDeviceInfo.PROPERTY_MANUFACTURER)
    override val model: String? = properties.string(MidiDeviceInfo.PROPERTY_PRODUCT)
    override val serialNumber: String? = properties.string(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER)
    override val name: String = model ?: manufacturer ?: "Android MIDI Device"
    override val usbVendorId: UShort? = null
    override val usbProductId: UShort? = null
    override val ports: List<AmethystMidiPort> = info.ports
        .mapNotNull { port -> AndroidMidiPort.from(systemId, port) }
        .sortedWith(compareBy<AmethystMidiPort> { it.direction.ordinal }.thenBy { it.portNumber })
    override val inputPorts: List<AmethystMidiPort> = ports.filter { it.direction == AmethystMidiPortDirection.INPUT }
    override val outputPorts: List<AmethystMidiPort> = ports.filter { it.direction == AmethystMidiPortDirection.OUTPUT }
    override val displayName: String get() = model ?: name
    override val isBidirectional: Boolean get() = inputPorts.isNotEmpty() && outputPorts.isNotEmpty()
}

private class AndroidMidiPort(
    override val id: String,
    override val name: String,
    override val direction: AmethystMidiPortDirection,
    override val portNumber: UInt,
) : AmethystMidiPort {
    override val isAvailable: Boolean = true

    companion object {
        fun from(deviceId: Int, port: MidiDeviceInfo.PortInfo): AndroidMidiPort? {
            val direction = when (port.type) {
                MidiDeviceInfo.PortInfo.TYPE_OUTPUT -> AndroidMidiPortDirection.INPUT
                MidiDeviceInfo.PortInfo.TYPE_INPUT -> AndroidMidiPortDirection.OUTPUT
                else -> return null
            }
            val name = port.name?.takeIf { it.isNotBlank() }
                ?: "${direction.label} ${port.portNumber + 1}"
            return AndroidMidiPort(
                id = AndroidMidiPortAddress(deviceId, direction, port.portNumber).encode(),
                name = name,
                direction = direction.amethystDirection,
                portNumber = port.portNumber.toUInt(),
            )
        }
    }
}

private enum class AndroidMidiPortDirection(val label: String, val amethystDirection: AmethystMidiPortDirection) {
    INPUT("Input", AmethystMidiPortDirection.INPUT),
    OUTPUT("Output", AmethystMidiPortDirection.OUTPUT),
}

private data class AndroidMidiPortAddress(
    val deviceId: Int,
    val direction: AndroidMidiPortDirection,
    val portNumber: Int,
) {
    fun encode(): String = "$PORT_ID_PREFIX:$deviceId:${direction.name.lowercase()}:$portNumber"

    companion object {
        fun parse(value: String, expectedDirection: AndroidMidiPortDirection): AndroidMidiPortAddress {
            val parts = value.split(':')
            require(parts.size == 4 && parts[0] == PORT_ID_PREFIX) { "Unknown Android MIDI port $value" }
            val direction = runCatching { AndroidMidiPortDirection.valueOf(parts[2].uppercase()) }.getOrNull()
                ?: throw IllegalArgumentException("Unknown Android MIDI port direction in $value")
            require(direction == expectedDirection) { "Android MIDI port $value has the wrong direction" }
            return AndroidMidiPortAddress(
                deviceId = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid Android MIDI device id in $value"),
                direction = direction,
                portNumber = parts[3].toIntOrNull()?.takeIf { it >= 0 }
                    ?: throw IllegalArgumentException("Invalid Android MIDI port number in $value"),
            )
        }
    }
}

private fun androidDeviceFingerprint(info: MidiDeviceInfo): String {
    val properties = info.properties
    val identity = listOf(
        info.type.toString(),
        properties.string(MidiDeviceInfo.PROPERTY_MANUFACTURER).orEmpty(),
        properties.string(MidiDeviceInfo.PROPERTY_PRODUCT).orEmpty(),
        properties.string(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER).orEmpty(),
        properties.string(MidiDeviceInfo.PROPERTY_VERSION).orEmpty(),
        info.ports.joinToString("|") { "${it.type}:${it.portNumber}:${it.name.orEmpty()}" },
    ).joinToString("\u001f")
    return "$PORT_ID_PREFIX-device:${identity.encodeToByteArray().joinToString("") { "%02x".format(it) }}"
}

private fun Bundle.string(key: String): String? = getString(key)?.trim()?.takeIf { it.isNotEmpty() }

private interface AndroidMidiConnection : AutoCloseable {
    val deviceId: Int
}

private class AndroidMidiInput(
    override val deviceId: Int,
    override val portId: String,
    private val device: MidiDevice,
    private val port: MidiOutputPort,
    private val onClose: (AndroidMidiConnection) -> Unit,
) : AmethystMidiInput, AndroidMidiConnection {
    private val open = AtomicBoolean(true)
    private val assembler = AndroidMidiMessageAssembler()
    private val messagesMutable = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val receiver = object : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            if (!open.get()) return
            val messages = synchronized(assembler) { assembler.feed(data, offset, count) }
            messages.forEach(messagesMutable::tryEmit)
        }

        override fun onFlush() {
            synchronized(assembler) { assembler.clear() }
        }
    }

    init {
        port.connect(receiver)
    }

    override val messages: Flow<ByteArray> = messagesMutable.asSharedFlow()
    override val isOpen: Boolean get() = open.get()

    override fun close() {
        if (!open.compareAndSet(true, false)) return
        port.disconnect(receiver)
        port.close()
        device.close()
        onClose(this)
    }
}

private class AndroidMidiOutput(
    override val deviceId: Int,
    override val portId: String,
    private val device: MidiDevice,
    private val port: MidiInputPort,
    private val onClose: (AndroidMidiConnection) -> Unit,
) : AmethystMidiOutput, AndroidMidiConnection {
    private val open = AtomicBoolean(true)
    private val sendLock = Any()
    override val isOpen: Boolean get() = open.get()

    override fun send(data: ByteArray) {
        synchronized(sendLock) {
            check(open.get()) { "Android MIDI output $portId is closed" }
            port.send(data, 0, data.size, 0)
        }
    }

    override fun sendSysEx(data: ByteArray) = send(normalizeSysEx(data))

    override fun sendDeviceInquiry() = send(byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte()))

    override fun close() {
        if (!open.compareAndSet(true, false)) return
        port.close()
        device.close()
        onClose(this)
    }
}

private fun normalizeSysEx(data: ByteArray): ByteArray = when {
    data.isEmpty() -> byteArrayOf(0xF0.toByte(), 0xF7.toByte())
    data.first() == 0xF0.toByte() && data.last() == 0xF7.toByte() -> data
    data.first() == 0xF0.toByte() -> data + 0xF7.toByte()
    data.last() == 0xF7.toByte() -> byteArrayOf(0xF0.toByte()) + data
    else -> byteArrayOf(0xF0.toByte()) + data + 0xF7.toByte()
}

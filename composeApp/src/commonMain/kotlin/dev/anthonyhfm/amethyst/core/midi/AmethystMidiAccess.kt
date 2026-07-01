package dev.anthonyhfm.amethyst.core.midi

import kotlinx.coroutines.flow.Flow

interface AmethystMidiAccess {
    val backendName: String
    val deviceChanges: Flow<Unit>
    suspend fun discoverDevices(): List<AmethystMidiDevice>
    fun getCachedDevices(): List<AmethystMidiDevice>
    suspend fun openInput(portId: String): AmethystMidiInput
    suspend fun openOutput(portId: String): AmethystMidiOutput
    fun close()
}

interface AmethystMidiDevice {
    val id: String
    val name: String
    val manufacturer: String?
    val model: String?
    val serialNumber: String?
    val usbVendorId: UShort?
    val usbProductId: UShort?
    val ports: List<AmethystMidiPort>
    val inputPorts: List<AmethystMidiPort>
    val outputPorts: List<AmethystMidiPort>
    val displayName: String
    val isBidirectional: Boolean
}

interface AmethystMidiPort {
    val id: String
    val name: String
    val direction: AmethystMidiPortDirection
    val portNumber: UInt
    val isAvailable: Boolean
}

enum class AmethystMidiPortDirection {
    INPUT, OUTPUT
}

interface AmethystMidiInput : AutoCloseable {
    val portId: String
    val messages: Flow<ByteArray>
    val isOpen: Boolean
}

interface AmethystMidiOutput : AutoCloseable {
    val portId: String
    val isOpen: Boolean
    fun send(data: ByteArray)
    fun sendSysEx(data: ByteArray)
    fun sendDeviceInquiry()
}

data class AmethystMidiDeviceConnection(
    val device: AmethystMidiDevice,
    val input: AmethystMidiInput,
    val output: AmethystMidiOutput
)

expect val platformMidiAccess: AmethystMidiAccess?

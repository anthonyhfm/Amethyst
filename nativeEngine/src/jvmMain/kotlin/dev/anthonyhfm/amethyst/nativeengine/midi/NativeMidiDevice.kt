package dev.anthonyhfm.amethyst.nativeengine.midi

import dev.anthonyhfm.amethyst.nativeengine.*

data class NativeMidiDevice(
    val id: String,
    val name: String,
    val manufacturer: String?,
    val model: String?,
    val serialNumber: String?,
    val usbVendorId: UShort?,
    val usbProductId: UShort?,
    val transport: MidiTransportType,
    val ports: List<NativeMidiPort>,
) {
    val inputPorts: List<NativeMidiPort>
        get() = ports.filter { it.direction == MidiPortDirection.INPUT }

    val outputPorts: List<NativeMidiPort>
        get() = ports.filter { it.direction == MidiPortDirection.OUTPUT }

    val displayName: String
        get() = model ?: name

    val isBidirectional: Boolean
        get() = inputPorts.isNotEmpty() && outputPorts.isNotEmpty()
}

data class NativeMidiPort(
    val id: String,
    val name: String,
    val direction: MidiPortDirection,
    val portNumber: UInt,
    val isAvailable: Boolean,
)

internal fun MidiDeviceInfo.toNativeMidiDevice() = NativeMidiDevice(
    id = id,
    name = name,
    manufacturer = manufacturer,
    model = model,
    serialNumber = serialNumber,
    usbVendorId = usbVendorId,
    usbProductId = usbProductId,
    transport = transport,
    ports = ports.map { NativeMidiPort(it.id, it.name, it.direction, it.portNumber, it.isAvailable) }
)

package dev.anthonyhfm.amethyst.core.midi

import kotlinx.cinterop.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.CoreFoundation.*
import platform.CoreMIDI.*

@OptIn(ExperimentalForeignApi::class)
class IosMidiAccess : AmethystMidiAccess {
    override val backendName: String = "CoreMIDI (iOS)"

    private val deviceChangesMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val deviceChanges: Flow<Unit> = deviceChangesMutable.asSharedFlow()

    private var client: MIDIClientRef = 0u
    private var isClosed = false

    init {
        memScoped {
            val clientRefVar = alloc<MIDIClientRefVar>()
            val clientName = CFStringCreateWithCString(null, "Amethyst", kCFStringEncodingUTF8)
            val status = MIDIClientCreateWithBlock(clientName, clientRefVar.ptr) { _ ->
                notifyDeviceChange()
            }
            if (clientName != null) CFRelease(clientName)
            if (status == 0) {
                client = clientRefVar.value
            }
        }
    }

    private fun notifyDeviceChange() {
        if (!isClosed) {
            deviceChangesMutable.tryEmit(Unit)
        }
    }

    override suspend fun discoverDevices(): List<AmethystMidiDevice> {
        return getCachedDevices()
    }

    override fun getCachedDevices(): List<AmethystMidiDevice> {
        if (isClosed) return emptyList()
        return scanDevices()
    }

    private fun scanDevices(): List<AmethystMidiDevice> {
        val result = mutableListOf<AmethystMidiDevice>()
        val processedEndpointIds = mutableSetOf<String>()

        val deviceCount = MIDIGetNumberOfDevices()
        for (i in 0uL until deviceCount) {
            val dev = MIDIGetDevice(i)
            if (dev == 0u) continue

            val offline = getIntegerProp(dev, kMIDIPropertyOffline) ?: 0
            if (offline != 0) continue

            val devName = getStringProp(dev, kMIDIPropertyName) ?: "Unknown MIDI Device"
            val manufacturer = getStringProp(dev, kMIDIPropertyManufacturer)
            val model = getStringProp(dev, kMIDIPropertyModel)
            val devUniqueId = getIntegerProp(dev, kMIDIPropertyUniqueID)
            val devId = devUniqueId?.toString() ?: "dev:$dev"

            val entityCount = MIDIDeviceGetNumberOfEntities(dev)
            val ports = mutableListOf<AmethystMidiPort>()

            for (j in 0uL until entityCount) {
                val entity = MIDIDeviceGetEntity(dev, j)
                if (entity == 0u) continue

                val srcCount = MIDIEntityGetNumberOfSources(entity)
                for (k in 0uL until srcCount) {
                    val endpoint = MIDIEntityGetSource(entity, k)
                    if (endpoint == 0u) continue

                    val epOffline = getIntegerProp(endpoint, kMIDIPropertyOffline) ?: 0
                    if (epOffline != 0) continue

                    val epUniqueId = getIntegerProp(endpoint, kMIDIPropertyUniqueID)
                    val portId = epUniqueId?.toString() ?: "src:$endpoint"
                    val portName = getStringProp(endpoint, kMIDIPropertyName) ?: devName

                    processedEndpointIds.add(portId)
                    ports.add(
                        IosMidiPort(
                            id = portId,
                            name = portName,
                            direction = AmethystMidiPortDirection.INPUT,
                            portNumber = k.toUInt(),
                            endpointRef = endpoint
                        )
                    )
                }

                val destCount = MIDIEntityGetNumberOfDestinations(entity)
                for (k in 0uL until destCount) {
                    val endpoint = MIDIEntityGetDestination(entity, k)
                    if (endpoint == 0u) continue

                    val epOffline = getIntegerProp(endpoint, kMIDIPropertyOffline) ?: 0
                    if (epOffline != 0) continue

                    val epUniqueId = getIntegerProp(endpoint, kMIDIPropertyUniqueID)
                    val portId = epUniqueId?.toString() ?: "dest:$endpoint"
                    val portName = getStringProp(endpoint, kMIDIPropertyName) ?: devName

                    processedEndpointIds.add(portId)
                    ports.add(
                        IosMidiPort(
                            id = portId,
                            name = portName,
                            direction = AmethystMidiPortDirection.OUTPUT,
                            portNumber = k.toUInt(),
                            endpointRef = endpoint
                        )
                    )
                }
            }

            if (ports.isNotEmpty()) {
                result.add(
                    IosMidiDevice(
                        id = devId,
                        name = devName,
                        manufacturer = manufacturer,
                        model = model,
                        serialNumber = null,
                        ports = ports
                    )
                )
            }
        }

        // Discover top-level virtual/standalone input sources
        val srcCount = MIDIGetNumberOfSources()
        for (i in 0uL until srcCount) {
            val endpoint = MIDIGetSource(i)
            if (endpoint == 0u) continue

            val offline = getIntegerProp(endpoint, kMIDIPropertyOffline) ?: 0
            if (offline != 0) continue

            val epUniqueId = getIntegerProp(endpoint, kMIDIPropertyUniqueID)
            val portId = epUniqueId?.toString() ?: "vsrc:$endpoint"
            if (portId in processedEndpointIds) continue

            val portName = getStringProp(endpoint, kMIDIPropertyName) ?: "Virtual Input"
            val port = IosMidiPort(
                id = portId,
                name = portName,
                direction = AmethystMidiPortDirection.INPUT,
                portNumber = 0u,
                endpointRef = endpoint
            )
            result.add(
                IosMidiDevice(
                    id = "vdevice_in:$portId",
                    name = portName,
                    manufacturer = "Virtual",
                    model = portName,
                    serialNumber = null,
                    ports = listOf(port)
                )
            )
        }

        // Discover top-level virtual/standalone destinations
        val destCount = MIDIGetNumberOfDestinations()
        for (i in 0uL until destCount) {
            val endpoint = MIDIGetDestination(i)
            if (endpoint == 0u) continue

            val offline = getIntegerProp(endpoint, kMIDIPropertyOffline) ?: 0
            if (offline != 0) continue

            val epUniqueId = getIntegerProp(endpoint, kMIDIPropertyUniqueID)
            val portId = epUniqueId?.toString() ?: "vdest:$endpoint"
            if (portId in processedEndpointIds) continue

            val portName = getStringProp(endpoint, kMIDIPropertyName) ?: "Virtual Output"
            val port = IosMidiPort(
                id = portId,
                name = portName,
                direction = AmethystMidiPortDirection.OUTPUT,
                portNumber = 0u,
                endpointRef = endpoint
            )
            result.add(
                IosMidiDevice(
                    id = "vdevice_out:$portId",
                    name = portName,
                    manufacturer = "Virtual",
                    model = portName,
                    serialNumber = null,
                    ports = listOf(port)
                )
            )
        }

        return result
    }

    override suspend fun openInput(portId: String): AmethystMidiInput {
        check(!isClosed) { "iOS CoreMIDI access is closed" }
        check(client != 0u) { "CoreMIDI client failed to initialize" }

        val endpoint = findSourceEndpoint(portId)
            ?: throw IllegalStateException("MIDI source endpoint not found for portId $portId")

        return IosMidiInput(portId, client, endpoint)
    }

    override suspend fun openOutput(portId: String): AmethystMidiOutput {
        check(!isClosed) { "iOS CoreMIDI access is closed" }
        check(client != 0u) { "CoreMIDI client failed to initialize" }

        val endpoint = findDestinationEndpoint(portId)
            ?: throw IllegalStateException("MIDI destination endpoint not found for portId $portId")

        return IosMidiOutput(portId, client, endpoint)
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        if (client != 0u) {
            MIDIClientDispose(client)
            client = 0u
        }
    }

    private fun findSourceEndpoint(portId: String): MIDIEndpointRef? {
        val srcCount = MIDIGetNumberOfSources()
        for (i in 0uL until srcCount) {
            val ep = MIDIGetSource(i)
            if (ep == 0u) continue
            val uniqueId = getIntegerProp(ep, kMIDIPropertyUniqueID)
            val currentId = uniqueId?.toString() ?: "src:$ep"
            if (currentId == portId || uniqueId?.toString() == portId) {
                return ep
            }
        }
        return null
    }

    private fun findDestinationEndpoint(portId: String): MIDIEndpointRef? {
        val destCount = MIDIGetNumberOfDestinations()
        for (i in 0uL until destCount) {
            val ep = MIDIGetDestination(i)
            if (ep == 0u) continue
            val uniqueId = getIntegerProp(ep, kMIDIPropertyUniqueID)
            val currentId = uniqueId?.toString() ?: "dest:$ep"
            if (currentId == portId || uniqueId?.toString() == portId) {
                return ep
            }
        }
        return null
    }

    private fun getStringProp(obj: MIDIObjectRef, prop: CFStringRef?): String? {
        if (prop == null) return null
        return memScoped {
            val strVar = alloc<CFStringRefVar>()
            val status = MIDIObjectGetStringProperty(obj, prop, strVar.ptr)
            if (status == 0 && strVar.value != null) {
                val cfStr = strVar.value
                val ptr = CFStringGetCStringPtr(cfStr, kCFStringEncodingUTF8)
                val result = if (ptr != null) {
                    ptr.toKString()
                } else {
                    val length = CFStringGetLength(cfStr)
                    val maxSize = CFStringGetMaximumSizeForEncoding(length, kCFStringEncodingUTF8) + 1L
                    val buffer = allocArray<ByteVar>(maxSize)
                    if (CFStringGetCString(cfStr, buffer, maxSize, kCFStringEncodingUTF8)) {
                        buffer.toKString()
                    } else null
                }
                CFRelease(cfStr)
                result
            } else null
        }
    }

    private fun getIntegerProp(obj: MIDIObjectRef, prop: CFStringRef?): Int? {
        if (prop == null) return null
        return memScoped {
            val intVar = alloc<IntVar>()
            val status = MIDIObjectGetIntegerProperty(obj, prop, intVar.ptr)
            if (status == 0) intVar.value else null
        }
    }
}

class IosMidiDevice(
    override val id: String,
    override val name: String,
    override val manufacturer: String?,
    override val model: String?,
    override val serialNumber: String?,
    override val ports: List<AmethystMidiPort>
) : AmethystMidiDevice {
    override val usbVendorId: UShort? = null
    override val usbProductId: UShort? = null
    override val inputPorts: List<AmethystMidiPort> = ports.filter { it.direction == AmethystMidiPortDirection.INPUT }
    override val outputPorts: List<AmethystMidiPort> = ports.filter { it.direction == AmethystMidiPortDirection.OUTPUT }
    override val displayName: String get() = model ?: name
    override val isBidirectional: Boolean get() = inputPorts.isNotEmpty() && outputPorts.isNotEmpty()
}

class IosMidiPort(
    override val id: String,
    override val name: String,
    override val direction: AmethystMidiPortDirection,
    override val portNumber: UInt,
    val endpointRef: MIDIEndpointRef
) : AmethystMidiPort {
    override val isAvailable: Boolean = true
}

@OptIn(ExperimentalForeignApi::class)
class IosMidiInput(
    override val portId: String,
    client: MIDIClientRef,
    private val sourceEndpoint: MIDIEndpointRef
) : AmethystMidiInput {
    private var inputPort: MIDIPortRef = 0u
    private var _isOpen = true
    override val isOpen: Boolean get() = _isOpen

    private val messagesMutable = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val messages: Flow<ByteArray> = messagesMutable.asSharedFlow()

    private val assembler = IosMidiMessageAssembler()

    init {
        memScoped {
            val portRefVar = alloc<MIDIPortRefVar>()
            val portName = CFStringCreateWithCString(null, "Amethyst Input Port", kCFStringEncodingUTF8)
            val status = MIDIInputPortCreateWithBlock(client, portName, portRefVar.ptr) { packetListPtr, _ ->
                if (_isOpen && packetListPtr != null) {
                    val numPackets = packetListPtr.pointed.numPackets
                    var packetPtr: CPointer<MIDIPacket>? = MIDIPacketListInit(packetListPtr)
                    for (i in 0u until numPackets) {
                        if (packetPtr == null) break
                        val packet = packetPtr.pointed
                        val length = packet.length.toInt()
                        if (length > 0) {
                            val bytes = ByteArray(length)
                            for (b in 0 until length) {
                                bytes[b] = packet.data[b].toByte()
                            }
                            val msgs = assembler.feed(bytes, 0, length)
                            msgs.forEach { msg ->
                                messagesMutable.tryEmit(msg)
                            }
                        }
                        val rawPtr = packetPtr.rawValue
                        val nextRawPtr = rawPtr + 10L + length.toLong()
                        packetPtr = interpretCPointer<MIDIPacket>(nextRawPtr)
                    }
                }
            }
            if (portName != null) CFRelease(portName)

            check(status == 0) { "Failed to create MIDI input port (OSStatus $status)" }
            inputPort = portRefVar.value

            val connectStatus = MIDIPortConnectSource(inputPort, sourceEndpoint, null)
            check(connectStatus == 0) { "Failed to connect MIDI source endpoint (OSStatus $connectStatus)" }
        }
    }

    override fun close() {
        if (!_isOpen) return
        _isOpen = false
        if (inputPort != 0u) {
            MIDIPortDisconnectSource(inputPort, sourceEndpoint)
            MIDIPortDispose(inputPort)
            inputPort = 0u
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosMidiOutput(
    override val portId: String,
    client: MIDIClientRef,
    private val destinationEndpoint: MIDIEndpointRef
) : AmethystMidiOutput {
    private var outputPort: MIDIPortRef = 0u
    private var _isOpen = true
    override val isOpen: Boolean get() = _isOpen

    init {
        memScoped {
            val portRefVar = alloc<MIDIPortRefVar>()
            val portName = CFStringCreateWithCString(null, "Amethyst Output Port", kCFStringEncodingUTF8)
            val status = MIDIOutputPortCreate(client, portName, portRefVar.ptr)
            if (portName != null) CFRelease(portName)

            check(status == 0) { "Failed to create MIDI output port (OSStatus $status)" }
            outputPort = portRefVar.value
        }
    }

    override fun send(data: ByteArray) {
        check(_isOpen) { "MIDI output port $portId is closed" }
        if (data.isEmpty()) return

        memScoped {
            val listSize = (1024 + data.size).toULong()
            val buffer = allocArray<ByteVar>(listSize.toLong())
            val pktListPtr = buffer.reinterpret<MIDIPacketList>()

            val curPacketPtr = MIDIPacketListInit(pktListPtr)
            val dataRef = data.refTo(0)

            val nextPacketPtr = MIDIPacketListAdd(
                pktListPtr,
                listSize,
                curPacketPtr,
                0uL,
                data.size.toULong(),
                dataRef.getPointer(this).reinterpret()
            )

            if (nextPacketPtr != null) {
                var status = MIDISend(outputPort, destinationEndpoint, pktListPtr)
                var retries = 0
                while (status != 0 && retries < 3) {
                    retries++
                    platform.posix.usleep(1000u)
                    status = MIDISend(outputPort, destinationEndpoint, pktListPtr)
                }
                check(status == 0) {
                    "CoreMIDI send failed for output $portId (OSStatus $status)"
                }
            }
        }
    }

    override fun sendSysEx(data: ByteArray) {
        send(normalizeSysEx(data))
    }

    override fun sendDeviceInquiry() {
        send(byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte()))
    }

    override fun close() {
        if (!_isOpen) return
        _isOpen = false
        if (outputPort != 0u) {
            MIDIPortDispose(outputPort)
            outputPort = 0u
        }
    }

    private fun normalizeSysEx(data: ByteArray): ByteArray = when {
        data.isEmpty() -> byteArrayOf(0xF0.toByte(), 0xF7.toByte())
        data.first() == 0xF0.toByte() && data.last() == 0xF7.toByte() -> data
        data.first() == 0xF0.toByte() -> data + 0xF7.toByte()
        data.last() == 0xF7.toByte() -> byteArrayOf(0xF0.toByte()) + data
        else -> byteArrayOf(0xF0.toByte()) + data + 0xF7.toByte()
    }
}

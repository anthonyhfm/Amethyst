package dev.anthonyhfm.amethyst.core.midi

/** Reassembles MIDI byte streams before they reach shared Launchpad detection code. */
internal class AndroidMidiMessageAssembler {
    /**
     * Some Android USB-MIDI drivers report a complete transfer buffer instead of
     * its payload length. A large all-zero tail is not a valid continuation on
     * its own, but would be parsed as running-status messages.
     */
    private companion object {
        const val ZERO_PADDING_THRESHOLD = 32
    }

    private val current = ArrayList<Byte>()
    private var runningStatus: Int? = null
    private var expectedDataBytes = 0
    private var inSysEx = false

    fun feed(data: ByteArray, offset: Int, count: Int): List<ByteArray> {
        require(offset >= 0 && count >= 0 && offset + count <= data.size) {
            "Invalid MIDI buffer range: offset=$offset, count=$count, size=${data.size}"
        }
        if (count == 0) return emptyList()

        val trailingZeros = data.trailingZeroCount(offset, count)
        if (trailingZeros < ZERO_PADDING_THRESHOLD) {
            return feedRange(data, offset, count)
        }

        val payloadCount = count - trailingZeros
        val messages = feedRange(data, offset, payloadCount).toMutableList()

        // Preserve a legitimate final data byte such as Note On velocity 0 or a
        // byte that finishes a message fragmented across two receiver calls.
        val completionBytes = pendingDataBytes().coerceAtMost(trailingZeros)
        if (completionBytes > 0) {
            messages += feedRange(data, offset + payloadCount, completionBytes)
        }
        return messages
    }

    fun clear() {
        current.clear()
        runningStatus = null
        expectedDataBytes = 0
        inSysEx = false
    }

    private fun feedRange(data: ByteArray, offset: Int, count: Int): List<ByteArray> {
        val messages = mutableListOf<ByteArray>()
        for (index in offset until offset + count) {
            val value = data[index].toInt() and 0xFF
            when {
                value >= 0xF8 -> messages += byteArrayOf(value.toByte())
                inSysEx -> {
                    current += value.toByte()
                    if (value == 0xF7) {
                        messages += current.toByteArray()
                        resetMessage()
                        inSysEx = false
                    }
                }
                value == 0xF0 -> {
                    resetMessage()
                    current += value.toByte()
                    inSysEx = true
                    runningStatus = null
                }
                value >= 0x80 -> startStatus(value, messages)
                else -> appendData(value, messages)
            }
        }
        return messages
    }

    private fun pendingDataBytes(): Int {
        if (inSysEx || current.isEmpty()) return 0
        return (expectedDataBytes + 1 - current.size).coerceAtLeast(0)
    }

    private fun startStatus(status: Int, messages: MutableList<ByteArray>) {
        resetMessage()
        current += status.toByte()
        expectedDataBytes = dataLength(status)
        runningStatus = if (status in 0x80..0xEF) status else null
        if (expectedDataBytes == 0) {
            messages += current.toByteArray()
            resetMessage()
        }
    }

    private fun appendData(value: Int, messages: MutableList<ByteArray>) {
        if (current.isEmpty()) {
            val status = runningStatus ?: return
            current += status.toByte()
            expectedDataBytes = dataLength(status)
        }
        current += value.toByte()
        if (current.size == expectedDataBytes + 1) {
            messages += current.toByteArray()
            val status = runningStatus
            resetMessage()
            if (status != null) expectedDataBytes = dataLength(status)
        }
    }

    private fun resetMessage() {
        current.clear()
        expectedDataBytes = 0
    }

    private fun dataLength(status: Int): Int = when (status and 0xF0) {
        0x80, 0x90, 0xA0, 0xB0, 0xE0 -> 2
        0xC0, 0xD0 -> 1
        else -> when (status) {
            0xF1, 0xF3 -> 1
            0xF2 -> 2
            else -> 0
        }
    }
}

internal fun ByteArray.trailingZeroCount(offset: Int, count: Int): Int {
    var index = offset + count - 1
    while (index >= offset && this[index] == 0.toByte()) index--
    return offset + count - 1 - index
}

package dev.anthonyhfm.amethyst.core.midi

import platform.objc.objc_sync_enter
import platform.objc.objc_sync_exit

/** Reassembles MIDI byte streams on iOS before passing messages to Launchpad listeners. */
internal class IosMidiMessageAssembler {
    private val current = ArrayList<Byte>()
    private var runningStatus: Int? = null
    private var expectedDataBytes = 0
    private var inSysEx = false

    fun feed(data: ByteArray, offset: Int, count: Int): List<ByteArray> {
        require(offset >= 0 && count >= 0 && offset + count <= data.size) {
            "Invalid MIDI buffer range: offset=$offset, count=$count, size=${data.size}"
        }
        if (count == 0) return emptyList()

        objc_sync_enter(this)
        try {
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
        } finally {
            objc_sync_exit(this)
        }
    }

    fun clear() {
        objc_sync_enter(this)
        try {
            current.clear()
            runningStatus = null
            expectedDataBytes = 0
            inSysEx = false
        } finally {
            objc_sync_exit(this)
        }
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

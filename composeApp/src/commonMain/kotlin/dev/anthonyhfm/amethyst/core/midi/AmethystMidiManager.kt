package dev.anthonyhfm.amethyst.core.midi

import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * # Amethyst Midi Manager
 *
 * The Amethyst Midi Manager should be able to recognize device types based on input and output device.
 */
class AmethystMidiManager {
    @OptIn(ExperimentalUnsignedTypes::class)
    fun detect(input: MidiInput, output: MidiOutput): LaunchpadDeviceType? {
        val deviceInquiry = ubyteArrayOf(
            240u,
            126u,
            127u,
            6u,
            1u,
            247u
        )

        var deviceType: LaunchpadDeviceType? = null
        var wait = true
        var waitCounter = 0

        input.setMessageReceivedListener({ data, _, _, _ ->
            deviceType = getDeviceTypeByInquiry(data)

            if (deviceType != null) {
                wait = false
            }

            println(data.toMutableList().map { it.toUByte() })
        })

        output.send(
            mevent = deviceInquiry.toByteArray(),
            offset = 0,
            length = deviceInquiry.size,
            timestampInNanoseconds = 0,
        )

        runBlocking {
            while (wait && waitCounter < 2000) {
                waitCounter += 1
                delay(1)
            }
        }

        return deviceType
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun getDeviceTypeByInquiry(data: ByteArray): LaunchpadDeviceType? {
        val convertedData = data.toUByteArray()
        val messageStart = data.toUByteArray().indexOf(240u)
        val messageEnd = data.toUByteArray().indexOf(247u)

        // Checks if the received message is a sysex message
        if (messageStart == -1 || messageEnd == -1) return null

        val sysex = convertedData.copyOfRange(messageStart, messageEnd + 1)

        // Checks if the received message is a valid inquiry response
        if (sysex[1] == 126.toUByte() && sysex[2] == 127.toUByte()) {
            
        }

        return null
    }
}
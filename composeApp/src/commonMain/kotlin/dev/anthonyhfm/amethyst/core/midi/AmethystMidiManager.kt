package dev.anthonyhfm.amethyst.core.midi

import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceMystrix
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevicePro
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceProMk3
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceX
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
    val inquiryTests: Map<LaunchpadDeviceType, (UByteArray) -> Boolean> = mapOf(
        LaunchpadDeviceType.LAUNCHPAD_PRO_MK3 to { LaunchpadDeviceProMk3.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_X to { LaunchpadDeviceX.identify(it) },
        LaunchpadDeviceType.LAUNCHPAD_PRO_CFW to { LaunchpadDevicePro.identifyCFW(it) },
        LaunchpadDeviceType.LAUNCHPAD_PRO to { LaunchpadDevicePro.identify(it) },
        LaunchpadDeviceType.MYSTRIX to { LaunchpadDeviceMystrix.identify(it) }
    )

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
        val timeout = 2000
        
        val listener: (ByteArray, Int, Long, Long) -> Unit = { data, _, _, _ ->
            val detectedType = getDeviceTypeByInquiry(data)
            if (detectedType != null) {
                deviceType = detectedType
                wait = false
            }
        }

        input.setMessageReceivedListener(
            listener = { data, _, _, _ ->
                listener(data, 0, 0, 0)
            }
        )

        output.send(
            mevent = deviceInquiry.toByteArray(),
            offset = 0,
            length = deviceInquiry.size,
            timestampInNanoseconds = 0,
        )

        runBlocking {
            while (wait && waitCounter < timeout) {
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

        if (messageStart == -1 || messageEnd == -1) return null

        val sysex = convertedData.copyOfRange(messageStart, messageEnd + 1)

        if (sysex[1] == 126.toUByte()) {
            return inquiryTests.entries.find {
                it.value(sysex)
            }?.key
        }

        return null
    }
}

package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiArpeggiator
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.ableton.AbletonArpeggiatorChainDeviceState
import kotlin.time.Duration.Companion.milliseconds

class MidiArpeggiatorAdapter(
    private val device: MidiArpeggiator
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val palette = AbletonConverter.palette
        val velocity = device.velocityTarget.manual.value

        return listOf(
            AbletonArpeggiatorChainDeviceState(
                rate = if (device.syncState.manual.value) {
                    Timing.Rythm(Timing.Rythm.RythmTiming.entries[device.syncRate.manual.value])
                } else {
                    Timing.Duration(device.freeRate.manual.value.toInt().milliseconds)
                },
                distance = device.transposeDistance.manual.value,
                steps = device.transposeSteps.manual.value,
                repeats = device.repeatCount.manual.value,
                color = if (device.velocityEnabled.manual.value) {
                    Triple(
                        palette[velocity].first / 63f,
                        palette[velocity].second / 63f,
                        palette[velocity].third / 63f,
                    )
                } else null,
                gate = device.gate.manual.value
            )
        ).withMuteState(device.on.manual.value)
    }
}
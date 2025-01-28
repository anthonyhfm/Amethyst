package dev.anthonyhfm.amethyst.core.data.project

import dev.anthonyhfm.amethyst.core.midi.devices.DeviceType
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput

data class ProjectDeviceConfig(
    val name: String,
    val input: MidiInput? = null,
    val output: MidiOutput? = null,
    val type: DeviceType? = null
)
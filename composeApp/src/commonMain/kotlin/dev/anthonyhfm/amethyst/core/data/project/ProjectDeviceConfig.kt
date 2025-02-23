package dev.anthonyhfm.amethyst.core.data.project

import dev.anthonyhfm.amethyst.core.midi.devices.DeviceType
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ProjectDeviceConfig(
    @Transient
    val input: MidiInput? = null,
    @Transient
    val output: MidiOutput? = null,
    val type: DeviceType? = null
)
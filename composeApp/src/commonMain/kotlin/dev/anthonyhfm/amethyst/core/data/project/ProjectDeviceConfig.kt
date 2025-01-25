package dev.anthonyhfm.amethyst.core.data.project

import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput

data class ProjectDeviceConfig(
    val name: String,
    val input: MidiInput? = null,
    val output: MidiOutput? = null,
)
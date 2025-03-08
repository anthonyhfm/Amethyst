package dev.anthonyhfm.amethyst.core.midi

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.WebMidiAccess

actual val platformMidiAccess: MidiAccess
    get() = WebMidiAccess()
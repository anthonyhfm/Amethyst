package dev.anthonyhfm.amethyst.core.midi

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.UmpCoreMidiAccess

actual val platformMidiAccess: MidiAccess
    get() = UmpCoreMidiAccess()
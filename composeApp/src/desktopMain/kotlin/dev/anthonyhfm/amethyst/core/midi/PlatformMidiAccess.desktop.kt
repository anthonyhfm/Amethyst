package dev.anthonyhfm.amethyst.core.midi

import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.MidiAccess

actual val platformMidiAccess: MidiAccess = JvmMidiAccess()
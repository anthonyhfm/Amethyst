package dev.anthonyhfm.amethyst.core.midi

import dev.anthonyhfm.amethyst.core.midi.mac.CoreMidi4JAccess
import dev.atsushieno.ktmidi.AlsaMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.MidiAccess
import java.io.File

actual val platformMidiAccess: MidiAccess =
    if (File("/dev/snd/seq").exists())
        AlsaMidiAccess()
    else if (System.getProperty("os.name").contains("Windows"))
        JvmMidiAccess()
    else
        CoreMidi4JAccess()
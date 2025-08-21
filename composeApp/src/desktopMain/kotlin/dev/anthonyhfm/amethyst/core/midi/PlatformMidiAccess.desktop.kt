package dev.anthonyhfm.amethyst.core.midi

import dev.anthonyhfm.amethyst.core.midi.linux.LinuxJVMAccess
import dev.anthonyhfm.amethyst.core.midi.mac.CoreMidi4JAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.MidiAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

actual val platformMidiAccess: MidiAccess =
    if (System.getProperty("os.name").contains("Linux"))
        LinuxJVMAccess()
    else if (System.getProperty("os.name").contains("Windows"))
        JvmMidiAccess()
    else
        CoreMidi4JAccess()
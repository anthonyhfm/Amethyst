package dev.anthonyhfm.amethyst.core.midi

import dev.anthonyhfm.amethyst.MainActivity
import dev.atsushieno.ktmidi.AndroidMidi2Access
import dev.atsushieno.ktmidi.MidiAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

actual val platformMidiAccess: MidiAccess
    get() = AndroidMidi2Access(MainActivity.context, true)
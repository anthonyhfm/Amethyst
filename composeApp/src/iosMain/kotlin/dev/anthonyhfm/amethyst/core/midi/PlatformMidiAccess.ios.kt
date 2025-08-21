package dev.anthonyhfm.amethyst.core.midi

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.TraditionalCoreMidiAccess
import dev.atsushieno.ktmidi.UmpCoreMidiAccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val platformMidiAccess: MidiAccess
    get() = TraditionalCoreMidiAccess()
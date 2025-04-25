package dev.anthonyhfm.amethyst.core.midi

import dev.atsushieno.ktmidi.MidiAccess
import org.koin.dsl.bind
import org.koin.dsl.module

val midiKoinModule = module {
    single { platformMidiAccess } bind MidiAccess::class

    single { AmethystMidiManager() }
}
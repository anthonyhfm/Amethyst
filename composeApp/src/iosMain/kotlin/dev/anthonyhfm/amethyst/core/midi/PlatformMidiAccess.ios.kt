package dev.anthonyhfm.amethyst.core.midi

actual val platformMidiAccess: AmethystMidiAccess? by lazy {
    IosMidiAccess()
}

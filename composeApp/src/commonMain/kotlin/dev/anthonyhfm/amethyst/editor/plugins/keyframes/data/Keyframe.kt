package dev.anthonyhfm.amethyst.editor.plugins.keyframes.data

import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData

data class Keyframe(
    val frame: List<List<MidiEffectData>> = List(10) { x ->
        List(10) { y ->
            MidiEffectData(
                x = x,
                y = y,
                r = 0,
                g = 0,
                b = 0
            )
        }
    }
)
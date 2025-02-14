package dev.anthonyhfm.amethyst.devices.effects.keyframes.data

import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID

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
    },
    internal val uuid: String = UUID.randomUUID()
)
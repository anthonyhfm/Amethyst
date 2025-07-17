package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.anthonyhfm.amethyst.core.heaven.elements.Screen
import dev.anthonyhfm.amethyst.core.midi.IO_COROUTINE
import dev.atsushieno.ktmidi.MidiOutput

abstract class LaunchpadDevice {
    val screen: Screen = Screen()

    protected val outscope = IO_COROUTINE

    init {
        screen.screenExit = { updates, colors ->
            sendUpdate(updates, colors)
        }
    }

    abstract var midiOutput: MidiOutput

    abstract fun clear()

    abstract fun sendUpdate(updates: List<RawUpdate>, colors: Array<Color>)

    abstract fun getEffectSysEx(updates: List<RawUpdate>): ByteArray
}

enum class LaunchpadDeviceType(val label: String) {
    LAUNCHPAD_PRO_MK3(
        label = "Launchpad Pro Mk3"
    ),
    LAUNCHPAD_X(
        label = "Launchpad X"
    ),
    LAUNCHPAD_PRO(
        label = "Launchpad Pro"
    ),
    LAUNCHPAD_PRO_CFW(
        label = "Launchpad Pro (CFW)"
    ),
    LAUNCHPAD_MK2(
        label = "Launchpad Pro"
    ),
    MYSTRIX(
        label = "Mystrix"
    ),
    ABLETON_PUSH_2(
        label = "Ableton Push 2"
    ),
}
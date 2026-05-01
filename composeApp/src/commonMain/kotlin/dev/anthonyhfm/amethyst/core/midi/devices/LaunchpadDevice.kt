package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.engine.heaven.Screen
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

abstract class LaunchpadDevice {
    val screen: Screen = Screen()

    protected val outscope = CoroutineScope(Dispatchers.Default)

    init {
        screen.screenExit = { updates, colors ->
            sendUpdate(updates, colors)
        }
    }

    abstract var midiOutput: MidiOutput

    abstract fun clear()

    abstract fun sendUpdate(updates: List<RawLEDUpdate>, colors: Array<Color>)

    abstract fun getEffectSysEx(updates: List<RawLEDUpdate>): ByteArray

    open fun handleMidiInput(inputData: ByteArray): MidiInputData? {
        return null
    }
}

enum class LaunchpadDeviceType(val label: String) {
    LAUNCHPAD_PRO_MK3(
        label = "Launchpad Pro Mk3"
    ),
    LAUNCHPAD_X(
        label = "Launchpad X"
    ),
    LAUNCHPAD_MINI_MK3(
        label = "Launchpad Mini Mk3"
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
}
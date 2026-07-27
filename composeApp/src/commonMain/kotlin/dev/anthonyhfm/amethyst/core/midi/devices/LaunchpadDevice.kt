package dev.anthonyhfm.amethyst.core.midi.devices

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.engine.heaven.Screen
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiOutput
import dev.anthonyhfm.amethyst.core.midi.AmethystMidiDeviceConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

abstract class LaunchpadDevice(
    open val connection: AmethystMidiDeviceConnection
) {
    val screen: Screen = Screen()

    protected val outscope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    private val sendChannel = Channel<ByteArray>(Channel.UNLIMITED)

    init {
        screen.screenExit = { updates, colors ->
            sendUpdate(updates, colors)
        }

        outscope.launch {
            for (data in sendChannel) {
                runCatching {
                    midiOutput.send(data)
                }
            }
        }
    }

    val midiOutput: AmethystMidiOutput get() = connection.output

    abstract fun clear()

    abstract fun sendUpdate(updates: List<RawLEDUpdate>, colors: Array<Color>)

    abstract fun getEffectSysEx(updates: List<RawLEDUpdate>): ByteArray

    open fun handleMidiInput(inputData: ByteArray): MidiInputData? {
        return dev.anthonyhfm.amethyst.core.midi.data.getMidiInputData(inputData)
    }

    protected fun sendMidi(data: ByteArray) {
        sendChannel.trySend(data)
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

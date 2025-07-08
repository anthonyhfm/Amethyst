package dev.anthonyhfm.amethyst.workspace.chain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.flow.MutableStateFlow

class WorkspaceChain(
    private val isSampling: Boolean = false
) {
    var heavenChain = Chain()
        set(value) {
            field = value

            if (!isSampling) {
                heavenChain.midiExit = {
                    Heaven.midiEnter(it)
                }
            }
        }

    init {
        if (!isSampling) {
            heavenChain.midiExit = {
                Heaven.midiEnter(it)
            }
        }
    }

    fun onMidiInput(inputData: MidiInputData, offset: Offset) {
        val x = inputData.pitch % 10
        val y = inputData.pitch / 10
        val posX = offset.x.toInt()
        val posY = offset.y.toInt()

        val signal = Signal(
            origin = this,
            x = posX + x,
            y = posY + (9 - y),
            color = if (inputData.velocity == 0) Color.Black else Color.White,
            layer = 0
        )

        heavenChain.midiEnter(signal)
    }

    fun addDevice(device: ChainDevice<*>, atIndex: Int?) {
        heavenChain.add(device, atIndex)
    }

    /**
     * Reorders a device in the chain
     *
     * @param fromIndex the current index of the device to be moved
     * @param toIndex the target index to which the device will be moved
     */
    fun reorderDevice(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return

        val devices = heavenChain.devices.value.toMutableList()
        if (fromIndex < 0 || fromIndex >= devices.size || toIndex < 0 || toIndex >= devices.size) return

        val device = devices.removeAt(fromIndex)
        devices.add(toIndex, device)

        // Update the devices list in the chain
        heavenChain.devices.value = devices

        heavenChain.reroute()
    }
}
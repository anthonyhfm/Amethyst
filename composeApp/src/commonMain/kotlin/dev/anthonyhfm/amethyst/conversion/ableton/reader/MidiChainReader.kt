package dev.anthonyhfm.amethyst.conversion.ableton.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

class MidiChainReader(
    offset: IntOffset = IntOffset.Zero,
    outputOffset: IntOffset = IntOffset.Zero,
) {
    fun readMidiChain(xmlElement: XmlElement): StateChain {
        val chainDevices = xmlElement.querySelector("DeviceChain")[0]
            .querySelector("DeviceChain")[0]
            .querySelector("Devices")[0].children

        val adapters = chainDevices.map {
            AbletonAdapter.resolveAdapter(it)
        }

        return StateChain(
            devices = adapters.filter { it != null }.map {
                it!!.toDeviceStates()
            }.flatten()
        )
    }

    fun getChainWeight(xmlElement: XmlElement): Int {
        val chainDevices = xmlElement.querySelector("DeviceChain")[0]
            .querySelector("DeviceChain")[0]
            .querySelector("Devices")[0]

        return chainDevices.getRecursiveChildrenCount()
    }
}
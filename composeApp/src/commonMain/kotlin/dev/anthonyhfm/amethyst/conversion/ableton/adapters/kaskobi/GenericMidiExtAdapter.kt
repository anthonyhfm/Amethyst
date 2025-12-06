package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.devices.DeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

class GenericMidiExtAdapter(
    private val device: MxDeviceMidiEffect,
    private val offset: IntOffset,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val fileRef = device.fileDropList.fileDropList.items.firstOrNull()?.ref?.fileRef ?: return emptyList()

        println(fileRef.resolvePath())

        val palette = AbletonConverter.palette

        val filePath: String = fileRef.resolvePath()

        val data = if (AbletonConverter.isZip) {
            AbletonConverter.zipEntries[filePath]?.data ?: return emptyList()
        } else {
            try {
                runBlocking { PlatformFile(filePath).readBytes() }
            } catch (e: Exception) {
                return emptyList()
            }
        }

        return listOf(
            MidiFileImporter.loadData(
                data = data,
                palette = palette,
                bpm = AbletonConverter.bpm,
                xyOffset = offset
            )
        )
    }
}
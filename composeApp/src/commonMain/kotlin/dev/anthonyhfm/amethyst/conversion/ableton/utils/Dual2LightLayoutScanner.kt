package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter.Companion.fileHashMap
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsMixerAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import io.github.vinceglb.filekit.PlatformFile

object Dual2LightLayoutScanner {
    fun scanTrackForMixer(track: MidiTrack, offset: IntOffset) {
        val maxDevices = MidiChainReader.getAllDevicesOfType<MxDeviceMidiEffect>(track)

        maxDevices.forEach {
            val path = it.patchSlot.value.patchRef?.fileRef?.resolvePath() ?: return@forEach

            val hash: String = fileHashMap[path].let {
                if (it != null) {
                    return@let it
                } else {
                    val hash = if (AbletonConverter.isZip) {
                        val maxFile = AbletonConverter.zipEntries[path]!!.data
                        maxFile.toFileHash()
                    } else {
                        val maxFile = PlatformFile(path)
                        maxFile.getFileHash()
                    }

                    fileHashMap[path] = hash

                    return@let hash
                }
            }

            when (hash) {
                "11d58c1fb5d3ba0593e905ee8940652c" -> {
                    DepthsMixerAdapter(it.decodeBlob(), offset).toDeviceStates()
                }
            }
        }
    }
}
package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter.Companion.fileHashMap
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.MxDeviceMidiEffectAdapter.Companion.readDataBlob
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsMixerAdapter
import io.github.vinceglb.filekit.PlatformFile

object Dual2LightLayoutScanner {
    fun scanTrackForMixer(track: XmlElement, offset: IntOffset) {
        val maxDevices = track.querySelector("MxDeviceMidiEffect")

        maxDevices.forEach {
            val patchSlot = it.localQuerySelector("PatchSlot")[0]
                .localQuerySelector("Value")[0]
                .localQuerySelector("MxDPatchRef")[0]

            val blob = it.localQuerySelector("BlobSlot")[0]
                .localQuerySelector("Value")[0]
                .localQuerySelector("MxDBlob")[0]
                .localQuerySelector("Blob")[0]

            val path = FileRef.resolveFileReference(patchSlot.localQuerySelector("FileRef").first())

            val hash: String = fileHashMap[path].let {
                if (it != null) {
                    return@let it
                } else {
                    val maxFile = PlatformFile(path)
                    val hash = maxFile.getFileHash()

                    fileHashMap[path] = hash

                    return@let hash
                }
            }

            when (hash) {
                "11d58c1fb5d3ba0593e905ee8940652c" -> {
                    DepthsMixerAdapter(readDataBlob(blob.text!!), offset).toDeviceStates()
                }
            }
        }
    }
}
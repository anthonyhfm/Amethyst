package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.utils.MultiPluginHashes.KASKOBI_MULTI_HASHES
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton.utils.MultiPluginHashes.MULTI_HASHES
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.dovitate.CycleLightsAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.dovitate.LightspeedAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.dovitate.SupersonicAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.AutoPageAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GenericMidiExtAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GridFilterAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.MidiLauncherAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.MidiLauncherProAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.PageSwitcherAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.Resonator1Adapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.Resonator2Adapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.Resonator3Adapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.nev.WormholeAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DelayAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsSelectorAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.FlipAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.InfinityAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.IrisAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.TwistAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceInstrument
import dev.anthonyhfm.amethyst.conversion.ableton.utils.ProjectSpecials
import dev.anthonyhfm.amethyst.conversion.ableton.utils.getFileHash
import dev.anthonyhfm.amethyst.conversion.ableton.utils.toFileHash
import dev.anthonyhfm.amethyst.devices.DeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.nameWithoutExtension

class MxDeviceInstrumentAdapter(
    private val device: MxDeviceInstrument,
    val offset: IntOffset = IntOffset.Zero,
    val outputOffset: IntOffset = IntOffset.Zero
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val blob = device.decodeBlob()

        val path = device.patchSlot.value.patchRef?.fileRef?.resolvePath() ?: return emptyList()

        val hash: String = fileHashMap[path].let {
            if (it != null) {
                return@let it
            } else {
                val hash: String = if (AbletonConverter.isZip) {
                    val entry = AbletonConverter.zipEntries[path]
                    val computed = entry?.data?.toFileHash() ?: ""

                    AbletonConverter.zipEntries.remove(path)
                    computed
                } else {
                    val maxFile = PlatformFile(path)
                    maxFile.getFileHash()
                }

                fileHashMap[path] = hash

                return@let hash
            }
        }

        try {
            when (hash) {
                "a65f124fa2b4df604144ec3bb78df008" -> {
                    return SupersonicAdapter(device, offset).toDeviceStates()
                }

                else -> {
                    val maxFile = PlatformFile(path)

                    println("Max instrument not supported: ${maxFile.nameWithoutExtension} - Hash: $hash")

                    return emptyList()
                }
            }
        } catch (e: Exception) {
            val maxFile = PlatformFile(path)

            println("Error while converting Max instrument (${maxFile.nameWithoutExtension}) - Hash: $hash")
            e.printStackTrace()

            return emptyList()
        }
    }

    companion object {
        val fileHashMap: MutableMap<String, String> = mutableMapOf()
    }
}

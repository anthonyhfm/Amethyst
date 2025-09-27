package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.clip.ClipChainDeviceState
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

class OriginalSimplerAdapter(
    private val xml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = getSimplerData(xml)

        return listOf(
            AbletonConverter.audioMap[data] ?: ClipChainDeviceState()
        )
    }

    data class OriginalSimplerData(
        val filePath: String,
        val sampleStart: Long,
        val sampleEnd: Long
    )

    companion object {
        fun getSimplerData(xml: XmlElement): OriginalSimplerData {
            val player = xml.localQuerySelector("Player")[0]
            val samplePart = player.querySelector("MultiSamplePart").getOrNull(0)
            val sampleRef = samplePart!!.localQuerySelector("SampleRef")[0]

            val filePath: String = FileRef.resolveFileReference(sampleRef.querySelector("FileRef")[0])

            val sampleStart = samplePart.querySelector("SampleStart")[0].attributes["Value"]?.toLong() ?: 0L
            val sampleEnd = samplePart.querySelector("SampleEnd")[0].attributes["Value"]?.toLong() ?: 0L

            return OriginalSimplerData(
                filePath = filePath,
                sampleStart = sampleStart,
                sampleEnd = sampleEnd
            )
        }
    }
}
package dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.DepthsSelectorAdapter.DepthsSelectorData
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDevice
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.ui.components.asTiming
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class IrisAdapter (
    private val data: ByteArray
): AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<IrisSelectorData>(data.decodeToString())

        val bpm = AbletonConverter.bpm

        val novationPalette = Palettes.novation
        val filteredVelocities = data.fadeVelocities.filter { it != 0 }

        val irisRateTiming = mapOf(
            0 to Timing.Duration(rythmIndexToDuration("1/128", bpm, filteredVelocities.size)),
            1 to Timing.Duration(rythmIndexToDuration("1/64", bpm, filteredVelocities.size)),
            2 to Timing.Duration(rythmIndexToDuration("1/48", bpm, filteredVelocities.size)), // 1/48 which does not exist in Amethyst (use 1/64 instead)
            3 to Timing.Duration(rythmIndexToDuration("1/32", bpm, filteredVelocities.size)),
            4 to Timing.Duration(rythmIndexToDuration("1/24", bpm, filteredVelocities.size)), // 1/24 which does not exist in Amethyst (use 1/32 instead)
            5 to Timing.Duration(rythmIndexToDuration("1/16", bpm, filteredVelocities.size)),
            6 to Timing.Duration(rythmIndexToDuration("1/12", bpm, filteredVelocities.size)), // 1/12 which does not exist in Amethyst (use 500ms instead)
            7 to Timing.Duration(rythmIndexToDuration("1/8", bpm, filteredVelocities.size)),
            8 to Timing.Duration(rythmIndexToDuration("1/4", bpm, filteredVelocities.size))
        )

        val timing = when {
            data.timingByMsStatus.first().toInt() == 0 ->  {
                irisRateTiming
                    .getOrElse(data.timingRate.first().toInt()) { Timing.Rythm(Timing.Rythm.RythmTiming._1_8) } // if we cannot resolve, default to 1/8
            }
            data.timingByMsStatus.first().toInt() == 1 -> {
                Timing.Duration(data.timingMs.first().milliseconds * filteredVelocities.size)
            }
            else -> Timing.Rythm(Timing.Rythm.RythmTiming._1_8)
        }

        return listOf(
            GradientChainDeviceState(
                gradientData = List(filteredVelocities.size) { index ->
                    val color = novationPalette.getOrElse(filteredVelocities[index]) { index -> Triple(0, 0, 0) }

                    GradientChainDeviceState.GradientColor(
                        position = index.toFloat() / (filteredVelocities.size - 1),
                        r = color.first.toFloat() / 63f,
                        g = color.second.toFloat() / 63f,
                        b = color.third.toFloat() / 63f,
                    )
                },
                timing = timing,
                durationMs = timing.let {
                    when (it) {
                        is Timing.Duration -> it.duration.inWholeMilliseconds.toInt() * filteredVelocities.size
                        is Timing.Rythm -> rythmIndexToDuration(it.timing.text, bpm, filteredVelocities.size).inWholeMilliseconds.toInt()
                    }
                }.toDouble(),
            )
        )
    }

    fun rythmIndexToDuration(timing: String, bpm: Double, steps: Int): Duration {
        val factor = when (timing) {
            "1/128" -> 1 / 128f
            "1/64" -> 1 / 64f
            "1/48" -> 1 / 48f
            "1/32" -> 1 / 32f
            "1/24" -> 1 / 24f
            "1/16" -> 1 / 16f
            "1/12" -> 1 / 12f
            "1/8" -> 1 / 8f
            "1/4" -> 1 / 4f
            "1/2" -> 1 / 2f
            "1/1" -> 1f
            "2/1" -> 2f
            "4/1" -> 4f
            else -> 1 / 8f // Default to 1/8 if unknown
        }

        val fraction = factor * 4
        val secondsPerQuarter = 60.0 / bpm
        println("Converted $timing at $bpm BPM to ${(secondsPerQuarter * fraction * 1000).toInt()} ms")
        return ((secondsPerQuarter * fraction * 1000).toInt() * steps).milliseconds
    }

    @Serializable
    data class IrisSelectorData(
        @SerialName("Rate")
        val timingRate: List<Double>,
        @SerialName("Rate[2]")
        val timingMs: List<Double>,

        // TODO: add support for loop mode

        @SerialName("live.text[1]")
        val timingByMsStatus: List<Double>,

        @SerialName("table")
        val fadeVelocities: List<Int>,
    )
}
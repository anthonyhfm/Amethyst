package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils.rythmIndexToDuration
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDeviceState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MidiNoteLengthAdapter(
    private val xml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val isNoteOff = xml
            .querySelector("Mode")
            .first()
            .querySelector("Manual")
            .first()
            .attributes["Value"]?.toBoolean() ?: false

        val isSync = xml
            .querySelector("SyncState")
            .first()
            .querySelector("Manual")
            .first()
            .attributes["Value"]?.toBoolean() ?: false

        val timeLength = xml
            .querySelector("TimeLength")
            .first()
            .querySelector("Manual")
            .first()
            .attributes["Value"]?.toDouble() ?: 100.0

        val syncedLength: Duration = xml
            .querySelector("SyncedLength")
            .first()
            .querySelector("Manual")
            .first()
            .attributes["Value"]?.toInt()
            .let {
                val stringTiming = indexToSyncTiming(it ?: 4)

                return@let rythmIndexToDuration(stringTiming, AbletonConverter.bpm, 1)
            }

        val gate = xml
            .querySelector("Gate")
            .first()
            .querySelector("Manual")
            .first()
            .attributes["Value"]?.toInt() ?: 100

        return listOf(
            HoldChainDeviceState(
                onRelease = isNoteOff,
                timing = if (isSync) {
                    Timing.Duration(syncedLength)
                } else {
                    Timing.Duration(timeLength.milliseconds)
                },
                delayMs = if (isSync) {
                    syncedLength.inWholeMilliseconds
                } else {
                    timeLength.toLong()
                },
                gate = gate / 200.0f,
            )
        )
    }

    fun indexToSyncTiming(index: Int): String {
        return when (index) {
            0 -> "1/128"
            1 -> "1/64"
            2 -> "1/48"
            3 -> "1/32"
            4 -> "1/24"
            5 -> "1/16"
            6 -> "1/12"
            7 -> "1/8"
            8 -> "1/4"
            else -> "1/8" // Default to 1/8 if unknown
        }
    }
}
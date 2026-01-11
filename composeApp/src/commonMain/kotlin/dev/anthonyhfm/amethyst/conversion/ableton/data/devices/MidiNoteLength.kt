package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MidiNoteLength(
    @SerialName("Id")
    val id: Int,
    val mode: Mode,
    val syncState: SyncState,
    val timeLength: TimeLength,
    val syncedLength: SyncedLength,
    val gate: Gate
) : AbletonDevice {
    @Serializable
    data class Mode(
        val manual: AbletonManual<Boolean>
    )

    @Serializable
    data class SyncState(
        val manual: AbletonManual<Boolean>
    )

    @Serializable
    data class TimeLength(
        val manual: AbletonManual<Double>
    )

    @Serializable
    data class SyncedLength(
        val manual: AbletonManual<Int>,
    )

    @Serializable
    data class Gate(
        val manual: AbletonManual<Int>,
    )
}
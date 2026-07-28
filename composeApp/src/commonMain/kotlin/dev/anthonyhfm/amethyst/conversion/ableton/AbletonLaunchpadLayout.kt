package dev.anthonyhfm.amethyst.conversion.ableton

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData

internal class AbletonLaunchpadLayout(
    val launchpads: List<SavableWorkspaceData.SavableViewportLaunchpad>,
) {
    internal data class Target(
        val launchpad: SavableWorkspaceData.SavableViewportLaunchpad,
    ) {
        val offset: IntOffset
            get() = IntOffset(
                x = launchpad.positionX.toInt(),
                y = launchpad.positionY.toInt(),
            )

        fun padFilter(localX: Int, localY: Int): LaunchpadPadFilter =
            LaunchpadPadFilter(
                launchpadId = launchpad.id,
                localX = localX,
                localY = localY,
            )

        fun midiImportTarget(): MidiFileImporter.DeviceTarget =
            MidiFileImporter.DeviceTarget(
                launchpadId = launchpad.id,
                offset = offset,
            )
    }

    fun target(index: Int): Target = Target(launchpads[index])

    fun targetAt(offset: IntOffset): Target =
        launchpads.firstOrNull {
            it.positionX.toInt() == offset.x && it.positionY.toInt() == offset.y
        }?.let(::Target)
            ?: error("No Ableton launchpad allocated at $offset")

    fun offsetBetween(fromIndex: Int, toIndex: Int): IntOffset =
        target(toIndex).offset - target(fromIndex).offset

    companion object {
        private const val LAUNCHPAD_SIZE = 10

        fun create(count: Int): AbletonLaunchpadLayout =
            AbletonLaunchpadLayout(
                launchpads = List(count.coerceAtLeast(1)) { index ->
                    SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(
                        positionX = (index * LAUNCHPAD_SIZE).toFloat(),
                        positionY = 0f,
                    )
                },
            )
    }
}

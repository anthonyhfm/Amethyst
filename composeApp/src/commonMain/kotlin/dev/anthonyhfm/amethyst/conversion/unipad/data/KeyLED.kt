package dev.anthonyhfm.amethyst.conversion.unipad.data

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.macro_filter.MacroFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds

object KeyLED {
    /**
     * The mcs seems to be the round side buttons. Weird, but that's how it is.
     * They are indexed instead of being a coordinate in unipad.
     */
    val mcCoordinates = listOf(
        Pair(9, 0),
        Pair(1, 0),
        Pair(2, 0),
        Pair(3, 0),
        Pair(4, 0),
        Pair(5, 0),
        Pair(6, 0),
        Pair(7, 0),
        Pair(8, 0),
        Pair(9, 1),
        Pair(9, 2),
        Pair(9, 3),
        Pair(9, 4),
        Pair(9, 5),
        Pair(9, 6),
        Pair(9, 7),
        Pair(9, 8),
        Pair(8, 9),
        Pair(7, 9),
        Pair(6, 9),
        Pair(5, 9),
        Pair(4, 9),
        Pair(3, 9),
        Pair(2, 9),
        Pair(1, 9),
        Pair(0, 8),
        Pair(0, 7),
        Pair(0, 6),
        Pair(0, 5),
        Pair(0, 4),
        Pair(0, 3),
        Pair(0, 2),
        Pair(0, 1),
    )

    fun convertToKeyframes(data: ByteArray) : KeyframesChainDeviceContract.KeyframesChainDeviceState {
        val events: List<String> = data.decodeToString().trim().split("\n")

        val frames = mutableListOf<KeyframesChainDeviceContract.Frame>()
        var currentFrame: KeyframesChainDeviceContract.Frame = KeyframesChainDeviceContract.Frame(
            timing = Timing.Duration(100.milliseconds)
        )

        events.forEach { event ->
            event.split(" ").let { inst ->
                if (inst[0] == "o") {
                    if (inst.size == 4) {
                        println("Unsupported instruction: $event")
                        return@let
                    }

                    val color: Color = Color(
                        Palettes.novation[inst[4].trim().toInt()].first / 63f,
                        Palettes.novation[inst[4].trim().toInt()].second / 63f,
                        Palettes.novation[inst[4].trim().toInt()].third / 63f,
                    )

                    if (inst[1] == "mc") { // Side buttons
                        currentFrame = currentFrame.copy(
                            entries = currentFrame.entries.filter {
                                (it.x != mcCoordinates[inst[2].toInt()].first || it.y != mcCoordinates[inst[2].toInt()].second)
                            }.plus(
                                KeyframesChainDeviceContract.KeyframesEntry(
                                    x = mcCoordinates[inst[2].toInt()].first,
                                    y = mcCoordinates[inst[2].toInt()].second,
                                    r = color.red,
                                    g = color.green,
                                    b = color.blue,
                                )
                            )
                        )
                    } else { // Normal lights
                        val x = inst[2].toInt()
                        val y = inst[1].toInt()

                        currentFrame = currentFrame.copy(
                            entries = currentFrame.entries.filter {
                                (it.x != x || it.y != y)
                            }.plus(
                                KeyframesChainDeviceContract.KeyframesEntry(
                                    x = x,
                                    y = y,
                                    r = color.red,
                                    g = color.green,
                                    b = color.blue,
                                )
                            )
                        )
                    }
                }

                if (inst[0] == "d") {
                    currentFrame = currentFrame.copy(
                        timing = Timing.Duration((inst[1].trim().toInt()).milliseconds)
                    )

                    frames.add(currentFrame)

                    currentFrame = KeyframesChainDeviceContract.Frame(
                        timing = Timing.Duration(100.milliseconds),
                        entries = currentFrame.entries
                    )
                }
            }
        }

        val renderedAnimation: List<Pair<Int, List<Signal>>>

        KeyframesChainDevice().apply {
            state.update {
                it.copy(
                    frames = frames
                )
            }

            renderAnimation()

            renderedAnimation = state.value.renderedAnimation
        }

        return KeyframesChainDeviceContract.KeyframesChainDeviceState(
            frames = frames,
            renderedAnimation = renderedAnimation
        )
    }

    fun createChain(zipFile: String, page: Int, entries: List<String>): StateChain {
        val groups = mutableListOf<Group>()

        entries.filter { // Single Animation
            it.split(" ").size == 4
        }.forEachIndexed { index, entry ->
            val x = entry.split(" ")[2].toInt()
            val y = entry.split(" ")[1].toInt()

            val keyLED = Zip.getInputStream(zipFile, entry)

            groups.add(
                Group(
                    name = "Single ${index + 1}",
                    stateChain = StateChain(
                        devices = listOf(
                            CoordinateFilterChainDeviceState(
                                filters = listOf(Pair(x, y))
                            ),
                            convertToKeyframes(keyLED)
                        )
                    )
                )
            )
        }

        entries.filter {
            it.split(" ").size == 5
        }.map {
            it.removePrefix("keyLED/").trim().split(" ").let { inst ->
                "${inst[0]} ${inst[1]} ${inst[2]} ${inst[3]}"
            }
        }.distinct().forEachIndexed { index, entry ->
            val multiEntries = entries.filter {
                it.removePrefix("keyLED/").startsWith(entry)
            }.sortedBy { it.split(" ")[4].trim() }

            val x = entry.split(" ")[2].toInt()
            val y = entry.split(" ")[1].toInt()

            groups.add(
                Group(
                    name = "Multi ${index + 1}",
                    stateChain = StateChain(
                        devices = listOf(
                            CoordinateFilterChainDeviceState(
                                filters = listOf(Pair(x, y))
                            ),
                            MultiGroupChainDeviceState(
                                groups = multiEntries.map {
                                    Group(
                                        name = it.split(" ")[4].trim(),
                                        stateChain = StateChain(
                                            devices = listOf(
                                                convertToKeyframes(Zip.getInputStream(zipFile, it))
                                            )
                                        )
                                    )
                                }
                            )
                        )
                    )
                )
            )
        }

        return StateChain(
            devices = listOf(
                MacroFilterChainDeviceState(
                    value = page
                ),
                GroupChainDeviceState(
                    groups = groups
                )
            )
        )
    }
}

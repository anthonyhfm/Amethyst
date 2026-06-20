package dev.anthonyhfm.amethyst.conversion.unipad.data

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.conversion.unipad.UnipadConverter
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Palettes
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
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
        val events: List<String> = data.decodeToString()
            .replace("\r\n", "\n").replace("\r", "\n")
            .trim().split("\n")

        val frames = mutableListOf<KeyframesChainDeviceContract.Frame>()
        var currentFrame: KeyframesChainDeviceContract.Frame = KeyframesChainDeviceContract.Frame(
            timing = Timing.Duration(100.milliseconds)
        )

        events.forEach { rawEvent ->
            val event = rawEvent.trim()
            if (event.isEmpty()) return@forEach
            val inst = event.split(" ")
            val cmd = inst[0].lowercase()

            // on / o  — turn LED on
            if (cmd == "on" || cmd == "o") {
                val isLogo = inst.getOrNull(1)?.lowercase().let { it == "l" || it == "logo" }
                if (!isLogo && inst.size < 4) {
                    println("KeyLED: skipping malformed 'on' line: $event")
                    return@forEach
                }

                val colorToken = if (isLogo) inst.getOrNull(2)?.trim()?.lowercase() else inst.getOrNull(3)?.trim()?.lowercase()

                val color: Color = when {
                    // auto + velocity  (5 tokens: on x y auto velocity / o l a velocity)
                    colorToken == "auto" || colorToken == "a" -> {
                        val velocityIdx = if (isLogo) 3 else 4
                        if (inst.size < velocityIdx + 1) {
                            println("KeyLED: 'auto' color requires velocity token: $event")
                            return@forEach
                        }
                        val velocity = inst[velocityIdx].trim().toIntOrNull() ?: run {
                            println("KeyLED: invalid velocity in: $event")
                            return@forEach
                        }
                        val (r, g, b) = Palettes.novation[velocity]
                        Color(r / 63f, g / 63f, b / 63f)
                    }
                    // 6-digit HEX  (4 tokens: on x y RRGGBB)
                    !isLogo && colorToken != null && colorToken.length == 6 && colorToken.all { it.isDigit() || it in 'a'..'f' } -> {
                        val r = colorToken.substring(0, 2).toInt(16) / 255f
                        val g = colorToken.substring(2, 4).toInt(16) / 255f
                        val b = colorToken.substring(4, 6).toInt(16) / 255f
                        Color(r, g, b)
                    }
                    else -> {
                        println("KeyLED: unrecognised color token '$colorToken' in: $event")
                        return@forEach
                    }
                }

                if (isLogo) {
                    val (cx, cy) = mcCoordinates[0]
                    currentFrame = currentFrame.copy(
                        entries = currentFrame.entries
                            .filter { it.x != cx || it.y != cy }
                            .plus(KeyframesChainDeviceContract.KeyframesEntry(x = cx, y = cy, r = color.red, g = color.green, b = color.blue))
                    )
                } else {
                    val isMc = inst.getOrNull(1)?.lowercase().let { it == "mc" || it == "*" }
                    if (isMc) {
                        val idx = inst[2].toIntOrNull() ?: return@forEach
                        if (idx < 0 || idx >= mcCoordinates.size) return@forEach
                        val (cx, cy) = mcCoordinates[idx]
                        currentFrame = currentFrame.copy(
                            entries = currentFrame.entries
                                .filter { it.x != cx || it.y != cy }
                                .plus(KeyframesChainDeviceContract.KeyframesEntry(x = cx, y = cy, r = color.red, g = color.green, b = color.blue))
                        )
                    } else {
                        // Docs: on x y color  → inst[1]=x, inst[2]=y
                        val rawX = inst[1].toIntOrNull() ?: return@forEach
                        val rawY = inst[2].toIntOrNull() ?: return@forEach
                        val x = rawY
                        val y = rawX
                        currentFrame = currentFrame.copy(
                            entries = currentFrame.entries
                                .filter { it.x != x || it.y != y }
                                .plus(KeyframesChainDeviceContract.KeyframesEntry(x = x, y = y, r = color.red, g = color.green, b = color.blue))
                        )
                    }
                }
            }

            // off / f  — turn LED off
            else if (cmd == "off" || cmd == "f") {
                val isLogo = inst.getOrNull(1)?.lowercase().let { it == "l" || it == "logo" }
                if (!isLogo && inst.size < 3) {
                    println("KeyLED: skipping malformed 'off' line: $event")
                    return@forEach
                }
                if (isLogo) {
                    val (cx, cy) = mcCoordinates[0]
                    currentFrame = currentFrame.copy(
                        entries = currentFrame.entries
                            .filter { it.x != cx || it.y != cy }
                            .plus(KeyframesChainDeviceContract.KeyframesEntry(x = cx, y = cy, r = 0f, g = 0f, b = 0f))
                    )
                } else {
                    val isMc = inst.getOrNull(1)?.lowercase().let { it == "mc" || it == "*" }
                    if (isMc) {
                        val idx = inst[2].toIntOrNull() ?: return@forEach
                        if (idx < 0 || idx >= mcCoordinates.size) return@forEach
                        val (cx, cy) = mcCoordinates[idx]
                        currentFrame = currentFrame.copy(
                            entries = currentFrame.entries
                                .filter { it.x != cx || it.y != cy }
                                .plus(KeyframesChainDeviceContract.KeyframesEntry(x = cx, y = cy, r = 0f, g = 0f, b = 0f))
                        )
                    } else {
                        val rawX = inst[1].toIntOrNull() ?: return@forEach
                        val rawY = inst[2].toIntOrNull() ?: return@forEach
                        val x = rawY
                        val y = rawX
                        currentFrame = currentFrame.copy(
                            entries = currentFrame.entries
                                .filter { it.x != x || it.y != y }
                                .plus(KeyframesChainDeviceContract.KeyframesEntry(x = x, y = y, r = 0f, g = 0f, b = 0f))
                        )
                    }
                }
            }

            // delay / d  — advance time and commit frame
            else if (cmd == "delay" || cmd == "d") {
                val ms = inst.getOrNull(1)?.trim()?.toLongOrNull() ?: return@forEach
                currentFrame = currentFrame.copy(timing = Timing.Duration(ms.milliseconds))
                frames.add(currentFrame)
                currentFrame = KeyframesChainDeviceContract.Frame(
                    timing = Timing.Duration(100.milliseconds),
                    entries = currentFrame.entries
                )
            }

            // chain / c  — chain switch inside LED sequence (not renderable as keyframe, log)
            else if (cmd == "chain" || cmd == "c") {
                val chainNum = inst.getOrNull(1)?.trim()?.toIntOrNull()
                println("KeyLED: 'chain $chainNum' inside LED sequence is not yet supported – skipping")
            }

            else {
                println("KeyLED: unrecognised command '$cmd' in: $event")
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
            renderedAnimation = renderedAnimation,
            useOwnershipTracking = true,
            ownershipId = UUID.randomUUID()
        )
    }

    fun createChain(page: Int, entries: List<String>): StateChain {
        val groups = mutableListOf<Group>()

        data class LedEntry(val path: String, val x: Int, val y: Int, val loop: Int)

        val parsed = entries.mapNotNull { path ->
            val parts = path.split(" ")
            val sub = parts.getOrNull(1)?.lowercase()

            if (sub == "l" || sub == "logo") {
                val coords = mcCoordinates[0]
                return@mapNotNull LedEntry(path, coords.first, coords.second, parts.getOrNull(2)?.toIntOrNull() ?: 1)
            }

            val rawX = sub?.toIntOrNull() ?: return@mapNotNull null
            val rawY = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            val x = rawY
            val y = rawX
            val loop = parts.getOrNull(3)?.toIntOrNull() ?: 1
            LedEntry(path = path, x = x, y = y, loop = loop)
        }

        parsed.groupBy { Pair(it.x, it.y) }.values.forEachIndexed { index, group ->
            val x = group.first().x
            val y = group.first().y

            if (group.size > 1) {
                val multiGroups = group.mapNotNull { ledEntry ->
                    val keyLED = UnipadConverter.entries[ledEntry.path]?.data ?: return@mapNotNull null

                    Group(
                        name = ledEntry.path,
                        stateChain = StateChain(
                            devices = listOf(convertToKeyframes(keyLED))
                        )
                    )
                }

                if (multiGroups.isNotEmpty()) {
                    groups.add(
                        Group(
                            name = "Multi ${index + 1}",
                            stateChain = StateChain(
                                devices = listOf(
                                    CoordinateFilterChainDeviceState(filters = listOf(Pair(x, y))),

                                    MultiGroupChainDeviceState(groups = multiGroups)
                                )
                            )
                        )
                    )
                }
            } else {
                val ledEntry = group.first()
                val keyLED = UnipadConverter.entries[ledEntry.path]?.data ?: return@forEachIndexed
                println("Decoding KeyLED: ${ledEntry.path}")

                groups.add(
                    Group(
                        name = "Single ${index + 1}",
                        stateChain = StateChain(
                            devices = listOf(
                                CoordinateFilterChainDeviceState(filters = listOf(Pair(x, y))),

                                convertToKeyframes(keyLED)
                            )
                        )
                    )
                )
            }
        }

        return StateChain(
            devices = listOf(
                MacroFilterChainDeviceState(
                    allowedValues = setOf(page)
                ),
                GroupChainDeviceState(
                    groups = groups
                )
            )
        )
    }
}

package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils.rythmIndexToDuration
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDevice
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MaxParam
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.copy.CopyChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * # Resonator 4 by Kaskobi
 */
class Resonator4Adapter(
    val blob: String,
    val device: MxDevice
): AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val palette = AbletonConverter.palette

        val direction: Resonator3Prototype = jsonDecoder.decodeFromString<Resonator4Data>(blob)
        val data: Resonator4Data = jsonDecoder.decodeFromString(blob)

        val parameters = MaxParam(device.parameterList.parameterList.parameters)

        // Enum 0 = Time (Ms mode), 1 = Sync (Rythm mode)
        val noteLengthIsMs: Boolean = parameters.getEnumValue(11) == 0
        val noteLengthValueMs: Double = data.timeMode1ms4.firstOrNull() ?: 100.0
        val noteLengthValueSync: Int = data.timeMode1sync.firstOrNull() ?: 1

        val stepDelayIsMs: Boolean = parameters.getEnumValue(35) == 0
        val stepDelayValueMs: Double = parameters.getFloatValue(12).toDouble()
        val stepDelayValueSync: Int = parameters.getEnumValue(13)

        val timeBetweenColorsIsMs: Boolean = parameters.getEnumValue(34) == 0
        val timeBetweenColorsMs: Double = parameters.getFloatValue(14).toDouble()
        val timeBetweenColorsSync: Int = parameters.getEnumValue(15)

        val steps: Int = (parameters.getIntValue(10) - 1).coerceAtLeast(1)

        val isolation: CopyChainDeviceState.IsolationType = parameters.getEnumValue(5)
            .let {
                when (it) {
                    1 -> CopyChainDeviceState.IsolationType.FULL
                    else -> CopyChainDeviceState.IsolationType.EDGELESS
                }
            }
        
        val holdMode: Boolean = parameters.getEnumValue(3) == 1

        val gradientEnabled: Boolean = parameters.getEnumValue(1) == 1

        val colorCount = parameters.getIntValue(2).coerceIn(1, 16) - 1

        val gradientColors: List<Int> = run {
            val ids = listOf(29, 22, 28, 27, 26, 21, 25, 24, 23, 20, 19, 18, 33, 32, 31, 30)

            return@run ids.map { id ->
                parameters.getIntValue(id)
            }
        }

        val stepDelayValue = if (stepDelayIsMs) {
            stepDelayValueMs.roundToLong()
        } else {
            val timingString = rateIndexToTiming(stepDelayValueSync) ?: "1/8"
            rythmIndexToDuration(timingString, AbletonConverter.bpm, 1).inWholeMilliseconds
        }

        val noteLengthValue = if (noteLengthIsMs) {
            noteLengthValueMs.roundToLong()
        } else {
            val timingString = rateIndexToTiming(noteLengthValueSync) ?: "1/8"
            rythmIndexToDuration(timingString, AbletonConverter.bpm, 1).inWholeMilliseconds
        }

        val timeBetweenColorsValue = if (timeBetweenColorsIsMs) {
            timeBetweenColorsMs.roundToLong()
        } else {
            val timingString = rateIndexToTiming(timeBetweenColorsSync) ?: "1/8"
            rythmIndexToDuration(timingString, AbletonConverter.bpm, 1).inWholeMilliseconds
        }

        return listOfNotNull(
            GroupChainDeviceState(
                groups = mutableListOf<Group>().apply {
                    if (direction.upLeft.contains(1)) {
                        add(
                            Group(
                                name = "Left, Up",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            mode = CopyChainDeviceState.CopyMode.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            offsets = listOf(CopyChainDeviceState.Offset(x = -steps, y = steps))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.up.contains(1)) {
                        add(
                            Group(
                                name = "Up",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            mode = CopyChainDeviceState.CopyMode.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            offsets = listOf(CopyChainDeviceState.Offset(x = 0, y = steps))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.upRight.contains(1)) {
                        add(
                            Group(
                                name = "Right, Up",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            mode = CopyChainDeviceState.CopyMode.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            offsets = listOf(CopyChainDeviceState.Offset(x = steps, y = steps))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.left.contains(1)) {
                        add(
                            Group(
                                name = "Left",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            mode = CopyChainDeviceState.CopyMode.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            offsets = listOf(CopyChainDeviceState.Offset(x = -steps, y = 0))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.right.contains(1)) {
                        add(
                            Group(
                                name = "Right",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            mode = CopyChainDeviceState.CopyMode.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            offsets = listOf(CopyChainDeviceState.Offset(x = steps, y = 0))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.downLeft.contains(1)) {
                        add(
                            Group(
                                name = "Left, Down",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            mode = CopyChainDeviceState.CopyMode.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            offsets = listOf(CopyChainDeviceState.Offset(x = -steps, y = -steps))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.down.contains(1)) {
                        add(
                            Group(
                                name = "Down",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            mode = CopyChainDeviceState.CopyMode.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            offsets = listOf(CopyChainDeviceState.Offset(x = 0, y = -steps))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.downRight.contains(1)) {
                        add(
                            Group(
                                name = "Right, Down",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            mode = CopyChainDeviceState.CopyMode.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            offsets = listOf(CopyChainDeviceState.Offset(x = steps, y = -steps))
                                        )
                                    )
                                )
                            )
                        )
                    }
                }
            ),
            HoldChainDeviceState(
                timing = Timing.Duration(noteLengthValue.milliseconds),
                delayMs = noteLengthValue,
                gate = 0.5f
            ),
            if (gradientEnabled) {
                if (colorCount > 1) {
                    GradientChainDeviceState(
                        gradientData = mutableListOf<GradientChainDeviceState.GradientColor>().apply {
                            for (i in 0 until colorCount) {
                                add(
                                    GradientChainDeviceState.GradientColor(
                                        r = palette[gradientColors[i]].first / 63f,
                                        g = palette[gradientColors[i]].second / 63f,
                                        b = palette[gradientColors[i]].third / 63f,
                                        position = i.toFloat() / (colorCount - 1).coerceAtLeast(1)
                                    )
                                )
                            }
                        },
                        timing = Timing.Duration(timeBetweenColorsValue.milliseconds * colorCount),
                        durationMs = (timeBetweenColorsValue.toDouble() * colorCount)
                    )
                } else {
                    ColorChainDeviceState(
                        r = palette[gradientColors[0]].first / 63f,
                        g = palette[gradientColors[0]].second / 63f,
                        b = palette[gradientColors[0]].third / 63f,
                    )
                }
            } else null
        )
    }

    private fun rateIndexToTiming(index: Int): String? {
        return when (index) {
            0 -> "1/128"
            1 -> "1/64"
            2 -> "1/32"
            3 -> "1/16"
            4 -> "1/8"
            5 -> "1/4"
            6 -> "1/2"
            7 -> "1/1"
            else -> "1/8"
        }
    }
}

@Serializable
private data class Resonator4Data(
    @SerialName("Circle")
    override val circle: List<Int> = listOf(0),
    @SerialName("Device")
    override val device: List<Float> = listOf(0f),
    @SerialName("Diamond")
    override val diamond: List<Int> = listOf(0),
    @SerialName("Down")
    override val down: List<Int> = listOf(0),
    @SerialName("DownLeft")
    override val downLeft: List<Int> = listOf(0),
    @SerialName("DownRight")
    override val downRight: List<Int> = listOf(0),
    @SerialName("Launchpad Position")
    override val launchpadPosition: List<Float> = listOf(0f),
    @SerialName("Left")
    override val left: List<Int> = listOf(0),
    @SerialName("Right")
    override val right: List<Int> = listOf(0),
    @SerialName("SingleLED")
    override val singleLED: List<Int> = listOf(0),
    @SerialName("Square")
    override val square: List<Int> = listOf(0),
    @SerialName("Up")
    override val up: List<Int> = listOf(0),
    @SerialName("UpLeft")
    override val upLeft: List<Int> = listOf(0),
    @SerialName("UpRight")
    override val upRight: List<Int> = listOf(0),
    @SerialName("TimeMode1ms4")
    val timeMode1ms4: List<Double> = listOf(100.0),
    @SerialName("TimeMode1sync")
    val timeMode1sync: List<Int> = listOf(1),
) : Resonator3Prototype

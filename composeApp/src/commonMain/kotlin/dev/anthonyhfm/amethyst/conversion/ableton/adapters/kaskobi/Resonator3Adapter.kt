package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.outbreak.utils.rythmIndexToDuration
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
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
 * # Resonator 3 by Kaskobi
 *
 * @param isUpdatedVersion Whether or not the blob is from Resonator 3.0.1 or 3.0.0
 */
class Resonator3Adapter(
    val isUpdatedVersion: Boolean,
    val blob: String,
    val device: MxDeviceMidiEffect
): AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val palette = AbletonConverter.palette

        val direction: Resonator3Prototype = if (isUpdatedVersion) {
            jsonDecoder.decodeFromString<Resonator301Data>(blob)
        } else {
            jsonDecoder.decodeFromString<Resonator3Data>(blob)
        }
        
        val parameters = MaxParam(device.parameterList.parameterList.parameters)

        val noteLengthMode: Boolean = parameters.getEnumValue(if (isUpdatedVersion) 9 else 26) == 1
        val noteLengthValueMs: Double = convertWeirdFuckingFloatValues(parameters.getFloatValue(if (isUpdatedVersion) 10 else 21).toDouble())
        val noteLengthValueSync: Int = parameters.getEnumValue(if (isUpdatedVersion) 11 else 20)

        val stepDelayValueMode: Boolean = parameters.getEnumValue(if (isUpdatedVersion) 34 else 28) == 1
        val stepDelayValueMs: Double = convertWeirdFuckingFloatValues(parameters.getFloatValue(if (isUpdatedVersion) 14 else 19).toDouble())
        val stepDelayValueSync: Int = parameters.getEnumValue(if (isUpdatedVersion) 15 else 18)

        val timeBetweenColorsMode: Boolean = parameters.getEnumValue(if (isUpdatedVersion) 35 else 30) == 1
        val timeBetweenColorsMs: Double = convertWeirdFuckingFloatValues(parameters.getFloatValue(if (isUpdatedVersion) 12 else 23).toDouble())
        val timeBetweenColorsSync: Int = parameters.getEnumValue(if (isUpdatedVersion) 13 else 22)

        val steps: Int = parameters.getIntValue(if (isUpdatedVersion) 8 else 0) - 1

        val isolation: CopyChainDeviceState.IsolationType = parameters.getEnumValue(if (isUpdatedVersion) 4 else 29)
            .let {
                when (it) {
                    1 -> CopyChainDeviceState.IsolationType.FULL
                    else -> CopyChainDeviceState.IsolationType.EDGELESS
                }
            }

        val holdMode: Boolean = parameters.getEnumValue(if (isUpdatedVersion) 3 else 27) == 1

        val gradientEnabled: Boolean = parameters.getEnumValue(if (isUpdatedVersion) 1 else 24) == 1

        val colorCount = parameters.getIntValue(if (isUpdatedVersion) 2 else 16).coerceIn(1, 16) - 1

        val gradientColors: List<Int> = run {
            val ids = if (isUpdatedVersion) {
                listOf(18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33)
            } else {
                listOf(12, 5, 11, 10, 9, 4, 8, 7, 6, 3, 2, 1, 16, 15, 14, 13)
            }

            return@run ids.map { id ->
                parameters.getIntValue(id)
            }
        }

        val stepDelayValue = if (stepDelayValueMode) {
            stepDelayValueMs.roundToLong()
        } else {
            val timingString = rateIndexToTiming(stepDelayValueSync) ?: "1/8"
            rythmIndexToDuration(timingString, AbletonConverter.bpm, 1).inWholeMilliseconds
        }

        val noteLengthValue = if (noteLengthMode) {
            noteLengthValueMs.roundToLong()
        } else {
            val timingString = rateIndexToTiming(noteLengthValueSync) ?: "1/8"
            rythmIndexToDuration(timingString, AbletonConverter.bpm, 1).inWholeMilliseconds
        }

        val timeBetweenColorsValue = if (timeBetweenColorsMode) {
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
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue,
                                            offsets = listOf(Pair(-steps, steps))
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
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue,
                                            offsets = listOf(Pair(0, steps))
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
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue,
                                            offsets = listOf(Pair(steps, steps))
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
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue,
                                            offsets = listOf(Pair(-steps, 0))
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
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue,
                                            offsets = listOf(Pair(steps, 0))
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
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue,
                                            offsets = listOf(Pair(-steps, -steps))
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
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue,
                                            offsets = listOf(Pair(0, -steps))
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
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue,
                                            offsets = listOf(Pair(steps, -steps))
                                        )
                                    )
                                )
                            )
                        )
                    }
                }
            ),
            if (holdMode) {
                HoldChainDeviceState(
                    timing = Timing.Duration(noteLengthValue.milliseconds),
                    delayMs = noteLengthValue,
                )
            } else null,
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

    private val RAW_REF_100 = 31320.877
    private val LN2 = kotlin.math.ln(2.0)
    private val B = 200.0 / LN2
    private val A = RAW_REF_100 - B * kotlin.math.ln(100.0)

    private val IDENTITY_ANCHORS = doubleArrayOf(10.0, 60000.0)
    private val EPS = 1e-6

    /**
     * Here we go again.
     *
     * I am not proud of this.
     *
     * May god have mercy on our souls.
     *
     * @see Resonator2Adapter
     */
    private fun convertWeirdFuckingFloatValues(raw: Double): Double {
        if (IDENTITY_ANCHORS.any { kotlin.math.abs(raw - it) <= EPS }) return raw
        return kotlin.math.exp((raw - A) / B)
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
            else -> "1/8" // Default to 1/8 if unknown
        }
    }
}

interface Resonator3Prototype {
    val circle: List<Int>
    val device: List<Float>
    val diamond: List<Int>
    val down: List<Int>
    val downLeft: List<Int>
    val downRight: List<Int>
    val launchpadPosition: List<Float>
    val left: List<Int>
    val right: List<Int>
    val singleLED: List<Int>
    val square: List<Int>
    val up: List<Int>
    val upLeft: List<Int>
    val upRight: List<Int>
}

@Serializable
private data class Resonator3Data(
    @SerialName("pictctrl[13]")
    override val circle: List<Int> = listOf(0),
    @SerialName("Device")
    override val device: List<Float> = listOf(0f),
    @SerialName("pictctrl[15]")
    override val diamond: List<Int> = listOf(0),
    @SerialName("pictctrl[10]")
    override val down: List<Int> = listOf(0),
    @SerialName("pictctrl[6]")
    override val downLeft: List<Int> = listOf(0),
    @SerialName("pictctrl[4]")
    override val downRight: List<Int> = listOf(0),
    @SerialName("Launchpad Position")
    override val launchpadPosition: List<Float> = listOf(0f),
    @SerialName("pictctrl[8]")
    override val left: List<Int> = listOf(0),
    @SerialName("pictctrl[9]")
    override val right: List<Int> = listOf(0),
    @SerialName("SingleLED")
    override val singleLED: List<Int> = listOf(0),
    @SerialName("pictctrl[12]")
    override val square: List<Int> = listOf(0),
    @SerialName("pictctrl[11]")
    override val up: List<Int> = listOf(0),
    @SerialName("pictctrl[3]")
    override val upLeft: List<Int> = listOf(0),
    @SerialName("pictctrl[5]")
    override val upRight: List<Int> = listOf(0),
) : Resonator3Prototype

@Serializable
private data class Resonator301Data(
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
) : Resonator3Prototype
package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement
import dev.anthonyhfm.amethyst.core.util.Palettes
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
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Adapter for Kaskobi Resonator 2 device from Ableton Live
 *
 * This thing is super experimental, because the plugin itself is super weird.
 * However it shouldnt be too broken when playing a project.
 */
class Resonator2Adapter(
    val blob: ByteArray,
    val xml: XmlElement
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val palette = AbletonConverter.palette
        
        val parameterList = xml.querySelector("ParameterList")[1]
        val direction = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<ResonatorBlob>(blob.decodeToString())

        val noteLengthValue: Double = (parameterList
            .querySelector("MxDFloatParameter").find {
                it.attributes["Id"] == "20"
            }!!.querySelector("Manual").first()
            .attributes["Value"]?.toFloatOrNull() ?: 0f)
            .let {
                convertWeirdFuckingFloatValues(it.toDouble())
            }

        val stepDelayValue: Double = (parameterList
            .querySelector("MxDFloatParameter").find {
                it.attributes["Id"] == "19"
            }!!.querySelector("Manual").first()
            .attributes["Value"]?.toFloatOrNull() ?: 0f)
            .let {
                convertWeirdFuckingFloatValues(it.toDouble())
            }

        val timeBetweenColors: Double = (parameterList
            .querySelector("MxDFloatParameter").find {
                it.attributes["Id"] == "21"
            }!!.querySelector("Manual").first()
            .attributes["Value"]?.toFloatOrNull() ?: 0f)
            .let {
                convertWeirdFuckingFloatValues(it.toDouble())
            }

        val steps: Int = (parameterList
            .querySelector("MxDIntParameter").find {
                it.attributes["Id"] == "1"
            }!!.querySelector("Manual").first()
            .attributes["Value"]?.toIntOrNull()?.minus(1) ?: 0)

        val isolation: CopyChainDeviceState.IsolationType = (parameterList
            .querySelector("MxDEnumParameter").find {
                it.attributes["Id"] == "15"
            }!!.querySelector("Manual").first()
            .attributes["Value"]?.toIntOrNull() ?: 0f)
            .let {
                when (it) {
                    1 -> CopyChainDeviceState.IsolationType.FULL
                    else -> CopyChainDeviceState.IsolationType.EDGELESS
                }
            }

        val gradientEnabled: Boolean = (parameterList
            .querySelector("MxDEnumParameter").find {
                it.attributes["Id"] == "18"
            }!!.querySelector("Manual").first()
            .attributes["Value"]?.toIntOrNull() ?: 0f)
            .let { it == 1 }

        val colorCount = (parameterList
            .querySelector("MxDIntParameter").find {
                it.attributes["Id"] == "13"
            }!!.querySelector("Manual").first()
            .attributes["Value"]?.toIntOrNull() ?: 0)

        val gradientColors: List<Int> = run {
            val ids = listOf("14", "3", "4", "5", "2", "8", "7", "6", "12", "11", "10", "9")

            return@run ids.map { id ->
                (parameterList
                    .querySelector("MxDIntParameter").find {
                        it.attributes["Id"] == id
                    }!!.querySelector("Manual").first()
                    .attributes["Value"]?.toIntOrNull() ?: 0)
            }
        }

        return listOfNotNull(
            GroupChainDeviceState(
                groups = mutableListOf<Group>().apply {
                    if (direction.diagonalLeftUp.contains(1)) {
                        add(
                            Group(
                                name = "Left, Up",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue.roundToInt(),
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
                                            delayMs = stepDelayValue.roundToInt(),
                                            offsets = listOf(Pair(0, steps))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.diagonalRightUp.contains(1)) {
                        add(
                            Group(
                                name = "Right, Up",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue.roundToInt(),
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
                                            delayMs = stepDelayValue.roundToInt(),
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
                                            delayMs = stepDelayValue.roundToInt(),
                                            offsets = listOf(Pair(steps, 0))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.diagonalLeftDown.contains(1)) {
                        add(
                            Group(
                                name = "Left, Down",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue.roundToInt(),
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
                                            delayMs = stepDelayValue.roundToInt(),
                                            offsets = listOf(Pair(0, -steps))
                                        )
                                    )
                                )
                            )
                        )
                    }

                    if (direction.diagonalRightDown.contains(1)) {
                        add(
                            Group(
                                name = "Right, Down",
                                stateChain = StateChain(
                                    devices = listOf(
                                        CopyChainDeviceState(
                                            isolate = isolation,
                                            type = CopyChainDeviceState.CopyType.INTERPOLATE,
                                            timing = Timing.Duration(stepDelayValue.milliseconds),
                                            delayMs = stepDelayValue.roundToInt(),
                                            offsets = listOf(Pair(steps, -steps))
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
                delayMs = noteLengthValue.roundToInt(),
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
                        timing = Timing.Duration(timeBetweenColors.milliseconds * colorCount),
                        durationMs = (timeBetweenColors * colorCount)
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

    @Serializable
    data class ResonatorBlob(
        @SerialName("pictctrl[3]")
        val diagonalLeftUp: List<Int>,

        @SerialName("pictctrl[11]")
        val up: List<Int>,

        @SerialName("pictctrl[5]")
        val diagonalRightUp: List<Int>,

        @SerialName("pictctrl[8]")
        val left: List<Int>,

        @SerialName("pictctrl[9]")
        val right: List<Int>,

        @SerialName("pictctrl[6]")
        val diagonalLeftDown: List<Int>,

        @SerialName("pictctrl[10]")
        val down: List<Int>,

        @SerialName("pictctrl[4]")
        val diagonalRightDown: List<Int>,
    )

    private val RAW_REF_100 = 719.005432
    private val LN2 = kotlin.math.ln(2.0)
    private val B = 200.0 / LN2
    private val A = RAW_REF_100 - B * kotlin.math.ln(100.0)

    private val IDENTITY_ANCHORS = doubleArrayOf(10.0, 2000.0)
    private val EPS = 1e-6

    /**
     * To anybody reading this code in the future, I'm very sorry.
     * I have no idea why Kaskobi did this, but the float values in Ableton
     * are stored in a very weird way. They are not linear at all.
     *
     * This function is trying to GUESS the correct value based on some
     * anchor points I found by testing the plugin in Ableton.
     *
     * For questions, do not contact me, I have no idea either.
     */
    private fun convertWeirdFuckingFloatValues(raw: Double): Double {
        if (IDENTITY_ANCHORS.any { kotlin.math.abs(raw - it) <= EPS }) return raw
        return kotlin.math.exp((raw - A) / B)
    }
}
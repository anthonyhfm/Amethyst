package dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.copy.CopyChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.hold.HoldChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

class Resonator1Adapter(
    private val blob: String,
) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val data = jsonDecoder.decodeFromString<ResonatorData>(blob)

        return listOf(
            GroupChainDeviceState(
                groups = mutableListOf<Group>().apply {
                    val isolation = CopyChainDeviceState.IsolationType.EDGELESS
                    val stepDelayValue = data.stepDelay.first()
                    val steps = 10

                    if (data.upLeft.contains(1)) {
                        add(
                            Group(
                                name = "Left, Up",
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

                    if (data.upArrow.contains(1)) {
                        add(
                            Group(
                                name = "Up",
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

                    if (data.upRight.contains(1)) {
                        add(
                            Group(
                                name = "Right, Up",
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

                    if (data.leftArrow.contains(1)) {
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

                    if (data.rightArrow.contains(1)) {
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

                    if (data.downLeft.contains(1)) {
                        add(
                            Group(
                                name = "Left, Down",
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

                    if (data.downArrow.contains(1)) {
                        add(
                            Group(
                                name = "Down",
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

                    if (data.downRight.contains(1)) {
                        add(
                            Group(
                                name = "Right, Down",
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
                }
            ),
            HoldChainDeviceState(
                timing = Timing.Duration(data.noteLength.first().toLong().milliseconds),
                delayMs = data.noteLength.first().toLong(),
                gate = 0.5f
            )
        )

        return emptyList()
    }
    @Serializable
    data class ResonatorData(
        @SerialName("pictctrl[15]")
        val roundExplosion: List<Int> = listOf(0),
        @SerialName("pictctrl[12]")
        val squareExplosion: List<Int> = listOf(0),
        @SerialName("pictctrl[8]")
        val leftArrow: List<Int> = listOf(0),
        @SerialName("pictctrl[9]")
        val rightArrow: List<Int> = listOf(0),
        @SerialName("pictctrl[10]")
        val downArrow: List<Int> = listOf(0),
        @SerialName("pictctrl[11]")
        val upArrow: List<Int> = listOf(0),
        @SerialName("pictctrl[3]")
        val upLeft: List<Int> = listOf(0),
        @SerialName("pictctrl[4]")
        val downRight: List<Int> = listOf(0),
        @SerialName("pictctrl[5]")
        val upRight: List<Int> = listOf(0),
        @SerialName("pictctrl[6]")
        val downLeft: List<Int> = listOf(0),
        @SerialName("live.dial[1]")
        val stepDelay: List<Double> = listOf(0.0),
        @SerialName("live.dial")
        val noteLength: List<Double> = listOf(0.0),
    )
}
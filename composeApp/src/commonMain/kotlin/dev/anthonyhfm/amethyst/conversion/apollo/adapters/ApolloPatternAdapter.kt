package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.conversion.apollo.utils.toTiming
import dev.anthonyhfm.amethyst.conversion.apollo.utils.apolloPadCoordinates
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract

class ApolloPatternAdapter(
    model: ApolloModel.Device.Pattern
) : ApolloAdapter<ApolloModel.Device.Pattern>(model) {
    override fun toDeviceState(): DeviceState {
        val frames = model.frames.map { apolloFrame ->
            val entries = mutableListOf<KeyframesChainDeviceContract.KeyframesEntry>()
            apolloFrame.colors.forEachIndexed { index, color ->
                if (color.r != 0.toByte() || color.g != 0.toByte() || color.b != 0.toByte()) {
                    val coordinates = apolloPadCoordinates(index = index) ?: return@forEachIndexed
                    val (x, y) = coordinates
                    entries.add(
                        KeyframesChainDeviceContract.KeyframesEntry(
                            x = x,
                            y = y,
                            r = color.r.toInt() / 63f,
                            g = color.g.toInt() / 63f,
                            b = color.b.toInt() / 63f,
                            apolloIndex = index,
                        )
                    )
                }
            }

            KeyframesChainDeviceContract.Frame(
                timing = apolloFrame.time.toTiming(),
                gate = model.gate.toFloat() / 2f,
                entries = entries
            )
        }

        val playbackMode = when (model.playbackMode) {
            0 -> KeyframesChainDeviceContract.PlaybackMode.Mono
            1 -> KeyframesChainDeviceContract.PlaybackMode.Poly
            2 -> KeyframesChainDeviceContract.PlaybackMode.Loop
            else -> KeyframesChainDeviceContract.PlaybackMode.Mono
        }

        return KeyframesChainDeviceContract.KeyframesChainDeviceState(
            frames = frames.ifEmpty {
                listOf(
                    KeyframesChainDeviceContract.Frame(
                        timing = Timing.Rythm(timing = Timing.Rythm.RythmTiming._1_4),
                        gate = model.gate.toFloat() / 2f,
                    )
                )
            },
            repeats = model.repeats,
            playbackMode = playbackMode,
            rootKey = model.rootKey?.let { root ->
                val (x, y) = apolloPadCoordinates(index = root) ?: return@let null
                x + y * 10
            },
            terminalGate = model.gate.toFloat() / 2f,
            apolloPattern = true,
            wrap = model.wrap,
            infinity = model.infinite,
            pinch = model.pinch.toFloat(),
            bilateralPinch = model.bilateral
        )
    }
}

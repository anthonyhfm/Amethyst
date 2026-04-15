package dev.anthonyhfm.amethyst.conversion.apollo.adapters

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloAdapter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.conversion.apollo.utils.toTiming
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
                    val x = index % 10
                    val y = index / 10
                    entries.add(
                        KeyframesChainDeviceContract.KeyframesEntry(
                            x = x,
                            y = y,
                            r = (color.r.toInt() and 0xFF) / 255f,
                            g = (color.g.toInt() and 0xFF) / 255f,
                            b = (color.b.toInt() and 0xFF) / 255f
                        )
                    )
                }
            }

            KeyframesChainDeviceContract.Frame(
                timing = apolloFrame.time.toTiming(),
                gate = 0.5f, // 100% gate in Amethyst = 0.5f (Amethyst stores gate as 0..1 where 0.5=100%)
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
                listOf(KeyframesChainDeviceContract.Frame(timing = model.frames.firstOrNull()?.time?.toTiming() ?: dev.anthonyhfm.amethyst.core.util.Timing.Rythm(dev.anthonyhfm.amethyst.core.util.Timing.Rythm.RythmTiming._1_4)))
            },
            repeats = model.repeats,
            playbackMode = playbackMode,
            rootKey = model.rootKey,
            wrap = model.wrap,
            infinity = model.infinite,
            pinch = model.pinch.toFloat(),
            bilateralPinch = model.bilateral
        )
    }
}

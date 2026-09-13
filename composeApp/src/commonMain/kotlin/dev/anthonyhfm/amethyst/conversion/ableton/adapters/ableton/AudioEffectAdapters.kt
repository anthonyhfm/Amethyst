package dev.anthonyhfm.amethyst.conversion.ableton.adapters.ableton

import dev.anthonyhfm.amethyst.conversion.ableton.adapters.AbletonAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Compressor2
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Eq8
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Eq8BandParameter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.Limiter
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.StereoGain
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.DuckerChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.EqEightBandState
import dev.anthonyhfm.amethyst.devices.audio.effects.EqEightChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.LimiterChainDeviceState
import dev.anthonyhfm.amethyst.devices.audio.effects.StereoGainChainDeviceState
import kotlin.math.log10

class Eq8Adapter(private val device: Eq8) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> = listOf(
        EqEightChainDeviceState(
            bandsA = device.bands.map { it.parameterA.toState() },
            bandsB = device.bands.map { it.parameterB.toState() },
            channelMode = device.mode.value.coerceIn(0, 2),
            globalGainDb = device.globalGain.manual.value,
            scale = device.scale.manual.value,
        )
    ).withMuteState(device.on.manual.value)

    private fun Eq8BandParameter.toState() = EqEightBandState(
        enabled = isOn.manual.value,
        mode = mode.manual.value,
        frequencyHz = freq.manual.value,
        gainDb = gain.manual.value,
        q = q.manual.value,
    )
}

class StereoGainAdapter(private val device: StereoGain) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        val state = StereoGainChainDeviceState(
            gainDb = linearToDb(device.gain.manual.value),
            width = device.stereoWidth.manual.value,
            balance = device.balance.manual.value,
            phaseInvertLeft = device.phaseInvertL.manual.value,
            phaseInvertRight = device.phaseInvertR.manual.value,
            mono = device.mono.manual.value,
            muted = device.mute.manual.value,
        )
        return listOf(state).withMuteState(device.on.manual.value)
    }
}

class LimiterAdapter(private val device: Limiter) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> = listOf(
        LimiterChainDeviceState(
            inputGainDb = device.gain.manual.value,
            ceilingDb = device.ceiling.manual.value.coerceAtMost(0f),
            releaseMs = device.release.manual.value,
            lookaheadMs = when (device.lookahead.manual.value) {
                0 -> 1.5f
                1 -> 3f
                2 -> 6f
                else -> 3f
            },
        )
    ).withMuteState(device.on.manual.value)
}

class Compressor2Adapter(private val device: Compressor2) : AbletonAdapter() {
    override fun toDeviceStates(): List<DeviceState> {
        if (!device.on.manual.value || !device.sideChain.onOff.manual.value) return emptyList()
        val trackId = TRACK_POST_FX.matchEntire(device.sideChain.routedInput.routable.target.value)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()
        return listOf(
            DuckerChainDeviceState(
                attackMs = device.attack.manual.value.coerceAtLeast(0f),
                releaseMs = device.release.manual.value.coerceAtLeast(0f),
                strength = 1f,
                sidechainBusId = "ableton-track-$trackId-postfx",
                thresholdDb = linearToDb(device.threshold.manual.value),
                ratio = device.ratio.manual.value.coerceAtLeast(1f),
                kneeDb = device.knee.manual.value.coerceAtLeast(0f),
                detectorGainDb = linearToDb(device.sideChain.routedInput.volume.manual.value),
                makeupDb = device.gain.manual.value,
                dryWet = device.dryWet.manual.value.coerceIn(0f, 1f),
                lookaheadMs = when (device.lookahead.manual.value) {
                    0 -> 0f
                    1 -> 1f
                    2 -> 10f
                    else -> 0f
                },
            )
        )
    }

    companion object {
        private val TRACK_POST_FX = Regex("^AudioIn/Track\\.(\\d+)/PostFxOut$")
    }
}

private fun linearToDb(value: Float): Float =
    if (!value.isFinite() || value <= 0f) -120f else (20.0 * log10(value.toDouble())).toFloat()

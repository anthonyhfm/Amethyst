package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class Eq8(
    @SerialName("Id")
    val id: Int = 0,
    @XmlElement
    val on: AbletonOn = AbletonOn(),
    @XmlElement
    @XmlSerialName("Mode")
    val mode: AbletonValue<Int> = AbletonValue(0),
    @XmlElement
    @XmlSerialName("GlobalGain")
    val globalGain: AbletonFloatParameter = AbletonFloatParameter(),
    @XmlElement
    @XmlSerialName("Scale")
    val scale: AbletonFloatParameter = AbletonFloatParameter(1f),
    @XmlElement
    @XmlSerialName("Bands.0")
    val band0: Eq8Band = Eq8Band(),
    @XmlElement
    @XmlSerialName("Bands.1")
    val band1: Eq8Band = Eq8Band(),
    @XmlElement
    @XmlSerialName("Bands.2")
    val band2: Eq8Band = Eq8Band(),
    @XmlElement
    @XmlSerialName("Bands.3")
    val band3: Eq8Band = Eq8Band(),
    @XmlElement
    @XmlSerialName("Bands.4")
    val band4: Eq8Band = Eq8Band(),
    @XmlElement
    @XmlSerialName("Bands.5")
    val band5: Eq8Band = Eq8Band(),
    @XmlElement
    @XmlSerialName("Bands.6")
    val band6: Eq8Band = Eq8Band(),
    @XmlElement
    @XmlSerialName("Bands.7")
    val band7: Eq8Band = Eq8Band(),
) : AbletonDevice {
    val bands: List<Eq8Band> get() = listOf(band0, band1, band2, band3, band4, band5, band6, band7)
}

@Serializable
data class Eq8Band(
    @XmlElement
    @XmlSerialName("ParameterA")
    val parameterA: Eq8BandParameter = Eq8BandParameter(),
    @XmlElement
    @XmlSerialName("ParameterB")
    val parameterB: Eq8BandParameter = Eq8BandParameter(),
)

@Serializable
data class Eq8BandParameter(
    @XmlElement
    @XmlSerialName("IsOn")
    val isOn: AbletonOn = AbletonOn(manual = AbletonManual(false)),
    @XmlElement
    @XmlSerialName("Mode")
    val mode: AbletonIntParameter = AbletonIntParameter(3),
    @XmlElement
    @XmlSerialName("Freq")
    val freq: AbletonFloatParameter = AbletonFloatParameter(1_000f),
    @XmlElement
    @XmlSerialName("Gain")
    val gain: AbletonFloatParameter = AbletonFloatParameter(),
    @XmlElement
    @XmlSerialName("Q")
    val q: AbletonFloatParameter = AbletonFloatParameter(0.70710677f),
)

@Serializable
data class StereoGain(
    @SerialName("Id")
    val id: Int = 0,
    @XmlElement
    val on: AbletonOn = AbletonOn(),
    @XmlElement
    @XmlSerialName("PhaseInvertL")
    val phaseInvertL: AbletonOn = AbletonOn(manual = AbletonManual(false)),
    @XmlElement
    @XmlSerialName("PhaseInvertR")
    val phaseInvertR: AbletonOn = AbletonOn(manual = AbletonManual(false)),
    @XmlElement
    @XmlSerialName("StereoWidth")
    val stereoWidth: AbletonFloatParameter = AbletonFloatParameter(1f),
    @XmlElement
    @XmlSerialName("Mono")
    val mono: AbletonOn = AbletonOn(manual = AbletonManual(false)),
    @XmlElement
    @XmlSerialName("Balance")
    val balance: AbletonFloatParameter = AbletonFloatParameter(),
    @XmlElement
    @XmlSerialName("Gain")
    val gain: AbletonFloatParameter = AbletonFloatParameter(1f),
    @XmlElement
    @XmlSerialName("Mute")
    val mute: AbletonOn = AbletonOn(manual = AbletonManual(false)),
) : AbletonDevice

@Serializable
data class Limiter(
    @SerialName("Id")
    val id: Int = 0,
    @XmlElement
    val on: AbletonOn = AbletonOn(),
    @XmlElement
    @XmlSerialName("Gain")
    val gain: AbletonFloatParameter = AbletonFloatParameter(),
    @XmlElement
    @XmlSerialName("Ceiling")
    val ceiling: AbletonFloatParameter = AbletonFloatParameter(-0.3f),
    @XmlElement
    @XmlSerialName("Release")
    val release: AbletonFloatParameter = AbletonFloatParameter(300f),
    @XmlElement
    @XmlSerialName("Lookahead")
    val lookahead: AbletonIntParameter = AbletonIntParameter(1),
) : AbletonDevice

@Serializable
data class Compressor2(
    @SerialName("Id")
    val id: Int = 0,
    @XmlElement
    val on: AbletonOn = AbletonOn(),
    @XmlElement
    @XmlSerialName("Threshold")
    val threshold: AbletonFloatParameter = AbletonFloatParameter(1f),
    @XmlElement
    @XmlSerialName("Ratio")
    val ratio: AbletonFloatParameter = AbletonFloatParameter(2f),
    @XmlElement
    @XmlSerialName("Attack")
    val attack: AbletonFloatParameter = AbletonFloatParameter(2f),
    @XmlElement
    @XmlSerialName("Release")
    val release: AbletonFloatParameter = AbletonFloatParameter(50f),
    @XmlElement
    @XmlSerialName("Gain")
    val gain: AbletonFloatParameter = AbletonFloatParameter(),
    @XmlElement
    @XmlSerialName("DryWet")
    val dryWet: AbletonFloatParameter = AbletonFloatParameter(1f),
    @XmlElement
    @XmlSerialName("Knee")
    val knee: AbletonFloatParameter = AbletonFloatParameter(6f),
    @XmlElement
    @XmlSerialName("LookAhead")
    val lookahead: AbletonIntParameter = AbletonIntParameter(),
    @XmlElement
    @XmlSerialName("SideChain")
    val sideChain: CompressorSideChain = CompressorSideChain(),
) : AbletonDevice

@Serializable
data class CompressorSideChain(
    @XmlElement
    @XmlSerialName("OnOff")
    val onOff: AbletonOn = AbletonOn(manual = AbletonManual(false)),
    @XmlElement
    @XmlSerialName("RoutedInput")
    val routedInput: CompressorRoutedInput = CompressorRoutedInput(),
)

@Serializable
data class CompressorRoutedInput(
    @XmlElement
    @XmlSerialName("Routable")
    val routable: CompressorRoutable = CompressorRoutable(),
    @XmlElement
    @XmlSerialName("Volume")
    val volume: AbletonFloatParameter = AbletonFloatParameter(1f),
)

@Serializable
data class CompressorRoutable(
    @XmlElement
    @XmlSerialName("Target")
    val target: AbletonValue<String> = AbletonValue(""),
)

@Serializable
data class AbletonFloatParameter(
    @XmlElement
    val manual: AbletonManual<Float> = AbletonManual(0f),
) {
    constructor(value: Float) : this(AbletonManual(value))
}

@Serializable
data class AbletonIntParameter(
    @XmlElement
    val manual: AbletonManual<Int> = AbletonManual(0),
) {
    constructor(value: Int) : this(AbletonManual(value))
}

@Serializable
data class AbletonValue<T>(
    @SerialName("Value")
    val value: T,
)

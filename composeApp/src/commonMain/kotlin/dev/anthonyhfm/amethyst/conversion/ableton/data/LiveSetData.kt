package dev.anthonyhfm.amethyst.conversion.ableton.data

import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class Ableton(
    @SerialName("MajorVersion")
    val majorVersion: Int,
    @SerialName("MinorVersion")
    val minorVersion: String,
    @SerialName("SchemaChangeCount")
    val schemaChangeCount: Int = 0,
    @SerialName("Creator")
    val creator: String,
    @SerialName("Revision")
    val revision: String,

    @XmlElement
    @SerialName("LiveSet")
    val liveSet: LiveSetData
)

@SerialName("LiveSet")
@Serializable
data class LiveSetData(
    @XmlElement
    @SerialName("Tracks")
    val tracks: Tracks,

    @XmlElement
    @SerialName("MasterTrack")
    val masterTrack: MasterTrack,
)

@Serializable
data class Tracks(
    val midiTracks: List<MidiTrack>
)

@Serializable
data class MidiTrack(
    @SerialName("Id")
    val id: Int,

    private val _name: Name,
    val name: String = _name.effectiveName?.value ?: "Midi Track $id",

    @SerialName("DeviceChain")
    val deviceChain: DeviceChain,
) {
    @Serializable
    data class Name(
        @XmlElement
        @XmlSerialName("EffectiveName")
        val effectiveName: EffectiveName? = null,
    ) {
        @Serializable
        data class EffectiveName(
            @XmlSerialName("Value")
            val value: String
        )
    }
}

@Serializable
data class DeviceChain(
    @XmlElement
    @SerialName("DeviceChain")
    private val deviceChain: DeviceChain,
    val devices: List<AbletonDevice> = deviceChain.devices.devices
) {
    @Serializable
    data class DeviceChain(
        @XmlElement
        @SerialName("Devices")
        val devices: Devices
    ) {
        @Serializable
        data class Devices(
            val devices: List<@Polymorphic AbletonDevice>
        )
    }
}

@Serializable
data class MasterTrack(
    @XmlElement
    val deviceChain: DeviceChain,
) {
    @Serializable
    data class DeviceChain(
        @XmlElement
        val mixer: Mixer,
    )

    @Serializable
    data class Mixer(
        @XmlElement
        val tempo: Tempo
    ) {
        @Serializable
        data class Tempo(
            @XmlElement
            val manual: AbletonManual<Double>
        )
    }
}
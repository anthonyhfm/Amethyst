package dev.anthonyhfm.amethyst.conversion.ableton.data

import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

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

    @XmlElement
    val deviceChain: DeviceChain,
    @XmlElement
    @XmlSerialName("TakeLanes")
    val takeLanes: TakeLanes? = null
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

    @Serializable
    data class TakeLanes(
        @XmlElement
        @XmlSerialName("TakeLanes")
        val takeLanes: TakeLanes
    ) {
        @Serializable
        data class TakeLanes(
            val lanes: List<TakeLane> = emptyList()
        ) {
            @Serializable
            data class TakeLane(
                @XmlSerialName("Id")
                val id: Int,

                @XmlElement
                val clipAutomation: ClipAutomation
            ) {
                @Serializable
                data class ClipAutomation(
                    @XmlElement
                    @XmlSerialName("Events")
                    val events: Events
                ) {
                    @Serializable
                    data class Events(
                        val clips: List<MidiClip> = emptyList()
                    )
                }
            }
        }
    }
}

@Serializable
data class DeviceChain(
    @XmlElement
    @SerialName("DeviceChain")
    private val deviceChain: DeviceChain,
    val devices: List<AbletonDevice> = deviceChain.devices.devices,
    @XmlElement
    @XmlSerialName("MainSequencer")
    val mainSequencer: MainSequencer? = null
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

    @Serializable
    data class MainSequencer(
        @XmlElement
        @XmlSerialName("ClipTimeable")
        val clipTimeable: ClipTimeable? = null
    ) {
        @Serializable
        data class ClipTimeable(
            @XmlElement
            @XmlSerialName("ArrangerAutomation")
            val arrangerAutomation: ArrangerAutomation
        ) {
            @Serializable
            data class ArrangerAutomation(
                @XmlElement
                @XmlSerialName("Events")
                val events: Events
            ) {
                @Serializable
                data class Events(
                    val clips: List<MidiClip> = emptyList()
                )
            }
        }
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

@Serializable
data class MidiClip(
    @XmlSerialName("Id")
    val id: Int,

    @XmlSerialName("Time")
    val time: Float,

    @XmlElement
    @XmlSerialName("Name")
    val clipName: ClipName = ClipName(),

    @XmlSerialName("CurrentStart")
    @XmlElement
    val currentStart: CurrentTimeStamp,

    @XmlSerialName("CurrentEnd")
    @XmlElement
    val currentEnd: CurrentTimeStamp,

    @XmlElement
    @XmlSerialName("Notes")
    val notes: Notes
) {
    @Serializable
    data class ClipName(
        @XmlSerialName("Value")
        val value: String = ""
    )

    @Serializable
    data class CurrentTimeStamp(
        @XmlSerialName("Value")
        val value: Float
    )

    @Serializable
    data class Notes(
        @XmlElement
        @XmlSerialName("KeyTracks")
        val keyTracks: KeyTracks
    ) {
        @Serializable
        data class KeyTracks(
            val tracks: List<KeyTrack> = emptyList()
        ) {
            @Serializable
            data class KeyTrack(
                @XmlSerialName("Id")
                val id: Int,

                @XmlElement
                @XmlSerialName("Notes")
                val notes: Notes,
                @XmlElement
                @XmlSerialName("MidiKey")
                val midiKey: MidiKey
            ) {
                @Serializable
                data class MidiKey(
                    @XmlSerialName("Value")
                    val value: Int
                )

                @Serializable
                data class Notes(
                    val notes: List<MidiNoteEvent> = emptyList()
                ) {
                    @Serializable
                    data class MidiNoteEvent(
                        @XmlSerialName("Time")
                        val time: Float,

                        @XmlSerialName("Duration")
                        val duration: Float,

                        @XmlSerialName("Velocity")
                        val velocity: Float,
                    )
                }
            }
        }
    }
}

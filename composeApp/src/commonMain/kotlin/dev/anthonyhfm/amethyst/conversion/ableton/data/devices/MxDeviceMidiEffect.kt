package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class MxDeviceMidiEffect(
    @SerialName("Id")
    override val id: Int = 0,

    @XmlElement
    @XmlSerialName("PatchSlot")
    override val patchSlot: MxDevicePatchSlot,

    @XmlElement
    @XmlSerialName("BlobSlot")
    override val blobSlot: MxDeviceBlobSlot,

    @XmlElement
    @XmlSerialName("ParameterList")
    override val parameterList: MxDeviceParameterList,

    @XmlElement
    @XmlSerialName("FileDropList")
    override val fileDropList: MxDeviceFileDropList
) : MxDevice

@Serializable
sealed interface MxParameter {
    val id: Int
    val index: Int

    @Serializable
    data class MxDIntParameter(
        @SerialName("Id")
        override val id: Int = 0,
        @XmlElement
        @XmlSerialName("Index")
        val indexObj: AbletonIndex = AbletonIndex(0),
        val timeable: MxParameterValue<Int>
    ) : MxParameter {
        override val index: Int get() = indexObj.value
    }

    @Serializable
    data class MxDEnumParameter(
        @SerialName("Id")
        override val id: Int = 0,
        @XmlElement
        @XmlSerialName("Index")
        val indexObj: AbletonIndex = AbletonIndex(0),
        val timeable: MxParameterValue<Int>
    ) : MxParameter {
        override val index: Int get() = indexObj.value
    }

    @Serializable
    data class MxDFloatParameter(
        @SerialName("Id")
        override val id: Int = 0,
        @XmlElement
        @XmlSerialName("Index")
        val indexObj: AbletonIndex = AbletonIndex(0),
        val timeable: MxParameterValue<Float>
    ) : MxParameter {
        override val index: Int get() = indexObj.value
    }

    @Serializable
    @SerialName("Timeable")
    data class MxParameterValue<T>(
        @XmlElement
        val manual: AbletonManual<T>
    )
}

@Serializable
@SerialName("Index")
data class AbletonIndex(
    @SerialName("Value")
    val value: Int = 0
)

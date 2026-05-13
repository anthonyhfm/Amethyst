package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class MxDeviceInstrument(
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

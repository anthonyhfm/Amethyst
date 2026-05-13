package dev.anthonyhfm.amethyst.conversion.ableton.data.devices

import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.FileRef
import dev.anthonyhfm.amethyst.conversion.ableton.data.utils.AbletonManual
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
data class MxDeviceMidiEffect(
    @SerialName("Id")
    val id: Int = 0,

    @XmlElement
    val patchSlot: PatchSlot,

    @XmlElement
    val blobSlot: BlobSlot,

    @XmlElement
    val parameterList: ParameterList,

    @XmlElement
    val fileDropList: FileDropList
) : AbletonDevice {
    @Serializable
    data class ParameterList(
        val parameterList: ParameterList
    ) {
        @Serializable
        data class ParameterList(
            val parameters: List<@Polymorphic MxParameter>
        )
    }

    @Serializable
    data class FileDropList(
        @XmlElement
        val fileDropList: FileDropList
    ) {
        @Serializable
        data class FileDropList(
            val items: List<MxDFullFileDrop>
        ) {
            @Serializable
            data class MxDFullFileDrop(
                @SerialName("Id")
                val id: Int = 0,
                @XmlElement
                val ref: FileRefRef,
                @XmlElement
                val name: Name? = null,
            ) {
                @Serializable
                @SerialName("Name")
                data class Name(
                    @SerialName("Value")
                    val value: String
                )

                @Serializable
                @SerialName("FileRef")
                data class FileRefRef(
                    val fileRef: FileRef
                )
            }
        }
    }

    @Serializable
    data class BlobSlot(
        @XmlElement
        val value: Value
    ) {
        @Serializable
        data class Value(
            @XmlElement
            val mxdBlob: MxDBlob
        ) {
            @Serializable
            data class MxDBlob(
                @SerialName("Id")
                val id: Int = 0,

                @XmlElement
                val blob: Blob
            ) {
                @Serializable
                data class Blob(
                    @XmlValue
                    val value: String
                )
            }
        }
    }

    @Serializable
    data class PatchSlot(
        @XmlElement
        @SerialName("Value")
        val value: Value
    ) {
        @Serializable
        data class Value(
            @XmlElement
            @XmlSerialName("MxDPatchRef")
            private val legacyPatchRef: LegacyPatchRef? = null,

            @XmlElement
            @XmlSerialName("MxPatchRef")
            private val live12PatchRef: Live12PatchRef? = null
        ) {
            val patchRef: PatchRef?
                get() = legacyPatchRef?.toPatchRef() ?: live12PatchRef?.toPatchRef()

            data class PatchRef(
                val fileRef: FileRef
            )

            @Serializable
            data class LegacyPatchRef(
                @SerialName("Id")
                val id: Int = 0,

                @XmlElement
                @SerialName("FileRef")
                val fileRef: FileRef
            ) {
                fun toPatchRef(): PatchRef = PatchRef(fileRef = fileRef)
            }

            @Serializable
            data class Live12PatchRef(
                @SerialName("Id")
                val id: Int = 0,

                @XmlElement
                @SerialName("FileRef")
                val fileRef: FileRef
            ) {
                fun toPatchRef(): PatchRef = PatchRef(fileRef = fileRef)
            }
        }
    }

    fun decodeBlob(): String {
        val cleanHex = blobSlot.value.mxdBlob.blob.value.replace("\\s".toRegex(), "")
        require(cleanHex.length % 2 == 0) { "" }

        val raw = ByteArray(cleanHex.length / 2) { idx ->
            cleanHex
                .substring(idx * 2, idx * 2 + 2)
                .toInt(16)
                .toByte()
        }

        val lastNonZero = raw.indexOfLast { it != 0.toByte() }
        return if (lastNonZero == -1) {
            ByteArray(0)
        } else {
            raw.copyOfRange(0, lastNonZero + 1)
        }.decodeToString()
    }
}

@Serializable
sealed interface MxParameter {
    val id: Int

    @Serializable
    data class MxDIntParameter(
        @SerialName("Id")
        override val id: Int = 0,
        val timeable: MxParameterValue<Int>
    ) : MxParameter

    @Serializable
    data class MxDEnumParameter(
        @SerialName("Id")
        override val id: Int = 0,
        val timeable: MxParameterValue<Int>
    ) : MxParameter

    @Serializable
    data class MxDFloatParameter(
        @SerialName("Id")
        override val id: Int = 0,
        val timeable: MxParameterValue<Float>
    ) : MxParameter

    @Serializable
    @SerialName("Timeable")
    data class MxParameterValue<T>(
        @XmlElement
        val manual: AbletonManual<T>
    )
}

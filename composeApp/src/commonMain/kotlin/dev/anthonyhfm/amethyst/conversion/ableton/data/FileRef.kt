package dev.anthonyhfm.amethyst.conversion.ableton.data

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
data class FileRef(
    @XmlElement
    @SerialName("RelativePath")
    val relativePath: RelativePath,

    @XmlElement
    val name: Name? = null,

    @XmlElement
    @SerialName("Type")
    val type: Type,
) {
    @Serializable
    data class Type(
        @SerialName("Value")
        val value: Int
    )

    @Serializable
    @SerialName("Name")
    data class Name(
        @SerialName("Value")
        val value: String
    )

    @Serializable
    data class RelativePath(
        @SerialName("Value")
        val value: String? = null,

        val items: List<RelativePathElement>
    ) {
        @Serializable
        data class RelativePathElement(
            @SerialName("Dir")
            val dir: String? = null
        )
    }

    fun resolvePath(): String {
        val projectPath = if (AbletonConverter.isZip) {
            AbletonConverter.zipStartPath
        } else {
            AbletonConverter.file!!.parent()!!.path
        }

        return "$projectPath/" + if (name != null) {
            relativePath.items.joinToString("/") { it.dir ?: "" } + "/" + name.value
        } else {
            relativePath.value ?: error("Could not resolve file path")
        }
    }
}
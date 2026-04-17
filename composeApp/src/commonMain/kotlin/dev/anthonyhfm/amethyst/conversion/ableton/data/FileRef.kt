package dev.anthonyhfm.amethyst.conversion.ableton.data

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
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
    @SerialName("Path")
    val path: Path? = null,

    @XmlElement
    val name: Name? = null,

    @XmlElement
    @SerialName("SourceHint")
    val sourceHint: SourceHint? = null,

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
    data class Path(
        @SerialName("Value")
        val value: String? = null
    )

    @Serializable
    data class SourceHint(
        @SerialName("Value")
        val value: String? = null
    )

    @Serializable
    data class RelativePath(
        @SerialName("Value")
        val value: String? = null,

        val items: List<RelativePathElement> = emptyList()
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

        return resolvePath(
            projectPath = projectPath,
            isZip = AbletonConverter.isZip,
            zipEntryExists = AbletonConverter.zipEntries::containsKey
        )
    }

    internal fun resolvePathCandidates(projectPath: String): List<String> {
        val candidates = LinkedHashSet<String>()

        buildRelativePath()?.let {
            candidates += "$projectPath/$it"
        }

        path?.value
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates += it }

        return candidates.toList()
    }

    internal fun resolvePath(
        projectPath: String,
        isZip: Boolean,
        zipEntryExists: (String) -> Boolean = { false },
        fileExists: (String) -> Boolean = { PlatformFile(it).exists() }
    ): String {
        val candidates = resolvePathCandidates(projectPath)
        if (candidates.isEmpty()) {
            error("Could not resolve file path")
        }

        return if (isZip) {
            candidates.firstOrNull(zipEntryExists) ?: candidates.first()
        } else {
            candidates.firstOrNull(fileExists) ?: candidates.first()
        }
    }

    private fun buildRelativePath(): String? {
        if (name != null) {
            val pathParts = relativePath.items
                .mapNotNull { it.dir?.takeIf(String::isNotBlank) } +
                listOf(name.value)

            if (pathParts.isNotEmpty()) {
                return pathParts.joinToString("/")
            }
        }

        return relativePath.value?.takeIf { it.isNotBlank() }
    }
}

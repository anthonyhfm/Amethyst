package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.path

object FileRef {
    fun resolveFileReference(refXml: XmlElement): String {
        val projectPath = AbletonConverter.file!!.parent()!!.path

        val relativePathType: Int = refXml.querySelector("RelativePathType")[0].attributes["Value"]?.toInt() ?: -1
        
        when (relativePathType) {
            3 -> {
                var pathString: String = projectPath

                if (refXml.querySelector("RelativePath").first().children.isEmpty()) {
                    val path = refXml.querySelector("RelativePath")[0].attributes["Value"] ?: refXml.querySelector("Name").firstOrNull()?.attributes["Value"] ?: ""
                    return "$projectPath/$path"
                } else {
                    refXml.querySelector("RelativePath").first()
                        .children.forEach {
                            pathString += "/${it.attributes["Dir"]}"
                        }

                    refXml.querySelector("Name").firstOrNull()?.attributes["Value"]?.let {
                        pathString += "/$it"
                    }
                }

                return pathString
            }

            6 -> {
                println(refXml)
            }

            1 -> {
                val parent = refXml.querySelector("RelativePathElement")
                    .last()
                    .attributes["Dir"]

                if (AbletonConverter.file?.parent()?.parent()?.name == parent) {
                    return AbletonConverter.file!!.parent()!!.parent()!!.path + "/" +  refXml.querySelector("Name").first().attributes["Value"]
                }
            }

            5 -> {
                println("Relative to User Library - Not implemented")
            }

            else -> {
                println("Unknown RelativePathType: $relativePathType")
            }
        }

        return ""
    }
}
package dev.anthonyhfm.amethyst.conversion.ableton.utils

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming

@Serializable
data class XmlElement(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<XmlElement> = emptyList(),
    val text: String? = null
) {
    fun querySelector(name: String): List<XmlElement> {
        val results = mutableListOf<XmlElement>()
        if (this.name == name) results += this
        for (child in children) {
            results += child.querySelector(name)
        }
        return results
    }

    fun localQuerySelector(name: String): List<XmlElement> {
        val results = mutableListOf<XmlElement>()
        if (this.name == name) results += this

        for (child in children) {
            if (child.name == name) {
                results += child
            }
        }

        return results
    }

    fun getRecursiveChildrenCount(): Int {
        var count = 0

        count += children.size

        for (child in children) {
            count += child.getRecursiveChildrenCount()
        }

        return count
    }
}

object SimpleXmlParser {
    fun parse(string: String): XmlElement {
        val reader: XmlReader = xmlStreaming.newReader(string)
        reader.next()

        while (reader.eventType != EventType.START_ELEMENT) {
            reader.next()
        }
        return readElement(reader)
    }

    private fun readElement(reader: XmlReader): XmlElement {
        val name = reader.localName
        val attributes = mutableMapOf<String, String>()
        for (i in 0 until reader.attributeCount) {
            attributes[reader.getAttributeLocalName(i)] = reader.getAttributeValue(i)
        }
        reader.next()

        val children = mutableListOf<XmlElement>()
        var textContent: String? = null

        while (reader.eventType != EventType.END_ELEMENT) {
            when (reader.eventType) {
                EventType.START_ELEMENT -> {
                    children += readElement(reader)
                }

                EventType.TEXT -> {
                    val t = reader.text
                    if (t.isNotBlank()) {
                        textContent = (textContent ?: "") + t
                    }
                }

                else -> {}
            }
            reader.next()
        }

        reader.next()
        return XmlElement(name, attributes, children, textContent?.trim())
    }
}
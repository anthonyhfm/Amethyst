package dev.anthonyhfm.amethyst.conversion.ableton.reader

import dev.anthonyhfm.amethyst.conversion.ableton.utils.XmlElement

class BPMReader {
    fun readBPM(xml: XmlElement): Double {
        val bpm = xml.querySelector("LiveSet")[0]
            .querySelector("MasterTrack")[0]
            .querySelector("Tempo")[0]
            .querySelector("Manual")[0]
            .attributes["Value"]?.toDoubleOrNull()

        return bpm ?: 120.0
    }
}
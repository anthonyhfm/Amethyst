package dev.anthonyhfm.amethyst.conversion.ableton.utils

object AbletonLayoutDetector {
    fun detectLayout(tracks: List<XmlElement>): AbletonLayout {
        tracks.forEach {
            println(it.querySelector("UserName").first().attributes["Value"])
        }

        val launchpadInputs = tracks.map {
            it.querySelector("MidiInputRouting").first()
                .querySelector("UpperDisplayString").first()
                .attributes["Value"]
        }.distinct().filter {
            it?.lowercase()?.contains("launchpad") ?: false
        }

        val launchpadOutputs = tracks.map {
            it.querySelector("MidiOutputRouting").first()
                .querySelector("UpperDisplayString").first()
                .attributes["Value"]
        }.distinct().filter {
            it?.lowercase()?.contains("launchpad") ?: false
        }

        println("Launchpads Found: ${launchpadInputs.size} inputs, ${launchpadOutputs.size} outputs")

        return when (launchpadInputs.size) {
            0 -> {
                if (launchpadOutputs.size <= 1) {
                    getSingleLaunchpad(tracks)
                } else { // Kaskobi MidiManager Special Treatment
                    getMidiManagerSetup(tracks)
                }
            }

            1 -> getSingleLaunchpad(tracks)
            2 -> getDualSetup(tracks)

            else -> throw IllegalArgumentException("Unsupported layout with ${launchpadInputs.size} launchpads")
        }
    }

    private fun getSingleLaunchpad(tracks: List<XmlElement>): AbletonLayout.Single {
        return AbletonLayout.Single(
            audioTrack = null,
            midiTrack = null
        )
    }

    private fun getMidiManagerSetup(tracks: List<XmlElement>): AbletonLayout.Single {
        return AbletonLayout.Single(
            audioTrack = null,
            midiTrack = null
        )
    }

    private fun getDualSetup(tracks: List<XmlElement>): AbletonLayout.Single {


        return AbletonLayout.Single(
            audioTrack = null,
            midiTrack = null
        )
    }
}

sealed interface AbletonLayout {
    data class Single(
        val audioTrack: XmlElement?,
        val midiTrack: XmlElement?
    ) : AbletonLayout
}
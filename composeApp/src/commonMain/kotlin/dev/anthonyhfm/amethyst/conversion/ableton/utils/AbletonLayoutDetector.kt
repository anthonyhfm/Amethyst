package dev.anthonyhfm.amethyst.conversion.ableton.utils

import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.OriginalSimpler
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiChainReader

object AbletonLayoutDetector {
    fun detectLayout(tracks: List<MidiTrack>): AbletonLayout {
        val audioTracks: List<Pair<Int, MidiTrack>> = tracks.filterNot {
            it.deviceChain.devices.firstOrNull { it is OriginalSimpler } == null &&
                it.deviceChain.devices.firstOrNull { it is InstrumentGroupDevice } == null
        }.map {
            println("${it.name} has a weight of ${MidiChainReader.getChainWeight(it)}")

            MidiChainReader.getChainWeight(it) to it
        }.sortedByDescending {
            it.first
        }

        println("-------------------------------------")

        val lightsTracks: List<Pair<Int, MidiTrack>> = tracks.filter {
            it.deviceChain.devices.firstOrNull { it is OriginalSimpler } == null &&
                it.deviceChain.devices.firstOrNull { it is InstrumentGroupDevice } == null
        }.map {
            println("${it.name} has a weight of ${MidiChainReader.getChainWeight(it)}")

            MidiChainReader.getChainWeight(it) to it
        }.sortedByDescending {
            it.first
        }

        val maxAudio = audioTracks.firstOrNull()?.first ?: 0
        val audioCandidates = audioTracks.filter { it.first >= maxAudio * 0.2 }
            .sortedBy { it.second.name }

        val maxLight = lightsTracks.firstOrNull()?.first ?: 0
        val lightCandidates = lightsTracks.filter { it.first >= maxLight * 0.2 }
            .sortedBy { it.second.name }

        if (audioCandidates.size == 1 && lightCandidates.size == 1) {
            return AbletonLayout.Single(
                audioTrack = audioCandidates.first().second,
                lightsTrack = lightCandidates.first().second
            )
        } else if (audioCandidates.size == 2 && lightCandidates.size == 2) {
            return AbletonLayout.Dual2Light(
                audioLeft = audioCandidates[0].second,
                audioRight = audioCandidates[1].second,
                lightsLeft = lightCandidates[0].second,
                lightsRight = lightCandidates[1].second
            )
        } else if (audioCandidates.size == 2 && lightCandidates.size == 4) {
            return AbletonLayout.Dual4Light(
                audioLeft = audioCandidates[0].second,
                audioRight = audioCandidates[1].second,
                lightsLeft = lightCandidates[0].second,
                lightsLeftToRight = lightCandidates[1].second,
                lightsRightToLeft = lightCandidates[2].second,
                lightsRight = lightCandidates[3].second
            )
        } else {
            return AbletonLayout.Single(
                audioTrack = audioTracks.firstOrNull()?.second,
                lightsTrack = lightsTracks.firstOrNull()?.second
            )
        }
    }
}

sealed interface AbletonLayout {
    data class Single(
        val audioTrack: MidiTrack?,
        val lightsTrack: MidiTrack?
    ) : AbletonLayout

    data class Dual2Light(
        val audioLeft: MidiTrack?,
        val audioRight: MidiTrack?,
        val lightsLeft: MidiTrack?,
        val lightsRight: MidiTrack?
    ) : AbletonLayout

    data class Dual4Light(
        val audioLeft: MidiTrack?,
        val audioRight: MidiTrack?,
        val lightsLeft: MidiTrack?,
        val lightsLeftToRight: MidiTrack?,
        val lightsRightToLeft: MidiTrack?,
        val lightsRight: MidiTrack?,
    ) : AbletonLayout
}

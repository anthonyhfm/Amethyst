package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData

object AbletonTutorialDetector {
    fun getAutoPlayData(layout: AbletonLayout, tracks: List<XmlElement>): AutoPlayData {
        val tracks = detectPossibleTutorialTracks(layout, tracks)

        if (tracks.isEmpty()) {
            println("No tutorial tracks found")
            return AutoPlayData(emptyMap())
        }

        return AutoPlayData(
            actions = if (layout is AbletonLayout.Single) {
                getTutorialForTrack(tracks.first(), IntOffset.Zero)
            } else {
                val leftActions = getTutorialForTrack(tracks[0], IntOffset.Zero)
                val rightActions = getTutorialForTrack(tracks[1], IntOffset(x = 10, y = 0))

                val combinedActions = mutableMapOf<Double, List<AutoPlayData.Action>>()

                for (time in leftActions.keys + rightActions.keys) {
                    val leftList = leftActions[time] ?: emptyList()
                    val rightList = rightActions[time] ?: emptyList()

                    combinedActions[time] = leftList + rightList
                }

                combinedActions
            }
        )
    }

    fun detectPossibleTutorialTracks(
        layout: AbletonLayout,
        tracks: List<XmlElement>
    ): List<XmlElement> {
        val tutorialTracks = tracks.filter {
            val name = it.querySelector("EffectiveName")
                .firstOrNull()
                ?.attributes
                ?.get("Value")
                ?.lowercase()
                ?: ""

            name.contains("tutorial")
        }.sortedBy {
            it.querySelector("EffectiveName")
                .firstOrNull()
                ?.attributes
                ?.get("Value")
                ?.lowercase()
                ?: ""
        }.take(if (layout is AbletonLayout.Single) 1 else 2)

        return tutorialTracks
    }

    fun getTutorialForTrack(track: XmlElement, offset: IntOffset): Map<Double, List<AutoPlayData.Action>> {
        val actions = mutableMapOf<Double, List<AutoPlayData.Action>>()

        val name = track.querySelector("EffectiveName")
            .firstOrNull()
            ?.attributes
            ?.get("Value")
            ?.lowercase()

        println("Trying to get tutorial from Track \"$name\"")

        val midiClips = track.querySelector("MidiClip")

        var offsetTime: Double? = null
        midiClips.forEachIndexed { index, clip ->
            val clipName = clip.querySelector("Name")
                .firstOrNull()
                ?.attributes
                ?.get("Value")

            val currentStart = clip.querySelector("CurrentStart")
                .firstOrNull()
                ?.attributes
                ?.get("Value")
                ?.toFloat()
                ?.toDouble() ?: return@forEachIndexed

            if (offsetTime == null) offsetTime = currentStart

            val currentEnd = clip.querySelector("CurrentEnd")
                .firstOrNull()
                ?.attributes
                ?.get("Value")

            val keyTracks = clip.querySelector("KeyTrack")

            keyTracks.forEach {
                val pitch = it.querySelector("MidiKey").firstOrNull()?.attributes?.get("Value")?.toInt() ?: return@forEach
                val xyPitch = DRUM_RACK_TO_XY[pitch]

                it.querySelector("MidiNoteEvent").forEach {
                    val time = ((it.attributes["Time"]?.toFloat() ?: return@forEach))
                    val velocity = it.attributes["Velocity"]?.toInt() ?: return@forEach

                    actions[time.toDouble()] = (actions[time.toDouble()] ?: emptyList()) + AutoPlayData.Action(
                        x = offset.x + (9 - xyPitch % 10),
                        y = offset.y + (9 - xyPitch / 10),
                        down = velocity > 0
                    )
                }
            }
        }

        val count = actions.size + actions.values.sumOf { it.size }

        return actions
    }
}
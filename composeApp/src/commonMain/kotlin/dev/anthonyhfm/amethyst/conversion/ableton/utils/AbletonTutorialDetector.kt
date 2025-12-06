package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import kotlin.math.roundToLong

object AbletonTutorialDetector {

    // Auflösung wie in der exportierten MIDI
    private const val TICKS_PER_BEAT = 96.0

    // Main-Layer Threshold (Hot Topic etc: 41 ist "echter" Layer)
    private const val VELOCITY_THRESHOLD = 41

    fun getAutoPlayData(layout: AbletonLayout, tracks: List<MidiTrack>): AutoPlayData {
        /*val tutorialTracks = detectPossibleTutorialTracks(layout, tracks)

        if (tutorialTracks.isEmpty()) {
            println("No tutorial tracks found")
            return AutoPlayData(emptyMap())
        }

        // 1. Pro Track absolute Zeiten holen (nicht normalisiert!)
        val rawActions: Map<Double, List<AutoPlayData.Action>> =
            if (layout is AbletonLayout.Single || tutorialTracks.size == 1) {
                getTutorialForTrack(tutorialTracks.first(), IntOffset.Zero)
            } else {
                val leftActions = getTutorialForTrack(tutorialTracks[0], IntOffset.Zero)
                val rightActions = getTutorialForTrack(tutorialTracks[1], IntOffset(x = 10, y = 0))

                val allTimes = (leftActions.keys + rightActions.keys).toSet().sorted()
                val combined = mutableMapOf<Double, List<AutoPlayData.Action>>()

                for (time in allTimes) {
                    val leftList = leftActions[time].orEmpty()
                    val rightList = rightActions[time].orEmpty()
                    if (leftList.isNotEmpty() || rightList.isNotEmpty()) {
                        combined[time] = leftList + rightList
                    }
                }

                combined
            }

        // 2. Duplikate pro Timestamp rausschmeißen
        val deduped = rawActions.mapValues { (_, list) -> list.distinct() }

        if (deduped.isEmpty()) {
            return AutoPlayData(emptyMap())
        }

        // 3. Globale Normalisierung: frühester Zeitpunkt = 0.0
        val minTime = deduped.keys.minOrNull() ?: 0.0
        val normalized =
            if (minTime != 0.0) {
                val out = mutableMapOf<Double, List<AutoPlayData.Action>>()
                for ((time, list) in deduped) {
                    out[time - minTime] = list
                }
                out
            } else deduped

        return AutoPlayData(normalized)*/

        return AutoPlayData(emptyMap())
    }

    /*fun detectPossibleTutorialTracks(
        layout: AbletonLayout,
        tracks: List<XmlElement>
    ): List<XmlElement> {
        val tutorialTracks = tracks
            .filter { track ->
                val name = track.querySelector("EffectiveName")
                    .firstOrNull()
                    ?.attributes
                    ?.get("Value")
                    ?.lowercase()
                    ?: ""
                name.contains("tutorial")
            }
            .sortedBy { track ->
                track.querySelector("EffectiveName")
                    .firstOrNull()
                    ?.attributes
                    ?.get("Value")
                    ?.lowercase()
                    ?: ""
            }
            .take(if (layout is AbletonLayout.Single) 1 else 2)

        return tutorialTracks
    }

    fun getTutorialForTrack(track: XmlElement, offset: IntOffset): Map<Double, List<AutoPlayData.Action>> {
        data class NoteEvent(
            val startTicks: Long,
            val endTicks: Long,
            val padIndex: Int
        )

        val notes = mutableListOf<NoteEvent>()

        val trackName = track.querySelector("EffectiveName")
            .firstOrNull()
            ?.attributes
            ?.get("Value")
            ?: "<unnamed>"

        println("Trying to get tutorial from Track \"$trackName\"")

        val clip = findTutorialClip(track) ?: run {
            println("No MidiClip with \"tutorial\" in name found in track \"$trackName\"")
            return emptyMap()
        }

        val clipName = clip.querySelector("Name")
            .firstOrNull()
            ?.attributes
            ?.get("Value")
            ?: "<unnamed>"

        val clipStartBeats = clip.querySelector("CurrentStart")
            .firstOrNull()
            ?.attributes
            ?.get("Value")
            ?.toDoubleOrNull()
            ?: 0.0

        val clipEndBeats = clip.querySelector("CurrentEnd")
            .firstOrNull()
            ?.attributes
            ?.get("Value")
            ?.toDoubleOrNull()
            ?: clipStartBeats

        println("Using MidiClip \"$clipName\" (start=$clipStartBeats, end=$clipEndBeats) in track \"$trackName\"")

        val bpm = AbletonConverter.bpm
        if (bpm <= 0.0) {
            println("AbletonConverter.bpm is <= 0 ($bpm). Cannot convert beats to ms.")
            return emptyMap()
        }

        val msPerBeat = 60000.0 / bpm
        val msPerTick = msPerBeat / TICKS_PER_BEAT

        val keyTracks = clip.querySelector("KeyTrack")

        keyTracks.forEach { keyTrack ->
            val pitch = keyTrack.querySelector("MidiKey")
                .firstOrNull()
                ?.attributes
                ?.get("Value")
                ?.toIntOrNull()
                ?: return@forEach

            val padIndex = DRUM_RACK_TO_XY[pitch] ?: run {
                // Pitch nicht gemappt → kein Drum Rack Pad, überspringen
                return@forEach
            }

            println("KeyTrack for pitch=$pitch mappedToPadIndex=$padIndex")

            keyTrack.querySelector("MidiNoteEvent").forEach { note ->
                if (note.attributes["IsEnabled"] == "false") return@forEach

                val velocity = note.attributes["Velocity"]?.toIntOrNull() ?: return@forEach

                // Nur echte Performance-Noten
                if (velocity < VELOCITY_THRESHOLD) {
                    return@forEach
                }

                val timeBeats = note.attributes["Time"]?.toDoubleOrNull() ?: return@forEach
                val durationBeats = note.attributes["Duration"]?.toDoubleOrNull() ?: 0.0

                // Absoluter Beat im Arrangement (Clip-Start + Note-Time)
                val absStartBeats = clipStartBeats + timeBeats
                val absEndBeats = absStartBeats + durationBeats

                val startTicks = (absStartBeats * TICKS_PER_BEAT).roundToLong()
                val endTicks = (absEndBeats * TICKS_PER_BEAT).roundToLong()

                if (endTicks < startTicks) return@forEach

                notes += NoteEvent(
                    startTicks = startTicks,
                    endTicks = endTicks,
                    padIndex = padIndex
                )
            }
        }

        if (notes.isEmpty()) {
            println("No qualifying MidiNoteEvent (velocity >= $VELOCITY_THRESHOLD) in tutorial clip \"$clipName\" of track \"$trackName\"")
            return emptyMap()
        }

        val result = mutableMapOf<Double, MutableList<AutoPlayData.Action>>()

        fun addAction(timeMs: Double, padIndex: Int, down: Boolean) {
            val x = offset.x + (padIndex % 10)
            val y = offset.y + (9 - padIndex / 10)

            val action = AutoPlayData.Action(
                x = x,
                y = y,
                down = down
            )

            val list = result.getOrPut(timeMs) { mutableListOf() }
            list += action
        }

        // Für jede Note: Start = down=true, Ende = down=false (falls Dauer > 0)
        notes
            .sortedBy { it.startTicks }
            .forEach { note ->
                val startMs = note.startTicks * msPerTick
                val endMs = note.endTicks * msPerTick

                addAction(startMs, note.padIndex, down = true)

                if (note.endTicks > note.startTicks) {
                    addAction(endMs, note.padIndex, down = false)
                }
            }

        // Pro Zeitstempel & Pad: wenn beides (down/up) existiert → down gewinnt
        val collapsed: Map<Double, List<AutoPlayData.Action>> =
            result.mapValues { (_, list) ->
                list
                    .groupBy { it.x to it.y }
                    .values
                    .map { actionsAtPad ->
                        actionsAtPad.find { it.down } ?: actionsAtPad.first()
                    }
            }

        return collapsed
    }

    /**
     * Sucht den passenden Tutorial-Clip:
     * - erst alle Clips, deren Name genau "TUTORIAL" ist (wenn vorhanden)
     * - sonst alle, deren Name "tutorial" enthält
     * - wenn gar nichts → frühesten Clip im Track
     */
    private fun findTutorialClip(track: XmlElement): XmlElement? {
        val allClips = track.querySelector("MidiClip")
        if (allClips.isEmpty()) return null

        val exactTutorial = allClips.filter { clip ->
            val clipName = clip.querySelector("Name")
                .firstOrNull()
                ?.attributes
                ?.get("Value")
                ?: ""
            clipName.equals("tutorial", ignoreCase = true)
        }

        if (exactTutorial.isNotEmpty()) {
            return exactTutorial.minByOrNull { clip ->
                clip.querySelector("CurrentStart")
                    .firstOrNull()
                    ?.attributes
                    ?.get("Value")
                    ?.toDoubleOrNull() ?: 0.0
            }
        }

        val tutorialClips = allClips.filter { clip ->
            val clipName = clip.querySelector("Name")
                .firstOrNull()
                ?.attributes
                ?.get("Value")
                ?.lowercase()
                ?: ""
            clipName.contains("tutorial")
        }

        val pool = if (tutorialClips.isNotEmpty()) tutorialClips else allClips

        return pool.minByOrNull { clip ->
            clip.querySelector("CurrentStart")
                .firstOrNull()
                ?.attributes
                ?.get("Value")
                ?.toDoubleOrNull() ?: 0.0
        }
    }*/
}

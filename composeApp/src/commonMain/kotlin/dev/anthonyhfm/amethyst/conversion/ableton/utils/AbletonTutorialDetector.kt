package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiClip
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import kotlin.math.roundToLong

object AbletonTutorialDetector {

    private const val TICKS_PER_BEAT = 96.0

    private const val VELOCITY_THRESHOLD = 41

    fun getAutoPlayData(layout: AbletonLayout, tracks: List<MidiTrack>): AutoPlayData {
        println("=== TutorialDetector: START ===")
        println("Layout: ${layout::class.simpleName}")
        println("Total tracks: ${tracks.size}")
        tracks.forEach { println("  Track: \"${it.name}\" | clips: ${it.takeLanes?.takeLanes?.lanes?.firstOrNull()?.clipAutomation?.events?.clips?.size ?: 0}") }

        var tutorialTracks = detectPossibleTutorialTracks(layout, tracks)
        println("Tutorial-named tracks found: ${tutorialTracks.map { it.name }}")

        if (tutorialTracks.isEmpty()) {
            println("No tutorial-named tracks → falling back to layout tracks (sampling/lights)")
            tutorialTracks = detectTutorialInLayoutTracks(layout)
            println("Layout fallback tracks: ${tutorialTracks.map { it.name }}")
        }

        if (tutorialTracks.isEmpty()) {
            println("=== TutorialDetector: No tutorial tracks found at all → empty AutoPlayData ===")
            return AutoPlayData(emptyMap())
        }

        var rawActions: Map<Double, List<AutoPlayData.Action>> = buildActions(layout, tutorialTracks)
        println("rawActions after primary tracks: ${rawActions.size} time entries")

        if (rawActions.isEmpty()) {
            println("Primary tracks yielded no actions → falling back to layout tracks (sampling/lights)")
            val fallbackTracks = detectTutorialInLayoutTracks(layout)
            println("Layout fallback tracks: ${fallbackTracks.map { it.name }}")
            if (fallbackTracks.isNotEmpty()) {
                rawActions = buildActions(layout, fallbackTracks)
                println("rawActions after fallback tracks: ${rawActions.size} time entries")
            }
        }

        val deduped = rawActions.mapValues { (_, list) -> list.distinct() }

        if (deduped.isEmpty()) {
            println("=== TutorialDetector: deduped is empty → empty AutoPlayData ===")
            return AutoPlayData(emptyMap())
        }

        val minTime = deduped.keys.minOrNull() ?: 0.0
        val normalized =
            if (minTime != 0.0) {
                val out = mutableMapOf<Double, List<AutoPlayData.Action>>()
                for ((time, list) in deduped) {
                    out[time - minTime] = list
                }
                out
            } else deduped

        println("=== TutorialDetector: SUCCESS → ${normalized.size} time entries, first few: ${normalized.keys.take(5)} ===")
        return AutoPlayData(normalized)
    }

    fun detectPossibleTutorialTracks(
        layout: AbletonLayout,
        tracks: List<MidiTrack>
    ): List<MidiTrack> {
        val tutorialTracks = tracks
            .filter { track ->
                track.name.lowercase().contains("tutorial")
            }
            .sortedBy { track ->
                track.name.lowercase()
            }
            .take(if (layout is AbletonLayout.Single) 1 else 2)

        return tutorialTracks
    }

    private fun buildActions(layout: AbletonLayout, tutorialTracks: List<MidiTrack>): Map<Double, List<AutoPlayData.Action>> {
        return if (layout is AbletonLayout.Single || tutorialTracks.size == 1) {
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
    }

    fun getTutorialForTrack(track: MidiTrack, offset: IntOffset): Map<Double, List<AutoPlayData.Action>> {
        data class NoteEvent(
            val startTicks: Long,
            val endTicks: Long,
            val padIndex: Int
        )

        val notes = mutableListOf<NoteEvent>()

        val trackName = track.name

        println("Trying to get tutorial from Track \"$trackName\"")

        val clips = findTutorialClips(track)

        if (clips.isEmpty()) {
            println("No usable clips found in track \"$trackName\"")
            return emptyMap()
        }

        println("Processing ${clips.size} clip(s) from track \"$trackName\"")

        val bpm = AbletonConverter.bpm
        if (bpm <= 0.0) {
            println("AbletonConverter.bpm is <= 0 ($bpm). Cannot convert beats to ms.")
            return emptyMap()
        }

        val msPerBeat = 60000.0 / bpm
        val msPerTick = msPerBeat / TICKS_PER_BEAT

        clips.forEach { clip ->
            val clipStartBeats = clip.currentStart.value
            val clipEndBeats = clip.currentEnd.value

            println("Using MidiClip (start=$clipStartBeats, end=$clipEndBeats) in track \"$trackName\"")

            val keyTracks = clip.notes.keyTracks.tracks

            keyTracks.forEach { keyTrack ->
                val pitch = keyTrack.midiKey.value

                val padIndex = DRUM_RACK_TO_XY[pitch]

                println("KeyTrack for pitch=$pitch mappedToPadIndex=$padIndex")

                keyTrack.notes.notes.forEach { note ->
                    val velocity = note.velocity

                    if (velocity < VELOCITY_THRESHOLD) {
                        return@forEach
                    }

                    val timeBeats = note.time
                    val durationBeats = note.duration

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
        }

        if (notes.isEmpty()) {
            println("No qualifying MidiNoteEvent (velocity >= $VELOCITY_THRESHOLD) in tutorial clip of track \"$trackName\"")
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

    private fun detectTutorialInLayoutTracks(layout: AbletonLayout): List<MidiTrack> {
        val candidates: List<MidiTrack> = when (layout) {
            is AbletonLayout.Single -> listOfNotNull(layout.audioTrack, layout.lightsTrack)
            is AbletonLayout.Dual2Light -> listOfNotNull(
                layout.audioLeft, layout.lightsLeft,
                layout.audioRight, layout.lightsRight
            )
            is AbletonLayout.Dual4Light -> listOfNotNull(
                layout.audioLeft, layout.lightsLeft,
                layout.audioRight, layout.lightsRight
            )
        }

        return candidates
            .filter { findTutorialClips(it).isNotEmpty() }
            .take(if (layout is AbletonLayout.Single) 1 else 2)
    }

    private fun findTutorialClips(track: MidiTrack): List<MidiClip> {
        val takeLaneClips = track.takeLanes?.takeLanes?.lanes?.firstOrNull()?.clipAutomation?.events?.clips ?: emptyList()
        val clipTimeableClips = track.deviceChain.mainSequencer?.clipTimeable?.arrangerAutomation?.events?.clips ?: emptyList()
        val allClips = (takeLaneClips + clipTimeableClips)

        println("  findTutorialClips(\"${track.name}\"): takeLanes=${track.takeLanes != null}, takeLaneClips=${takeLaneClips.size}, clipTimeableClips=${clipTimeableClips.size}")
        allClips.forEach { clip ->
            val keyTrackCount = clip.notes.keyTracks.tracks.size
            val noteCount = clip.notes.keyTracks.tracks.sumOf { it.notes.notes.size }
            println("    Clip id=${clip.id} name=\"${clip.clipName.value}\" start=${clip.currentStart.value} end=${clip.currentEnd.value} keyTracks=$keyTrackCount notes=$noteCount")
        }

        if (allClips.isEmpty()) return emptyList()

        val namedTutorialClips = allClips.filter { clip ->
            clip.clipName.value.lowercase().contains("tutorial")
        }

        if (namedTutorialClips.isNotEmpty()) {
            println("  → Using ${namedTutorialClips.size} tutorial-named clip(s)")
            return namedTutorialClips.sortedBy { it.currentStart.value }
        }

        val nonEmptyClips = allClips
            .filter { clip -> clip.notes.keyTracks.tracks.isNotEmpty() }
            .sortedBy { it.currentStart.value }
        println("  → No tutorial-named clip. Non-empty clips: ${nonEmptyClips.size}")
        return nonEmptyClips
    }
}

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

        var tutorialStartBeats = findTutorialStartBeats(tutorialTracks)
        var rawActions: Map<Double, List<AutoPlayData.Action>> = buildActions(
            layout = layout,
            tutorialTracks = tutorialTracks,
            tutorialStartBeats = tutorialStartBeats
        )
        println("rawActions after primary tracks: ${rawActions.size} time entries")

        if (rawActions.isEmpty()) {
            println("Primary tracks yielded no actions → falling back to layout tracks (sampling/lights)")
            val fallbackTracks = detectTutorialInLayoutTracks(layout)
            println("Layout fallback tracks: ${fallbackTracks.map { it.name }}")
            if (fallbackTracks.isNotEmpty()) {
                tutorialStartBeats = findTutorialStartBeats(fallbackTracks)
                rawActions = buildActions(
                    layout = layout,
                    tutorialTracks = fallbackTracks,
                    tutorialStartBeats = tutorialStartBeats
                )
                println("rawActions after fallback tracks: ${rawActions.size} time entries")
            }
        }

        val deduped = rawActions.mapValues { (_, list) -> list.distinct() }

        if (deduped.isEmpty()) {
            println("=== TutorialDetector: deduped is empty → empty AutoPlayData ===")
            return AutoPlayData(emptyMap())
        }

        println("=== TutorialDetector: SUCCESS → ${deduped.size} time entries, first few: ${deduped.keys.take(5)} ===")
        return AutoPlayData(deduped)
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

    private fun buildActions(
        layout: AbletonLayout,
        tutorialTracks: List<MidiTrack>,
        tutorialStartBeats: Double
    ): Map<Double, List<AutoPlayData.Action>> {
        return if (layout is AbletonLayout.Single || tutorialTracks.size == 1) {
            getTutorialForTrack(tutorialTracks.first(), IntOffset.Zero, tutorialStartBeats)
        } else {
            val leftActions = getTutorialForTrack(tutorialTracks[0], IntOffset.Zero, tutorialStartBeats)
            val rightActions = getTutorialForTrack(tutorialTracks[1], IntOffset(x = 10, y = 0), tutorialStartBeats)

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
        val tutorialStartBeats = findTutorialClips(track)
            .minOfOrNull { it.currentStart.value.toDouble() }
            ?: return emptyMap()

        return getTutorialForTrack(track, offset, tutorialStartBeats)
    }

    private fun getTutorialForTrack(
        track: MidiTrack,
        offset: IntOffset,
        tutorialStartBeats: Double
    ): Map<Double, List<AutoPlayData.Action>> {
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

                    if (velocity <= 0f) {
                        return@forEach
                    }

                    val timeBeats = note.time
                    val durationBeats = note.duration

                    val timelineStartBeats = clipStartBeats + timeBeats - tutorialStartBeats
                    val timelineEndBeats = timelineStartBeats + durationBeats

                    val startTicks = (timelineStartBeats * TICKS_PER_BEAT).roundToLong()
                    val endTicks = (timelineEndBeats * TICKS_PER_BEAT).roundToLong()

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
            println("No qualifying MidiNoteEvent (velocity > 0) in tutorial clips of track \"$trackName\"")
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

    private fun findTutorialStartBeats(tracks: List<MidiTrack>): Double =
        tracks
            .flatMap(::findTutorialClips)
            .minOfOrNull { it.currentStart.value.toDouble() }
            ?: 0.0

    private fun findTutorialClips(track: MidiTrack): List<MidiClip> {
        val takeLaneClips = track.takeLanes?.takeLanes?.lanes
            ?.flatMap { it.clipAutomation.events.clips }
            .orEmpty()
        val clipTimeableClips = track.deviceChain.mainSequencer?.clipTimeable?.arrangerAutomation?.events?.clips ?: emptyList()
        // Arrangement clips are the audible timeline. Take lanes are alternatives and
        // must only be considered when the track has no arrangement clips at all.
        val timelineClips = clipTimeableClips.ifEmpty { takeLaneClips }

        println("  findTutorialClips(\"${track.name}\"): takeLanes=${track.takeLanes != null}, takeLaneClips=${takeLaneClips.size}, clipTimeableClips=${clipTimeableClips.size}")
        timelineClips.forEach { clip ->
            val keyTrackCount = clip.notes.keyTracks.tracks.size
            val noteCount = clip.notes.keyTracks.tracks.sumOf { it.notes.notes.size }
            println("    Clip id=${clip.id} name=\"${clip.clipName.value}\" start=${clip.currentStart.value} end=${clip.currentEnd.value} keyTracks=$keyTrackCount notes=$noteCount")
        }

        if (timelineClips.isEmpty()) return emptyList()

        val nonEmptyClips = timelineClips
            .filter { clip -> clip.notes.keyTracks.tracks.isNotEmpty() }
            .sortedBy { it.currentStart.value }

        if (!track.name.contains("tutorial", ignoreCase = true)) {
            val namedTutorialClips = nonEmptyClips.filter {
                it.clipName.value.contains("tutorial", ignoreCase = true)
            }
            if (namedTutorialClips.isNotEmpty()) {
                println("  → Using ${namedTutorialClips.size} tutorial-named timeline clip(s)")
                return namedTutorialClips
            }
        }

        println("  → Using all ${nonEmptyClips.size} non-empty timeline clip(s)")
        return nonEmptyClips
    }
}

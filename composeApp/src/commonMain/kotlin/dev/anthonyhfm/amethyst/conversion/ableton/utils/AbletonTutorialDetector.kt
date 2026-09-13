package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.ableton.data.AbletonDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.AutomationEnvelopes
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiClip
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.DrumGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.InstrumentGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import kotlin.math.roundToInt

object AbletonTutorialDetector {
    private data class AutoPlayTarget(
        val offset: IntOffset,
        val launchpadId: String?,
    )

    internal fun beatsToMilliseconds(beats: Double, bpm: Double): Double =
        beats * (60_000.0 / bpm)

    private fun dedupePageAutomationEvents(
        events: List<AutomationEnvelopes.FloatEvent>,
    ): List<AutomationEnvelopes.FloatEvent> {
        val sortedEvents = events.withIndex()
            .sortedWith(compareBy<IndexedValue<AutomationEnvelopes.FloatEvent>> { it.value.time }.thenBy { it.index })
            .map(IndexedValue<AutomationEnvelopes.FloatEvent>::value)
        val result = mutableListOf<AutomationEnvelopes.FloatEvent>()
        for (event in sortedEvents) {
            if (result.lastOrNull()?.time == event.time) {
                result[result.lastIndex] = event
            } else {
                result += event
            }
        }
        return result
    }

    fun getAutoPlayData(layout: AbletonLayout, tracks: List<MidiTrack>): AutoPlayData {

        var tutorialTracks = detectPossibleTutorialTracks(layout, tracks)

        if (tutorialTracks.isEmpty()) {
            tutorialTracks = detectTutorialInLayoutTracks(layout)
        }

        if (tutorialTracks.isEmpty()) {
            return AutoPlayData(emptyMap())
        }

        var tutorialStartBeats = findTutorialStartBeats(tutorialTracks)
        var tutorialEndBeats = findTutorialEndBeats(tutorialTracks)
        var rawActions: Map<Double, List<AutoPlayData.Action>> = buildActions(
            layout = layout,
            tutorialTracks = tutorialTracks,
            tutorialStartBeats = tutorialStartBeats
        )

        if (rawActions.isEmpty()) {
            val fallbackTracks = detectTutorialInLayoutTracks(layout)
            if (fallbackTracks.isNotEmpty()) {
                tutorialStartBeats = findTutorialStartBeats(fallbackTracks)
                tutorialEndBeats = findTutorialEndBeats(fallbackTracks)
                rawActions = buildActions(
                    layout = layout,
                    tutorialTracks = fallbackTracks,
                    tutorialStartBeats = tutorialStartBeats
                )
            }
        }

        val macroActions = detectMacroAutomationActions(
            layout = layout,
            tracks = tracks,
            tutorialStartBeats = tutorialStartBeats,
            tutorialEndBeats = tutorialEndBeats,
        )
        if (macroActions.isNotEmpty()) {
            val combined = rawActions.toMutableMap()
            for ((timeMs, actionList) in macroActions) {
                val existing = combined.getOrElse(timeMs) { emptyList() }
                combined[timeMs] = existing + actionList
            }
            rawActions = combined
        }

        val deduped = rawActions.mapValues { (_, list) -> list.distinct() }

        if (deduped.isEmpty()) {
            return AutoPlayData(emptyMap())
        }

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
        val trackActions = tutorialTracks.map { track ->
            val target = autoPlayTarget(layout, track)
            getTutorialForTrack(
                track = track,
                offset = target.offset,
                tutorialStartBeats = tutorialStartBeats,
                launchpadId = target.launchpadId,
            )
        }
        val allTimes = trackActions.flatMap { it.keys }.toSortedSet()
        return buildMap {
            allTimes.forEach { time ->
                val actions = trackActions.flatMap { it[time].orEmpty() }
                if (actions.isNotEmpty()) put(time, actions)
            }
        }
    }

    fun getTutorialForTrack(track: MidiTrack, offset: IntOffset): Map<Double, List<AutoPlayData.Action>> {
        val tutorialStartBeats = findTutorialClips(track)
            .minOfOrNull { it.currentStart.value }
            ?: return emptyMap()

        return getTutorialForTrack(track, offset, tutorialStartBeats, launchpadId = null)
    }

    private fun getTutorialForTrack(
        track: MidiTrack,
        offset: IntOffset,
        tutorialStartBeats: Double,
        launchpadId: String?,
    ): Map<Double, List<AutoPlayData.Action>> {
        data class NoteEvent(
            val startBeats: Double,
            val endBeats: Double,
            val padIndex: Int
        )

        val notes = mutableListOf<NoteEvent>()


        val clips = findTutorialClips(track)

        if (clips.isEmpty()) {
            return emptyMap()
        }


        val bpm = AbletonConverter.bpm
        if (bpm <= 0.0) {
            return emptyMap()
        }

        val msPerBeat = beatsToMilliseconds(beats = 1.0, bpm = bpm)
        clips.forEach { clip ->
            val clipStartBeats = clip.currentStart.value

            val keyTracks = clip.notes.keyTracks.tracks

            keyTracks.forEach { keyTrack ->
                val pitch = keyTrack.midiKey.value

                val padIndex = DRUM_RACK_TO_XY[pitch]

                keyTrack.notes.notes.forEach { note ->
                    val velocity = note.velocity

                    if (velocity <= 0f) {
                        return@forEach
                    }

                    val timeBeats = note.time
                    val durationBeats = note.duration

                    val timelineStartBeats = clipStartBeats + timeBeats - tutorialStartBeats
                    val timelineEndBeats = timelineStartBeats + durationBeats

                    if (timelineEndBeats < timelineStartBeats) return@forEach

                    notes += NoteEvent(
                        startBeats = timelineStartBeats,
                        endBeats = timelineEndBeats,
                        padIndex = padIndex
                    )
                }
            }
        }

        if (notes.isEmpty()) {
            return emptyMap()
        }

        val result = mutableMapOf<Double, MutableList<AutoPlayData.Action>>()

        fun addAction(timeMs: Double, padIndex: Int, down: Boolean) {
            val x = offset.x + (padIndex % 10)
            val y = offset.y + (9 - padIndex / 10)

            val action = AutoPlayData.Action(
                x = x,
                y = y,
                down = down,
                launchpadId = launchpadId,
            )

            val list = result.getOrPut(timeMs) { mutableListOf() }
            list += action
        }

        notes
            .sortedBy { it.startBeats }
            .forEach { note ->
                val startMs = note.startBeats * msPerBeat
                val endMs = note.endBeats * msPerBeat

                addAction(startMs, note.padIndex, down = true)

                if (note.endBeats > note.startBeats) {
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
            .minOfOrNull { it.currentStart.value }
            ?: 0.0

    private fun findTutorialEndBeats(tracks: List<MidiTrack>): Double =
        tracks
            .flatMap(::findTutorialClips)
            .maxOfOrNull { it.currentEnd.value }
            ?: Double.POSITIVE_INFINITY

    private fun findTutorialClips(track: MidiTrack): List<MidiClip> {
        val takeLaneClips = track.takeLanes?.takeLanes?.lanes
            ?.flatMap { it.clipAutomation.events.clips }
            .orEmpty()
        val clipTimeableClips = track.deviceChain.mainSequencer?.clipTimeable?.arrangerAutomation?.events?.clips ?: emptyList()
        // Arrangement clips are the audible timeline. Take lanes are alternatives and
        // must only be considered when the track has no arrangement clips at all.
        val timelineClips = clipTimeableClips.ifEmpty { takeLaneClips }

        if (timelineClips.isEmpty()) return emptyList()

        val nonEmptyClips = timelineClips
            .filter { clip -> clip.notes.keyTracks.tracks.isNotEmpty() }
            .sortedBy { it.currentStart.value }

        if (!track.name.contains("tutorial", ignoreCase = true)) {
            val namedTutorialClips = nonEmptyClips.filter {
                it.clipName.value.contains("tutorial", ignoreCase = true)
            }
            if (namedTutorialClips.isNotEmpty()) {
                return namedTutorialClips
            }
        }

        return nonEmptyClips
    }

    private fun pageAutomationTargetsByTrack(tracks: List<MidiTrack>): Map<Int, MidiTrack> {
        val targets = mutableMapOf<Int, MidiTrack>()

        // Page Switcher's Live API parameter 9/17 is the rack Chain Selector after
        // 8/16 macros. Bind only the selector of the rack immediately following it.
        for (track in tracks) {
            val explicitTargets = track.deviceChain.devices.zipWithNext().mapNotNull { (candidate, following) ->
                if (candidate is MxDeviceMidiEffect && isPageSwitcher(candidate)) {
                    chainSelectorTarget(following)
                } else null
            }

            if (explicitTargets.isNotEmpty()) {
                explicitTargets.forEach { targets[it] = track }
                continue
            }

            // Compatibility fallback for tracks whose Page Switcher file is unavailable.
            // Keep this per-track: an explicit Page Switcher on one controller track must
            // not suppress the restricted legacy targets on another controller track.
            for (device in collectAllDevices(track.deviceChain.devices)) {
                when (device) {
                    is InstrumentGroupDevice -> {
                        device.chainSelector.automationTarget?.id?.let { targets[it] = track }
                        device.getPageMacro(AbletonConverter.liveVersion)?.automationTarget?.id?.let { targets[it] = track }
                    }
                    is MidiEffectGroupDevice -> {
                        device.chainSelector.automationTarget?.id?.let { targets[it] = track }
                        device.getPageMacro(AbletonConverter.liveVersion)?.automationTarget?.id?.let { targets[it] = track }
                    }
                    is DrumGroupDevice -> {
                        device.chainSelector.automationTarget?.id?.let { targets[it] = track }
                        device.getPageMacro(AbletonConverter.liveVersion)?.automationTarget?.id?.let { targets[it] = track }
                    }
                    else -> {}
                }
            }
        }
        return targets
    }

    private fun isPageSwitcher(device: MxDeviceMidiEffect): Boolean {
        val fileRef = device.patchSlot.value.patchRef?.fileRef ?: return false
        val path = fileRef.relativePath.value ?: fileRef.path?.value.orEmpty()
        return path.substringAfterLast('/').substringAfterLast('\\')
            .equals("Page Switcher.amxd", ignoreCase = true)
    }

    private fun chainSelectorTarget(device: AbletonDevice): Int? = when (device) {
        is InstrumentGroupDevice -> device.chainSelector.automationTarget?.id
        is MidiEffectGroupDevice -> device.chainSelector.automationTarget?.id
        is DrumGroupDevice -> device.chainSelector.automationTarget?.id
        else -> null
    }

    private fun collectAllDevices(devices: List<AbletonDevice>): List<AbletonDevice> {
        val result = mutableListOf<AbletonDevice>()
        for (device in devices) {
            result.add(device)
            when (device) {
                is InstrumentGroupDevice -> {
                    for (branch in device.branches.branches) {
                        result.addAll(collectAllDevices(branch.deviceChain.deviceChain.devices.devices))
                    }
                }
                is MidiEffectGroupDevice -> {
                    for (branch in device.branches.branches) {
                        result.addAll(collectAllDevices(branch.deviceChain.deviceChain.devices.devices))
                    }
                }
                is DrumGroupDevice -> {
                    for (branch in device.branches.branches) {
                        result.addAll(collectAllDevices(branch.deviceChain.deviceChain.devices.devices))
                    }
                }
                else -> {}
            }
        }
        return result
    }

    private fun autoPlayTarget(layout: AbletonLayout, track: MidiTrack): AutoPlayTarget {
        val index = sourceLaunchpadIndex(layout, track)
        val fallbackOffset = IntOffset(x = index * 10, y = 0)
        val launchpad = runCatching { AbletonConverter.launchpadTarget(fallbackOffset) }.getOrNull()
        return AutoPlayTarget(
            offset = launchpad?.offset ?: fallbackOffset,
            launchpadId = launchpad?.launchpad?.id,
        )
    }

    private fun sourceLaunchpadIndex(layout: AbletonLayout, track: MidiTrack): Int {
        val leftTracks: List<MidiTrack?>
        val rightTracks: List<MidiTrack?>
        when (layout) {
            is AbletonLayout.Single -> return 0
            is AbletonLayout.Dual2Light -> {
                leftTracks = listOf(layout.audioLeft, layout.lightsLeft)
                rightTracks = listOf(layout.audioRight, layout.lightsRight)
            }
            is AbletonLayout.Dual4Light -> {
                leftTracks = listOf(layout.audioLeft, layout.lightsLeft, layout.lightsLeftToRight)
                rightTracks = listOf(layout.audioRight, layout.lightsRightToLeft, layout.lightsRight)
            }
        }

        if (rightTracks.any { it?.id == track.id }) return 1
        if (leftTracks.any { it?.id == track.id }) return 0

        val input = track.deviceChain.midiInputRouting.target.value
        val rightInputs = rightTracks.mapNotNull { it?.deviceChain?.midiInputRouting?.target?.value }
            .filter(String::isNotBlank)
            .toSet()
        if (input.isNotBlank() && input in rightInputs) return 1

        return 0
    }

    fun detectMacroAutomationActions(
        layout: AbletonLayout,
        tracks: List<MidiTrack>,
        tutorialStartBeats: Double,
        tutorialEndBeats: Double = Double.POSITIVE_INFINITY,
    ): Map<Double, List<AutoPlayData.Action>> {
        val targetTracksById = pageAutomationTargetsByTrack(tracks)
        if (targetTracksById.isEmpty()) return emptyMap()

        val bpm = AbletonConverter.bpm
        if (bpm <= 0.0) return emptyMap()

        val msPerBeat = beatsToMilliseconds(beats = 1.0, bpm = bpm)
        val result = mutableMapOf<Double, MutableList<AutoPlayData.Action>>()

        for (track in tracks) {
            val envelopes = track.automationEnvelopes?.envelopes?.envelopes.orEmpty()
            for (envelope in envelopes) {
                val pointeeId = envelope.envelopeTarget?.pointeeId?.value
                val targetTrack = pointeeId?.let(targetTracksById::get)
                if (targetTrack != null) {
                    val target = autoPlayTarget(layout, targetTrack)
                    val offset = target.offset
                    val rawEvents = envelope.automation?.events?.floatEvents.orEmpty()
                    if (rawEvents.isEmpty()) continue

                    val dedupedEvents = pageAutomationEventsWithinTutorial(
                        events = rawEvents,
                        tutorialStartBeats = tutorialStartBeats,
                        tutorialEndBeats = tutorialEndBeats,
                    )

                    var lastEmittedPage = -1

                    for (event in dedupedEvents) {
                        val timeBeats = event.time - tutorialStartBeats
                        val targetPage = event.value.roundToInt()

                        if (targetPage !in 0..15) continue
                        if (targetPage == lastEmittedPage) continue

                        val timeMs = if (timeBeats < 0.0) 0.0 else timeBeats * msPerBeat
                        lastEmittedPage = targetPage

                        val padX = if (targetPage < 8) 9 + offset.x else 0 + offset.x
                        val padY = 1 + (targetPage % 8) + offset.y

                        val pressAction = AutoPlayData.Action(
                            x = padX,
                            y = padY,
                            down = true,
                            launchpadId = target.launchpadId,
                        )
                        val releaseAction = pressAction.copy(down = false)

                        result.getOrPut(timeMs) { mutableListOf() }.add(pressAction)
                        result.getOrPut(timeMs + 50.0) { mutableListOf() }.add(releaseAction)

                    }
                }
            }
        }

        return result
    }

    private fun pageAutomationEventsWithinTutorial(
        events: List<AutomationEnvelopes.FloatEvent>,
        tutorialStartBeats: Double,
        tutorialEndBeats: Double,
    ): List<AutomationEnvelopes.FloatEvent> {
        val eventsByTime = dedupePageAutomationEvents(events)
        return listOfNotNull(eventsByTime.lastOrNull { it.time <= tutorialStartBeats }) +
            eventsByTime.filter { it.time > tutorialStartBeats && it.time <= tutorialEndBeats }
    }
}

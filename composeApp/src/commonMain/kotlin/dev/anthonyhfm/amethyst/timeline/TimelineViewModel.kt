package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.ScrollState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimelineViewModel : ViewModel() {
    private val _tracks = MutableStateFlow<List<TimelineTrack<*>>>(emptyList())
    val tracks: StateFlow<List<TimelineTrack<*>>> = _tracks.asStateFlow()

    private val _zoomLevel = MutableStateFlow(0.025f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _scrollState = MutableStateFlow<ScrollState?>(null)
    val scrollState: StateFlow<ScrollState?> = _scrollState.asStateFlow()

    val playheadPositionMs = TimelineRepository.playheadPositionMs
    val isPlaying = TimelineRepository.isPlaying

    init {
        initializeDemoData()
        viewModelScope.launch {
            TimelineRepository.tracks.collect { repoTracks ->
                _tracks.value = repoTracks
            }
        }

        viewModelScope.launch {
            WorkspaceRepository.gridType.collect { resnapTimelineSelections() }
        }
        viewModelScope.launch {
            WorkspaceRepository.bpm.collect { resnapTimelineSelections() }
        }
    }

    private fun resnapTimelineSelections() {
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val zoom = _zoomLevel.value
        val updated = SelectionManager.selections.value.map { sel ->
            when (sel) {
                is Selectable.TimelineTime -> {
                    val snapped = GridUtils.snapToGrid(sel.timeMs, zoom, bpm, gridType)
                    if (snapped != sel.timeMs) Selectable.TimelineTime(trackIndex = sel.trackIndex, timeMs = snapped) else sel
                }
                is Selectable.TimelineRange -> {
                    val newStart = GridUtils.snapToGrid(sel.startMs, zoom, bpm, gridType)
                    val newEnd = GridUtils.snapToGrid(sel.endMs, zoom, bpm, gridType).coerceAtLeast(newStart)
                    if (newStart != sel.startMs || newEnd != sel.endMs) Selectable.TimelineRange(trackIndex = sel.trackIndex, startMs = newStart, endMs = newEnd) else sel
                }
                else -> sel
            }
        }
        SelectionManager.selections.value = updated
    }

    private fun initializeDemoData() {
        _tracks.value = listOf(AudioTimelineTrack(), MidiTimelineTrack())

        _tracks.value.forEach { track ->
            TimelineRepository.addTrack(track)
        }
    }

    fun addAudioEntry(trackIndex: Int, audioEntry: AudioEntry) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            if (trackIndex < currentTracks.size && currentTracks[trackIndex] is AudioTimelineTrack) {
                val track = currentTracks[trackIndex] as AudioTimelineTrack
                track.entries[audioEntry.startTimeMs] = audioEntry

                _tracks.value = currentTracks.toList()

                TimelineRepository.tracks.value = currentTracks.toList()
            }
        }
    }

    private fun snapToGrid(timeMs: Long, intervalMs: Long): Long {
        if (intervalMs <= 0) return timeMs.coerceAtLeast(0L)
        val q = timeMs.toDouble() / intervalMs.toDouble()
        return (kotlin.math.round(q) * intervalMs).toLong().coerceAtLeast(0L)
    }

    private fun cropEntryEnd(entry: AudioEntry, newEndMs: Long): AudioEntry? {
        if (newEndMs <= entry.startTimeMs) return null
        val newDuration = newEndMs - entry.startTimeMs
        val raw = entry.rawData
        val croppedData = if (raw != null && raw.isNotEmpty()) {
            val bytesPerSample = (entry.bitDepth / 8) * entry.channels
            val samplesPerMs = entry.sampleRate.toDouble() / 1000.0
            val totalSamplesExact = newDuration * samplesPerMs
            val totalSamples = kotlin.math.round(totalSamplesExact).toLong()
            val totalBytes = (totalSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
            raw.sliceArray(0 until totalBytes)
        } else raw
        return entry.copy(durationMs = newDuration, rawData = croppedData)
    }

    private fun cropEntryEndInPlace(entry: AudioEntry, newEndMs: Long): AudioEntry? = cropEntryEnd(entry, newEndMs)

    private fun cropNewEntryEnd(newEntry: AudioEntry, newEndMs: Long): AudioEntry? {
        if (newEndMs <= newEntry.startTimeMs) return null
        val newDuration = newEndMs - newEntry.startTimeMs
        val raw = newEntry.rawData
        val croppedData = if (raw != null && raw.isNotEmpty()) {
            val bytesPerSample = (newEntry.bitDepth / 8) * newEntry.channels
            val samplesPerMs = newEntry.sampleRate.toDouble() / 1000.0
            val totalSamplesExact = newDuration * samplesPerMs
            val totalSamples = kotlin.math.round(totalSamplesExact).toLong()
            val totalBytes = (totalSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
            raw.sliceArray(0 until totalBytes)
        } else raw
        return newEntry.copy(durationMs = newDuration, rawData = croppedData)
    }

    private fun findNearestZeroCrossing(samples: FloatArray, fromIndex: Int, searchRadius: Int): Int {
        val n = samples.size
        var bestIdx = fromIndex.coerceIn(0, n - 2)
        var minAbs = Float.MAX_VALUE
        val start = (fromIndex - searchRadius).coerceAtLeast(0)
        val end = (fromIndex + searchRadius).coerceAtMost(n - 2)
        var i = start
        while (i <= end) {
            val s0 = samples[i]
            val s1 = samples[i + 1]
            val crosses = (s0 <= 0f && s1 >= 0f) || (s0 >= 0f && s1 <= 0f)
            val score = kotlin.math.abs(s0) + kotlin.math.abs(s1)
            if (crosses && score < minAbs) {
                minAbs = score
                bestIdx = i
            }
            i++
        }
        return bestIdx
    }

    private fun applyEdgeFadesPcm(raw: ByteArray, bitDepth: Int, channels: Int, sampleRate: Int, fadeMs: Int): ByteArray {
        if (fadeMs <= 0 || raw.isEmpty()) return raw
        val samplesPerMs = sampleRate / 1000f
        val fadeSamples = kotlin.math.max(1, (samplesPerMs * fadeMs).toInt())
        val bytesPerSample = (bitDepth / 8) * channels
        val totalFrames = raw.size / bytesPerSample
        if (totalFrames <= 1) return raw

        // Wandeln in Float-Mono für Fade-Berechnung, dann zurück in PCM mit gleicher BitTiefe/Kanälen
        fun pcmToMonoFloatsLocal(raw: ByteArray): FloatArray {
            val out = FloatArray(totalFrames)
            var frameIdx = 0
            var byteIndex = 0
            while (frameIdx < totalFrames) {
                var sum = 0f
                var c = 0
                while (c < channels) {
                    val off = byteIndex + c * (bitDepth / 8)
                    val sample = when (bitDepth) {
                        8 -> {
                            val u = raw[off].toInt() and 0xFF
                            ((u - 128) / 128f)
                        }
                        16 -> {
                            val lo = raw[off].toInt() and 0xFF
                            val hi = raw[off + 1].toInt() shl 8
                            val s = (lo or hi).toShort().toInt()
                            (s / 32768f)
                        }
                        24 -> {
                            val b0 = raw[off].toInt() and 0xFF
                            val b1 = raw[off + 1].toInt() and 0xFF
                            val b2 = raw[off + 2].toInt()
                            var v = b0 or (b1 shl 8) or (b2 shl 16)
                            if ((v and 0x800000) != 0) v = v or -0x1000000
                            (v / 8388608f)
                        }
                        32 -> {
                            val b0 = raw[off].toInt() and 0xFF
                            val b1 = raw[off + 1].toInt() and 0xFF
                            val b2 = raw[off + 2].toInt() and 0xFF
                            val b3 = raw[off + 3].toInt()
                            val v = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                            if (v == Int.MIN_VALUE) -1f else (v / 2147483648f)
                        }
                        else -> {
                            val lo = raw[off].toInt() and 0xFF
                            val hi = raw[off + 1].toInt() shl 8
                            val s = (lo or hi).toShort().toInt()
                            (s / 32768f)
                        }
                    }
                    sum += sample
                    c++
                }
                out[frameIdx] = (sum / channels)
                frameIdx++
                byteIndex += bytesPerSample
            }
            return out
        }

        fun monoFloatsToPcm(rawTemplate: ByteArray, mono: FloatArray): ByteArray {
            val out = rawTemplate.copyOf()
            var frameIdx = 0
            var byteIndex = 0
            while (frameIdx < totalFrames) {
                val v = mono[frameIdx].coerceIn(-1f, 1f)
                var c = 0
                while (c < channels) {
                    val off = byteIndex + c * (bitDepth / 8)
                    when (bitDepth) {
                        8 -> {
                            val u = ((v * 128f) + 128f).toInt().coerceIn(0, 255)
                            out[off] = u.toByte()
                        }
                        16 -> {
                            val s = (v * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            out[off] = (s.toInt() and 0xFF).toByte()
                            out[off + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        24 -> {
                            var vv = (v * 8388608f).toInt()
                            out[off] = (vv and 0xFF).toByte()
                            out[off + 1] = ((vv shr 8) and 0xFF).toByte()
                            out[off + 2] = ((vv shr 16) and 0xFF).toByte()
                        }
                        32 -> {
                            val iv = (v * 2147483648f).toInt()
                            out[off] = (iv and 0xFF).toByte()
                            out[off + 1] = ((iv shr 8) and 0xFF).toByte()
                            out[off + 2] = ((iv shr 16) and 0xFF).toByte()
                            out[off + 3] = ((iv shr 24) and 0xFF).toByte()
                        }
                        else -> {
                            val s = (v * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            out[off] = (s.toInt() and 0xFF).toByte()
                            out[off + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                    }
                    c++
                }
                frameIdx++
                byteIndex += bytesPerSample
            }
            return out
        }

        val mono = pcmToMonoFloatsLocal(raw)
        // Fade-In
        var i = 0
        while (i < fadeSamples && i < mono.size) {
            val g = i.toFloat() / fadeSamples.toFloat()
            mono[i] *= g
            i++
        }
        // Fade-Out
        i = 0
        while (i < fadeSamples && i < mono.size) {
            val idx = mono.size - 1 - i
            val g = i.toFloat() / fadeSamples.toFloat()
            mono[idx] *= g
            i++
        }
        return monoFloatsToPcm(raw, mono)
    }

    private fun sliceAudio(entry: AudioEntry, startMs: Long, endMs: Long): ByteArray? {
        val raw = entry.rawData ?: return null
        val safeStart = startMs.coerceAtLeast(entry.startTimeMs)
        val safeEnd = endMs.coerceAtMost(entry.endTimeMs)
        if (safeEnd <= safeStart) return ByteArray(0)
        val bytesPerSample = (entry.bitDepth / 8) * entry.channels
        val samplesPerMs = entry.sampleRate.toDouble() / 1000.0
        var startSamplesExact = (safeStart - entry.startTimeMs) * samplesPerMs
        var endSamplesExact = (safeEnd - entry.startTimeMs) * samplesPerMs
        var startSamples = kotlin.math.round(startSamplesExact).toLong().coerceAtLeast(0)
        var endSamples = kotlin.math.round(endSamplesExact).toLong().coerceAtLeast(startSamples + 1)

        // Zero-Crossing Snap innerhalb ±searchRadius Samples
        val totalFrames = raw.size / bytesPerSample
        val mono = try {
            // Schnelle lokale Konvertierung (wie oben) für Zero-Crossing
            val floats = kotlin.run {
                val out = FloatArray(totalFrames)
                var frameIdx = 0
                var byteIndex = 0
                while (frameIdx < totalFrames) {
                    var sum = 0f; var c = 0
                    while (c < entry.channels) {
                        val off = byteIndex + c * (entry.bitDepth / 8)
                        val sample = when (entry.bitDepth) {
                            8 -> { val u = raw[off].toInt() and 0xFF; ((u - 128) / 128f) }
                            16 -> { val lo = raw[off].toInt() and 0xFF; val hi = raw[off + 1].toInt() shl 8; val s = (lo or hi).toShort().toInt(); (s / 32768f) }
                            24 -> { val b0 = raw[off].toInt() and 0xFF; val b1 = raw[off + 1].toInt() and 0xFF; val b2 = raw[off + 2].toInt(); var v = b0 or (b1 shl 8) or (b2 shl 16); if ((v and 0x800000) != 0) v = v or -0x1000000; (v / 8388608f) }
                            32 -> { val b0 = raw[off].toInt() and 0xFF; val b1 = raw[off + 1].toInt() and 0xFF; val b2 = raw[off + 2].toInt() and 0xFF; val b3 = raw[off + 3].toInt(); val v = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24); if (v == Int.MIN_VALUE) -1f else (v / 2147483648f) }
                            else -> { val lo = raw[off].toInt() and 0xFF; val hi = raw[off + 1].toInt() shl 8; val s = (lo or hi).toShort().toInt(); (s / 32768f) }
                        }
                        sum += sample; c++
                    }
                    out[frameIdx] = (sum / entry.channels)
                    frameIdx++
                    byteIndex += bytesPerSample
                }
                out
            }
            floats
        } catch (e: Exception) { null }
        if (mono != null && mono.isNotEmpty()) {
            val radius = 64 // ca. ~1.5ms bei 44.1kHz
            val startIdx = findNearestZeroCrossing(mono, startSamples.toInt(), radius)
            val endIdx = findNearestZeroCrossing(mono, (endSamples - 1).toInt(), radius)
            startSamples = startIdx.toLong().coerceIn(0, endSamples - 1)
            endSamples = endIdx.toLong().coerceIn(startSamples + 1, totalFrames.toLong())
        }

        val startByte = (startSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
        val endByte = (endSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
        if (startByte >= endByte) return ByteArray(0)
        val sliced = raw.sliceArray(startByte until endByte)
        // Mikro-Fades anwenden (z. B. 4ms)
        return applyEdgeFadesPcm(sliced, entry.bitDepth, entry.channels, entry.sampleRate, fadeMs = 4)
    }

    private fun buildEntrySegment(original: AudioEntry, segStartMs: Long, segEndMs: Long): AudioEntry? {
        if (segEndMs <= segStartMs) return null
        val data = sliceAudio(original, segStartMs, segEndMs)
        return original.copy(
            startTimeMs = segStartMs,
            durationMs = segEndMs - segStartMs,
            rawData = data
        )
    }

    private fun splitEntry(original: AudioEntry, splitStartMs: Long, splitEndMs: Long): Pair<AudioEntry?, AudioEntry?> {
        val left = if (original.startTimeMs < splitStartMs && original.endTimeMs > splitStartMs) {
            buildEntrySegment(original, original.startTimeMs, splitStartMs)
        } else null

        val right = if (original.endTimeMs > splitEndMs && original.startTimeMs < splitEndMs) {
            buildEntrySegment(original, splitEndMs, original.endTimeMs)
        } else null
        return left to right
    }

    private fun resolveOverlapAsymmetric(
        track: AudioTimelineTrack,
        newEntry: AudioEntry,
        originStartMs: Long
    ): AudioEntry? {
        val direction = newEntry.startTimeMs - originStartMs
        var adjustedNew = newEntry
        val toRemove = mutableListOf<Long>()
        val toReplace = mutableMapOf<Long, AudioEntry>()
        val toAdd = mutableListOf<AudioEntry>()

        val sorted = track.entries.values.sortedBy { it.startTimeMs }
        if (direction >= 0) {
            sorted.forEach { existing ->
                val overlaps = existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.startTimeMs
                if (!overlaps) return@forEach

                val existingFullyInsideNew = existing.startTimeMs >= adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val newFullyInsideExisting = existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs >= adjustedNew.endTimeMs
                val existingOverlapsAtStart = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val existingOverlapsAtEnd = existing.startTimeMs >= adjustedNew.startTimeMs && existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.endTimeMs
                val existingSpansAcross = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.endTimeMs

                when {
                    newFullyInsideExisting -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)

                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[it.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }

                    existingFullyInsideNew -> {
                        toRemove.add(existing.startTimeMs)
                    }

                    existingOverlapsAtStart -> {
                        cropEntryEndInPlace(existing, adjustedNew.startTimeMs)?.let { toReplace[existing.startTimeMs] = it } ?: toRemove.add(existing.startTimeMs)
                    }

                    existingOverlapsAtEnd -> {
                        val tailStart = adjustedNew.endTimeMs
                        val tailEnd = existing.endTimeMs
                        toRemove.add(existing.startTimeMs)

                        buildEntrySegment(existing, tailStart, tailEnd)?.let { toAdd.add(it) }
                    }

                    existingSpansAcross -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[it.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                }
            }
        } else {
            var earliestBlockingStart: Long? = null
            val earlierClips = mutableListOf<AudioEntry>()
            val laterClips = mutableListOf<AudioEntry>()

            sorted.forEach { existing ->
                val overlaps = existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.startTimeMs
                if (!overlaps) return@forEach
                if (existing.startTimeMs < originStartMs) {
                    earlierClips.add(existing)
                } else {
                    laterClips.add(existing)
                }
            }

            earlierClips.forEach { existing ->
                val existingFullyInsideNew = existing.startTimeMs >= adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val newFullyInsideExisting = existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs >= adjustedNew.endTimeMs
                val existingOverlapsAtStart = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val existingOverlapsAtEnd = existing.startTimeMs >= adjustedNew.startTimeMs && existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.endTimeMs
                val existingSpansAcross = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.endTimeMs
                when {
                    newFullyInsideExisting -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[it.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                    existingFullyInsideNew -> {
                        toRemove.add(existing.startTimeMs)
                    }
                    existingOverlapsAtStart -> {
                        cropEntryEndInPlace(existing, adjustedNew.startTimeMs)?.let { toReplace[existing.startTimeMs] = it } ?: toRemove.add(existing.startTimeMs)
                    }
                    existingOverlapsAtEnd -> {
                        val tailStart = adjustedNew.endTimeMs
                        val tailEnd = existing.endTimeMs
                        toRemove.add(existing.startTimeMs)
                        buildEntrySegment(existing, tailStart, tailEnd)?.let { toAdd.add(it) }
                    }
                    existingSpansAcross -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[it.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                }
            }

            for (existing in laterClips) {
                if (existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.startTimeMs) {
                    return null
                }

                if (existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs >= adjustedNew.endTimeMs) {
                    return null
                }

                if (existing.startTimeMs > adjustedNew.startTimeMs && existing.startTimeMs < adjustedNew.endTimeMs) {
                    if (earliestBlockingStart == null || existing.startTimeMs < earliestBlockingStart) {
                        earliestBlockingStart = existing.startTimeMs
                    }
                }
            }
            if (earliestBlockingStart != null) {
                cropNewEntryEnd(adjustedNew, earliestBlockingStart)?.let { adjustedNew = it } ?: return null
            }
        }

        toRemove.forEach { track.entries.remove(it) }
        toReplace.forEach { (k, v) -> track.entries[k] = v }
        toAdd.forEach { add -> track.entries[add.startTimeMs] = add }
        return adjustedNew
    }

    private fun snapshotAudioEntries(track: AudioTimelineTrack): List<AudioEntry> =
        track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }

    fun addAudioFileToTrack(trackIndex: Int, file: PlatformFile, at: Long = 0) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            val track = currentTracks.getOrNull(trackIndex) as? AudioTimelineTrack ?: return@launch
            val before = snapshotAudioEntries(track)
            track.addFromFile(file, at)
            val original = track.entries[at] ?: return@launch
            val bpm = WorkspaceRepository.bpm.value
            val gridType = WorkspaceRepository.gridType.value
            val intervals = GridUtils.computeWithGridType(_zoomLevel.value, bpm, gridType)
            val gridInterval = intervals.intervalMs
            val snappedStart = snapToGrid(original.startTimeMs, gridInterval)
            val newEntry = if (snappedStart != original.startTimeMs) {
                track.entries.remove(original.startTimeMs)
                original.copy(startTimeMs = snappedStart)
            } else original
            val resolved = resolveOverlapAsymmetric(track, newEntry, originStartMs = original.startTimeMs) ?: return@launch
            track.entries[resolved.startTimeMs] = resolved
            val after = snapshotAudioEntries(track)
            val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
            SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = resolved.startTimeMs))
            UndoManager.addAction(UndoableAction.TimelineChange(trackIndex = trackIndex, beforeEntries = before, afterEntries = after))
        }
    }

    fun setZoomLevel(zoom: Float) {
        val before = _zoomLevel.value
        val clamped = zoom.coerceIn(0.01f, 10.0f)
        _zoomLevel.value = clamped
        println("[TimelineViewModel] setZoomLevel: requested=$zoom clamped=$clamped before=$before after=${_zoomLevel.value}")
    }

    fun zoomBy(factor: Float) {
        val before = _zoomLevel.value
        val requested = before * factor
        val clamped = requested.coerceIn(0.01f, 10.0f)
        _zoomLevel.value = clamped
        println("[TimelineViewModel] zoomBy: factor=$factor before=$before requested=$requested clamped=$clamped after=${_zoomLevel.value}")
    }

    fun msToPixels(timeMs: Long): Float {
        // Use Double precision for better accuracy
        val px = (timeMs.toDouble() * _zoomLevel.value.toDouble()).toFloat()
        // println("[TimelineViewModel] msToPixels: ms=$timeMs zoom=${_zoomLevel.value} -> px=$px")
        return px
    }

    fun pixelsToMs(pixels: Float): Long {
        // Use Double precision for better accuracy
        val ms = (pixels.toDouble() / _zoomLevel.value.toDouble()).toLong()
        // println("[TimelineViewModel] pixelsToMs: px=$pixels zoom=${_zoomLevel.value} -> ms=$ms")
        return ms
    }

    fun setScrollState(scrollState: ScrollState) {
        _scrollState.value = scrollState
    }

    fun moveAudioEntry(trackIndex: Int, oldStartMs: Long, newStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        val before = snapshotAudioEntries(track)
        val entry = track.entries.remove(oldStartMs) ?: return
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val intervals = GridUtils.computeWithGridType(_zoomLevel.value, bpm, gridType)
        val gridInterval = intervals.intervalMs
        val isAlreadySnapped = gridInterval > 0 && newStartMs % gridInterval == 0L
        val snappedStart = if (isAlreadySnapped) newStartMs else snapToGrid(newStartMs, gridInterval)
        println("[TimelineViewModel] moveAudioEntry: old=$oldStartMs requested=$newStartMs snapped=$snappedStart gridInterval=$gridInterval alreadySnapped=$isAlreadySnapped")
        val movedEntry = entry.copy(startTimeMs = snappedStart)
        val resolved = resolveOverlapAsymmetric(track, movedEntry, originStartMs = oldStartMs) ?: run {
            println("[TimelineViewModel] moveAudioEntry: overlap resolution failed, restoring original")
            track.entries[oldStartMs] = entry
            return
        }
        track.entries[resolved.startTimeMs] = resolved
        val after = snapshotAudioEntries(track)
        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = resolved.startTimeMs))
        UndoManager.addAction(UndoableAction.TimelineChange(trackIndex = trackIndex, beforeEntries = before, afterEntries = after))
    }

    fun deleteAudioEntry(trackIndex: Int, entryStartMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? AudioTimelineTrack ?: return
        val original = track.entries.remove(entryStartMs) ?: return
        val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
        val current = _tracks.value.toMutableList(); current[trackIndex] = newTrack
        _tracks.value = current.toList(); TimelineRepository.tracks.value = current.toList()
        UndoManager.addAction(UndoableAction.TimelineClipDeletion(trackIndex, deleted = original))
    }

    // ========== MIDI Track Operations ==========

    /**
     * Add an audio track to the timeline
     */
    fun addAudioTrack() {
        viewModelScope.launch {
            val newTrack = AudioTimelineTrack()
            val currentTracks = _tracks.value.toMutableList()
            val trackIndex = currentTracks.size
            currentTracks.add(newTrack)
            _tracks.value = currentTracks.toList()
            TimelineRepository.addTrack(newTrack)
            
            UndoManager.addAction(
                UndoableAction.TrackAddition(
                    trackIndex = trackIndex,
                    track = newTrack
                )
            )
        }
    }

    /**
     * Add a MIDI track to the timeline
     */
    fun addMidiTrack() {
        viewModelScope.launch {
            val newTrack = MidiTimelineTrack()
            val currentTracks = _tracks.value.toMutableList()
            val trackIndex = currentTracks.size
            currentTracks.add(newTrack)
            _tracks.value = currentTracks.toList()
            TimelineRepository.addTrack(newTrack)
            
            UndoManager.addAction(
                UndoableAction.TrackAddition(
                    trackIndex = trackIndex,
                    track = newTrack
                )
            )
        }
    }

    /**
     * Add a MIDI entry to a track
     */
    fun addMidiEntry(trackIndex: Int, entry: MidiEntry) {
        viewModelScope.launch {
            val currentTracks = _tracks.value.toMutableList()
            val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return@launch
            
            track.addEntry(entry)
            
            // Update tracks
            val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
            
            SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = entry.startTimeMs))
        }
    }

    /**
     * Add a MIDI note to an entry
     */
    fun addMidiNote(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        val entry = track.entries[entryStartMs]
        if (entry != null) {
            // Add note to existing entry
            val updatedEntry = entry.copy(notes = entry.notes + note)
            track.entries[entryStartMs] = updatedEntry
            
            // Update timeline
            val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
        }
    }

    /**
     * Update a MIDI note in an entry
     */
    fun updateMidiNote(trackIndex: Int, entryStartMs: Long, oldNote: MidiNote, newNote: MidiNote) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        track.updateNote(entryStartMs, oldNote, newNote)
        
        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
    }

    /**
     * Delete a MIDI note from an entry
     */
    fun deleteMidiNote(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        track.removeNote(entryStartMs, note)
        
        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
    }

    /**
     * Delete a MIDI entry
     */
    fun deleteMidiEntry(trackIndex: Int, entryStartMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        val original = track.entries.remove(entryStartMs) ?: return
        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
        val current = _tracks.value.toMutableList(); current[trackIndex] = newTrack
        _tracks.value = current.toList(); TimelineRepository.tracks.value = current.toList()
        // Note: UndoableAction would need to be extended to support MIDI entries
    }

    /**
     * Handle double-click on lights track to create a new MIDI clip or open existing one
     */
    fun onDoubleClickMidiTrack(trackIndex: Int, timeMs: Long) {
        val track = _tracks.value.getOrNull(trackIndex) as? MidiTimelineTrack ?: return
        
        // Check if there's an existing entry at this time
        val existingEntry = track.entries.values.firstOrNull { entry ->
            timeMs >= entry.startTimeMs && timeMs < entry.endTimeMs
        }
        
        if (existingEntry != null) {
            // Open Piano Roll for existing entry
            openPianoRollForEntry(trackIndex, existingEntry)
        } else {
            // Create a new empty MIDI entry at the double-click position
            val defaultDuration = 4000L // 4 seconds default
            val newEntry = MidiEntry(
                startTimeMs = timeMs,
                durationMs = defaultDuration,
                notes = emptyList(),
                name = "Lights Clip"
            )
            
            track.addEntry(newEntry)
            
            val currentTracks = _tracks.value.toMutableList()
            val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
            currentTracks[trackIndex] = newTrack
            _tracks.value = currentTracks.toList()
            TimelineRepository.tracks.value = currentTracks.toList()
            
            SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = newEntry.startTimeMs))
            
            println("Created new lights clip at ${timeMs}ms on track $trackIndex")
            
            openPianoRollForEntry(trackIndex, newEntry)
        }
    }

    /**
     * Open Piano Roll workspace mode for editing a MIDI entry
     */
    private fun openPianoRollForEntry(trackIndex: Int, entry: MidiEntry) {
        val pianoRollMode = PianoRollWorkspaceMode()

        pianoRollMode.currentEntry = entry
        pianoRollMode.trackIndex = trackIndex
        pianoRollMode.entryStartMs = entry.startTimeMs
        pianoRollMode.onNoteAdd = { note ->
            addNoteToPianoRoll(trackIndex, entry.startTimeMs, note)
        }
        pianoRollMode.onNoteUpdate = { old, new ->
            updateNoteInPianoRoll(trackIndex, entry.startTimeMs, old, new)
        }
        pianoRollMode.onNoteDelete = { note ->
            deleteNoteFromPianoRoll(trackIndex, entry.startTimeMs, note)
        }
        pianoRollMode.modeClose = { WorkspaceRepository.switchToPreviousMode() }
        WorkspaceRepository.switchMode(pianoRollMode)
        println("Opened Piano Roll for entry at ${entry.startTimeMs}ms on track $trackIndex")
    }

    private fun addNoteToPianoRoll(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        addMidiNoteLive(trackIndex, entryStartMs, note)
    }
    private fun updateNoteInPianoRoll(trackIndex: Int, entryStartMs: Long, old: MidiNote, new: MidiNote) {
        updateMidiNoteLive(trackIndex, entryStartMs, old, new)
    }
    private fun deleteNoteFromPianoRoll(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        deleteMidiNoteLive(trackIndex, entryStartMs, note)
    }

    /**
     * Move a MIDI entry to a new position
     */
    fun moveMidiEntry(trackIndex: Int, oldStartMs: Long, newStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        
        if (track !is MidiTimelineTrack) return
        
        val midiTrack = track as TimelineTrack<MidiEntry>
        val entry = midiTrack.entries.remove(oldStartMs) ?: return
        
        val bpm = WorkspaceRepository.bpm.value
        val gridType = WorkspaceRepository.gridType.value
        val intervals = GridUtils.computeWithGridType(_zoomLevel.value, bpm, gridType)
        val gridInterval = intervals.intervalMs
        val snappedStart = snapToGrid(newStartMs, gridInterval)
        
        val movedEntry = entry.copy(startTimeMs = snappedStart)
        midiTrack.entries[snappedStart] = movedEntry
        
        val newTrack = MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
        
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = snappedStart))
    }

    /**
     * Duplicate a MIDI entry
     */
    fun duplicateMidiEntry(trackIndex: Int, entryStartMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        
        if (track !is MidiTimelineTrack) return
        
        val entry = track.entries[entryStartMs] ?: return
        
        // Place duplicate right after the original
        val newStartMs = entry.endTimeMs
        val duplicatedEntry = entry.copy(startTimeMs = newStartMs)
        track.entries[newStartMs] = duplicatedEntry
        
        // Update track
        val newTrack = MidiTimelineTrack().apply { entries.putAll(track.entries) }
        currentTracks[trackIndex] = newTrack
        _tracks.value = currentTracks.toList()
        TimelineRepository.tracks.value = currentTracks.toList()
        
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = newStartMs))
    }

    private fun updateMidiEntry(trackIndex: Int, entryStartMs: Long, transform: (MidiEntry) -> MidiEntry) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        val isMidi = track is MidiTimelineTrack
        if (!isMidi) return
        val midiTrack = track as TimelineTrack<MidiEntry>
        val entry = midiTrack.entries[entryStartMs] ?: return
        val updated = transform(entry)
        midiTrack.entries[entryStartMs] = updated

        val maxEnd = updated.notes.maxOfOrNull { it.endTimeMs } ?: updated.durationMs
        val newDuration = maxEnd.coerceAtLeast(updated.durationMs)
        if (newDuration != updated.durationMs) {
            midiTrack.entries[entryStartMs] = updated.copy(durationMs = newDuration)
        }
        val newTrackInstance = MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
        currentTracks[trackIndex] = newTrackInstance
        _tracks.value = currentTracks.toList(); TimelineRepository.tracks.value = currentTracks.toList()
        // Update Piano Roll mode entry if open
        val mode = WorkspaceRepository.mode.value
        if (mode is PianoRollWorkspaceMode && mode.trackIndex == trackIndex && mode.entryStartMs == entryStartMs) {
            mode.currentEntry = newTrackInstance.entries[entryStartMs]
        }
    }

    fun updateMidiEntryNotes(trackIndex: Int, entryStartMs: Long, newNotes: List<MidiNote>) {
        updateMidiEntry(trackIndex, entryStartMs) { it.copy(notes = newNotes) }
    }

    // Override addMidiNote to reuse logic
    fun addMidiNoteLive(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        updateMidiEntry(trackIndex, entryStartMs) { it.copy(notes = it.notes + note) }
    }

    fun updateMidiNoteLive(trackIndex: Int, entryStartMs: Long, oldNote: MidiNote, newNote: MidiNote) {
        updateMidiEntry(trackIndex, entryStartMs) { it.copy(notes = it.notes.map { n -> if (n == oldNote) newNote else n }) }
    }

    fun deleteMidiNoteLive(trackIndex: Int, entryStartMs: Long, note: MidiNote) {
        updateMidiEntry(trackIndex, entryStartMs) { it.copy(notes = it.notes.filter { n -> n != note }) }
    }

    fun resizeMidiEntry(trackIndex: Int, oldStartMs: Long, newStartMs: Long, newDurationMs: Long) {
        val currentTracks = _tracks.value.toMutableList()
        val track = currentTracks.getOrNull(trackIndex)
        if (track !is MidiTimelineTrack) return
        val timelineTrack = track as TimelineTrack<MidiEntry>
        val entry = timelineTrack.entries[oldStartMs] ?: return

        val clampedDuration = newDurationMs.coerceAtLeast(50L)
        val startChanged = newStartMs != oldStartMs

        val movedNotes = if (startChanged) {
            val delta = newStartMs - oldStartMs
            entry.notes.map { n -> n.copy(startTimeMs = (n.startTimeMs + delta).coerceAtLeast(0L)) }
        } else entry.notes

        if (startChanged) timelineTrack.entries.remove(oldStartMs)
        val updated = entry.copy(startTimeMs = newStartMs, durationMs = clampedDuration, notes = movedNotes)
        timelineTrack.entries[newStartMs] = updated

        val maxEnd = updated.notes.maxOfOrNull { it.endTimeMs } ?: updated.endTimeMs
        val finalDuration = maxEnd - updated.startTimeMs
        if (finalDuration > updated.durationMs) {
            timelineTrack.entries[newStartMs] = updated.copy(durationMs = finalDuration)
        }

        val newTrackInstance = MidiTimelineTrack().apply { entries.putAll(timelineTrack.entries) }

        currentTracks[trackIndex] = newTrackInstance
        _tracks.value = currentTracks.toList(); TimelineRepository.tracks.value = currentTracks.toList()
        SelectionManager.select(Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = newStartMs))

        val mode = WorkspaceRepository.mode.value
        if (mode is PianoRollWorkspaceMode && mode.trackIndex == trackIndex && mode.entryStartMs == oldStartMs) {
            mode.entryStartMs = newStartMs
            mode.currentEntry = newTrackInstance.entries[newStartMs]
        }
    }
}

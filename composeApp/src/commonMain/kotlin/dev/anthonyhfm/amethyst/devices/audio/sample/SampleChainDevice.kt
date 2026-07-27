package dev.anthonyhfm.amethyst.devices.audio.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioChainDeviceRole
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSourceLibrary
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.applyAutomationCurve
import dev.anthonyhfm.amethyst.ui.components.SimplerWaveformEditor
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.atomicfu.atomic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

private data class SampleEnvelopeDragState(
    val beforePoint: TimelineAutomationPoint,
    val afterPoint: TimelineAutomationPoint,
    val mode: SampleEnvelopeDragMode
)

private data class SampleEnvelopeTapState(
    val pointId: String,
    val timeMillis: Long
)

private data class SampleEnvelopeSegmentHit(
    val startPoint: TimelineAutomationPoint,
    val endPoint: TimelineAutomationPoint
)

private enum class SampleEnvelopeDragMode {
    Point,
    Curve
}

private const val SampleEnvelopeDoubleTapTimeoutMs = 300L
private const val SampleEnvelopeTapSlopPx = 10f
private const val SampleEnvelopePointHitRadiusPx = 14f
private const val SampleEnvelopeSegmentHitRadiusPx = 12f
private const val SampleEnvelopeCurveDragSensitivityPx = 96f
private const val SampleEnvelopeCurvePathSteps = 16

private data class SampleRenderCache(
    val state: SampleChainDeviceState,
    val outputSampleRate: Int,
    val rawData: ByteArray?,
    val snapshot: SampleRenderSnapshot?,
)

class SampleChainDevice : AudioChainDevice<SampleChainDeviceState>() {
    override val state = MutableStateFlow(SampleChainDeviceState())
    override val helpRef = "Sample"
    override val audioRole = AudioChainDeviceRole.Generator

    private val triggerQueue = SampleTriggerQueue()
    private val voicePool = SampleVoicePool()
    private val publishedPlayheadFrame = atomic(-1L)
    private val audioConfiguration = atomic<AudioConfiguration?>(null)
    private val renderCache = atomic<SampleRenderCache?>(null)

    /**
     * Source-frame position consumed by the audio renderer, or `-1` while idle.
     * UI code may poll this snapshot without touching real-time voice state.
     */
    val playheadFrame: Long
        get() = publishedPlayheadFrame.value

    val droppedTriggerCount: Long
        get() = triggerQueue.droppedTriggers

    companion object : ChainDeviceFactory<SampleChainDeviceState> {
        override val stateClass = SampleChainDeviceState::class
        override val serializer = SampleChainDeviceState.serializer()
        override fun create() = SampleChainDevice()

        private const val VOLUME_MIN_DB = -24f
        private const val VOLUME_MAX_DB = 24f
        private const val VOLUME_RANGE_DB = VOLUME_MAX_DB - VOLUME_MIN_DB
        private const val PLAYHEAD_REFRESH_MILLIS = 16L
    }

    private fun formatCleanTitle(fileName: String): String {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\')
        if (name.isBlank()) return "Sample"
        return "Sample ($name)"
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        val titleText = if (deviceState.isLoaded && deviceState.fileName.isNotBlank()) {
            formatCleanTitle(deviceState.fileName)
        } else {
            "Sample"
        }

        ChainDeviceShell(
            title = titleText,
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(if (deviceState.isLoaded) 480.dp else 220.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            if (deviceState.isLoaded) {
                AudioView()
            } else {
                SampleEmptyState(
                    state = state,
                    onLoaded = ::primeAudioSnapshot,
                )
            }
        }
    }

    @Composable
    private fun AudioView() {
        val deviceState by state.collectAsState()
        val resolvedRawData = deviceState.resolvedRawData()
        val livePlayheadFrame by produceState(initialValue = playheadFrame) {
            while (true) {
                value = playheadFrame
                delay(PLAYHEAD_REFRESH_MILLIS)
            }
        }
        val bytesPerFrame = (deviceState.bitDepth / 8) * deviceState.channels
        val totalFrames = if (bytesPerFrame > 0) {
            (resolvedRawData?.size ?: 0) / bytesPerFrame
        } else {
            0
        }
        val playheadPosition = livePlayheadFrame
            .takeIf { it >= 0L && totalFrames > 0 }
            ?.let { it.toFloat() / totalFrames.toFloat() }
        val activeDurationMs = (deviceState.totalDurationMs * (deviceState.endPosition - deviceState.startPosition)).coerceAtLeast(1f)

        var beforeState by remember { mutableStateOf(deviceState) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            // Waveform Viewport Canvas Area
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 180.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Theme[colors][secondary])
                    .border(1.dp, Theme[colors][border], RoundedCornerShape(6.dp))
            ) {
                SimplerWaveformEditor(
                    rawData = resolvedRawData,
                    sampleRate = deviceState.sampleRate,
                    channels = deviceState.channels,
                    bitDepth = deviceState.bitDepth,
                    totalDurationMs = deviceState.totalDurationMs,
                    startPosition = deviceState.startPosition,
                    endPosition = deviceState.endPosition,
                    fadeInMs = deviceState.fadeInMs,
                    fadeOutMs = deviceState.fadeOutMs,
                    playheadPosition = playheadPosition,
                    onStartPositionChange = { newStart ->
                        val targetStart = newStart.coerceIn(0f, deviceState.endPosition - 0.001f)
                        val newActiveDurMs = (deviceState.totalDurationMs * (deviceState.endPosition - targetStart)).coerceAtLeast(1f)
                        state.update {
                            it.copy(
                                startPosition = targetStart,
                                fadeInMs = it.fadeInMs.coerceAtMost(newActiveDurMs),
                                fadeOutMs = it.fadeOutMs.coerceAtMost(newActiveDurMs)
                            )
                        }
                    },
                    onEndPositionChange = { newEnd ->
                        val targetEnd = newEnd.coerceIn(deviceState.startPosition + 0.001f, 1f)
                        val newActiveDurMs = (deviceState.totalDurationMs * (targetEnd - deviceState.startPosition)).coerceAtLeast(1f)
                        state.update {
                            it.copy(
                                endPosition = targetEnd,
                                fadeInMs = it.fadeInMs.coerceAtMost(newActiveDurMs),
                                fadeOutMs = it.fadeOutMs.coerceAtMost(newActiveDurMs)
                            )
                        }
                    },
                    onStartPositionFinishChange = {
                        pushStateChange(before = beforeState, after = state.value)
                        beforeState = state.value
                    },
                    onEndPositionFinishChange = {
                        pushStateChange(before = beforeState, after = state.value)
                        beforeState = state.value
                    },
                    onFadeInChange = { newFadeIn ->
                        state.update { it.copy(fadeInMs = newFadeIn.coerceIn(0f, activeDurationMs)) }
                    },
                    onFadeOutChange = { newFadeOut ->
                        state.update { it.copy(fadeOutMs = newFadeOut.coerceIn(0f, activeDurationMs)) }
                    },
                    onFadeInFinishChange = {
                        pushStateChange(before = beforeState, after = state.value)
                        beforeState = state.value
                    },
                    onFadeOutFinishChange = {
                        pushStateChange(before = beforeState, after = state.value)
                        beforeState = state.value
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Compact Flat Controls Panel
            SampleFlatControlsView(
                state = state,
                deviceState = deviceState,
                volumeMinDb = VOLUME_MIN_DB,
                volumeRangeDb = VOLUME_RANGE_DB,
                volumeMaxDb = VOLUME_MAX_DB,
                onPushStateChange = { before, after ->
                    pushStateChange(before = before, after = after)
                }
            )
        }
    }

    override fun signalEnter(n: List<Signal>) {
        var triggerCount = 0
        n.forEach { signal ->
            if (signal is Signal.Midi && signal.velocity != 0) {
                triggerCount++
            }
        }
        if (triggerCount == 0) return

        val deviceState = state.value
        if (!deviceState.isLoaded) {
            signalExit?.invoke(n)
            return
        }

        val snapshot = renderSnapshot(deviceState) ?: return
        repeat(triggerCount) {
            triggerQueue.offer(snapshot)
        }
    }

    override fun prepareAudio(configuration: AudioConfiguration) {
        audioConfiguration.value = configuration
        renderCache.value = null
        triggerQueue.clear()
        voicePool.prepare(configuration)
        publishedPlayheadFrame.value = -1L
        primeAudioSnapshot()
    }

    override fun processAudio(
        block: AudioProcessingBlock,
        context: AudioRenderContext,
    ) {
        var pending = triggerQueue.poll()
        while (pending != null) {
            voicePool.trigger(pending)
            pending = triggerQueue.poll()
        }
        voicePool.render(block)
        publishedPlayheadFrame.value = voicePool.sourceFrame
    }

    override fun resetAudio() {
        triggerQueue.clear()
        voicePool.stop()
        publishedPlayheadFrame.value = -1L
    }

    override fun releaseAudio() {
        resetAudio()
        audioConfiguration.value = null
    }

    override fun onStateRestored() {
        renderCache.value = null
        primeAudioSnapshot()
    }

    private fun primeAudioSnapshot() {
        if (audioConfiguration.value != null) {
            renderSnapshot(state.value)
        }
    }

    private fun renderSnapshot(
        deviceState: SampleChainDeviceState,
    ): SampleRenderSnapshot? {
        val outputSampleRate = audioConfiguration.value?.sampleRate
            ?: deviceState.sampleRate
        val resolvedRawData = deviceState.resolvedRawData()
        val cached = renderCache.value
        if (cached?.state == deviceState &&
            cached.outputSampleRate == outputSampleRate &&
            cached.rawData === resolvedRawData
        ) {
            return cached.snapshot
        }
        val snapshot = SampleRenderSnapshot.from(
            state = deviceState,
            rawData = resolvedRawData,
        )
        snapshot?.let(voicePool::prepareSnapshot)
        return snapshot.also {
            renderCache.value = SampleRenderCache(
                state = deviceState,
                outputSampleRate = outputSampleRate,
                rawData = resolvedRawData,
                snapshot = it,
            )
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000L
        val remainderMs = durationMs % 1000L
        return if (durationMs < 10_000L) {
            val hundredths = (remainderMs / 10L).toString().padStart(2, '0')
            "$totalSeconds.$hundredths s"
        } else {
            val tenths = remainderMs / 100L
            "$totalSeconds.$tenths s"
        }
    }

    @Composable
    private fun SampleVolumeEnvelopeEditor(
        modifier: Modifier = Modifier,
        lane: TimelineAutomationLane,
        durationMs: Long,
        onLaneCommitted: (beforeLane: TimelineAutomationLane, afterLane: TimelineAutomationLane) -> Unit
    ) {
        val normalizedDurationMs = durationMs.coerceAtLeast(1L)
        val normalizedLane = lane.normalized()
        val palette = Theme[colors]
        val mutedForegroundColor = palette[mutedForeground]
        val secondaryColor = palette[secondary]
        val target = TimelineTrackAutomationTarget.VOLUME
        val currentIsAltPressed by rememberUpdatedState(ModifierKeysState.isAltPressed)

        var dragState by remember(normalizedLane.points) {
            mutableStateOf<SampleEnvelopeDragState?>(null)
        }
        var lastTapState by remember(normalizedLane.points) {
            mutableStateOf<SampleEnvelopeTapState?>(null)
        }

        val renderedLane = remember(normalizedLane, dragState) {
            val currentDragState = dragState ?: return@remember normalizedLane
            normalizedLane.withPointUpdates(listOf(currentDragState.afterPoint))
        }

        Box(
            modifier = modifier
                .pointerInput(normalizedLane.points, normalizedDurationMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Main
                        )
                        var cancelled = false
                        var upPosition: Offset? = null
                        var upTimeMillis = 0L

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if (change.isConsumed) {
                                cancelled = true
                                break
                            }
                            if (!change.pressed) {
                                upPosition = change.position
                                upTimeMillis = change.uptimeMillis
                                break
                            }
                        }

                        val releasePosition = upPosition ?: return@awaitEachGesture
                        if (cancelled || !isWithinSampleEnvelopeTapSlop(down.position, releasePosition)) {
                            return@awaitEachGesture
                        }

                        val hitPoint = hitSampleEnvelopePoint(
                            points = normalizedLane.points,
                            tapOffset = releasePosition,
                            contentWidthPx = size.width.toFloat(),
                            laneHeightPx = size.height.toFloat(),
                            durationMs = normalizedDurationMs,
                            target = target
                        )

                        if (hitPoint != null) {
                            val isDoubleTap = lastTapState?.pointId == hitPoint.pointId &&
                                    upTimeMillis - (lastTapState?.timeMillis ?: 0L) <= SampleEnvelopeDoubleTapTimeoutMs
                            if (isDoubleTap) {
                                val updatedLane = normalizedLane.withoutPoints(listOf(hitPoint.pointId))
                                onLaneCommitted(normalizedLane, updatedLane)
                                lastTapState = null
                            } else {
                                lastTapState = SampleEnvelopeTapState(
                                    pointId = hitPoint.pointId,
                                    timeMillis = upTimeMillis
                                )
                            }
                            return@awaitEachGesture
                        }

                        val updatedLane = normalizedLane.withPointUpdates(
                            listOf(
                                TimelineAutomationPoint(
                                    timeMs = sampleEnvelopeOffsetToTimeMs(
                                        x = releasePosition.x,
                                        contentWidthPx = size.width.toFloat(),
                                        durationMs = normalizedDurationMs
                                    ),
                                    value = sampleEnvelopeOffsetToValue(
                                        y = releasePosition.y,
                                        laneHeightPx = size.height.toFloat(),
                                        target = target
                                    )
                                )
                            )
                        )
                        onLaneCommitted(normalizedLane, updatedLane)
                        lastTapState = null
                    }
                }
                .pointerInput(normalizedLane.points, normalizedDurationMs, currentIsAltPressed) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val hitPoint = hitSampleEnvelopePoint(
                                points = normalizedLane.points,
                                tapOffset = offset,
                                contentWidthPx = size.width.toFloat(),
                                laneHeightPx = size.height.toFloat(),
                                durationMs = normalizedDurationMs,
                                target = target
                            )
                            if (hitPoint != null) {
                                dragState = SampleEnvelopeDragState(
                                    beforePoint = hitPoint,
                                    afterPoint = hitPoint,
                                    mode = SampleEnvelopeDragMode.Point
                                )
                                return@detectDragGestures
                            }

                            if (currentIsAltPressed) {
                                val hitSegment = hitSampleEnvelopeSegment(
                                    points = normalizedLane.points,
                                    tapOffset = offset,
                                    contentWidthPx = size.width.toFloat(),
                                    laneHeightPx = size.height.toFloat(),
                                    durationMs = normalizedDurationMs,
                                    target = target
                                )
                                if (hitSegment != null) {
                                    dragState = SampleEnvelopeDragState(
                                        beforePoint = hitSegment.startPoint,
                                        afterPoint = hitSegment.startPoint,
                                        mode = SampleEnvelopeDragMode.Curve
                                    )
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val currentDragState = dragState ?: return@detectDragGestures
                            val updatedPoint = when (currentDragState.mode) {
                                SampleEnvelopeDragMode.Point -> currentDragState.beforePoint.copy(
                                    timeMs = sampleEnvelopeOffsetToTimeMs(
                                        x = change.position.x,
                                        contentWidthPx = size.width.toFloat(),
                                        durationMs = normalizedDurationMs
                                    ),
                                    value = sampleEnvelopeOffsetToValue(
                                        y = change.position.y,
                                        laneHeightPx = size.height.toFloat(),
                                        target = target
                                    )
                                )

                                SampleEnvelopeDragMode.Curve -> currentDragState.afterPoint.copy(
                                    curve = (currentDragState.afterPoint.curve - (dragAmount.y / SampleEnvelopeCurveDragSensitivityPx))
                                        .coerceIn(-1f, 1f)
                                )
                            }

                            if (updatedPoint != currentDragState.afterPoint) {
                                dragState = currentDragState.copy(afterPoint = updatedPoint)
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            val currentDragState = dragState
                            dragState = null
                            if (currentDragState != null && currentDragState.beforePoint != currentDragState.afterPoint) {
                                val updatedLane = normalizedLane.withPointUpdates(listOf(currentDragState.afterPoint))
                                onLaneCommitted(normalizedLane, updatedLane)
                            }
                        },
                        onDragCancel = {
                            dragState = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val neutralY = sampleEnvelopeValueToY(
                    value = TimelineTrackAutomationTarget.VOLUME.defaultValue,
                    laneHeightPx = size.height,
                    target = target
                )
                drawLine(
                    color = mutedForegroundColor.copy(alpha = 0.32f),
                    start = Offset(0f, neutralY),
                    end = Offset(size.width, neutralY),
                    strokeWidth = 1.dp.toPx()
                )

                val lanePath = Path().apply {
                    moveTo(
                        0f,
                        sampleEnvelopeValueToY(
                            value = target.defaultValue,
                            laneHeightPx = size.height,
                            target = target
                        )
                    )

                    if (renderedLane.points.isEmpty()) {
                        lineTo(size.width, neutralY)
                    } else {
                        renderedLane.points.forEachIndexed { index, point ->
                            val pointX = sampleEnvelopeTimeToX(point.timeMs, size.width, normalizedDurationMs)
                            val pointY = sampleEnvelopeValueToY(
                                value = point.value,
                                laneHeightPx = size.height,
                                target = target
                            )

                            if (index == 0) {
                                lineTo(pointX, neutralY)
                                lineTo(pointX, pointY)
                            } else {
                                appendSampleEnvelopeSegmentToPath(
                                    path = this,
                                    startPoint = renderedLane.points[index - 1],
                                    endPoint = point,
                                    contentWidthPx = size.width,
                                    laneHeightPx = size.height,
                                    durationMs = normalizedDurationMs,
                                    target = target
                                )
                            }
                        }

                        lineTo(
                            size.width,
                            sampleEnvelopeValueToY(
                                value = renderedLane.points.last().value,
                                laneHeightPx = size.height,
                                target = target
                            )
                        )
                    }
                }

                drawPath(
                    path = lanePath,
                    color = Color(0xFF6CA5FF),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                renderedLane.points.forEach { point ->
                    val pointX = sampleEnvelopeTimeToX(point.timeMs, size.width, normalizedDurationMs)
                    val pointY = sampleEnvelopeValueToY(
                        value = point.value,
                        laneHeightPx = size.height,
                        target = target
                    )
                    val isDraggedPoint = dragState?.beforePoint?.pointId == point.pointId
                    drawCircle(
                        color = Color(0xFF6CA5FF),
                        radius = if (isDraggedPoint) 5.dp.toPx() else 4.dp.toPx(),
                        center = Offset(pointX, pointY)
                    )
                    drawCircle(
                        color = secondaryColor,
                        radius = if (isDraggedPoint) 2.2.dp.toPx() else 1.8.dp.toPx(),
                        center = Offset(pointX, pointY)
                    )
                }
            }
        }
    }
}

@Serializable
data class SampleChainDeviceState(
    val fileName: String = "",
    val rawData: ByteArray? = null,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitDepth: Int = 16,
    val totalDurationMs: Long = 0L,
    val isLoaded: Boolean = false,
    val fadeInMs: Float = 0f,
    val fadeOutMs: Float = 0f,
    val volumeDb: Float = 0f,
    val startPosition: Float = 0f,
    val endPosition: Float = 1f,
    @SerialName("volumeAutomationLane")
    val volumeAutomationLane: TimelineAutomationLane? = null,
    val sourceId: String? = null,
) : DeviceState()

fun SampleChainDeviceState.resolvedRawData(): ByteArray? =
    sourceId
        ?.takeIf(String::isNotBlank)
        ?.let(AudioSourceLibrary::get)
        ?.rawData
        ?: rawData

fun sampleChainStateFromAudioEntry(
    entry: AudioEntry,
    volumeAutomationLane: TimelineAutomationLane? = null
): SampleChainDeviceState? {
    val source = entry.source() ?: return null
    if (source.totalSamples <= 0L || entry.clipEndSample <= entry.clipStartSample) return null
    val startPosition = entry.clipStartSample.toDouble()
        .div(source.totalSamples)
        .toFloat()
        .coerceIn(0f, 1f)
    val endPosition = entry.clipEndSample.toDouble()
        .div(source.totalSamples)
        .toFloat()
        .coerceIn(startPosition, 1f)
    val displayName = entry.name.ifBlank { entry.fileName }

    return SampleChainDeviceState(
        fileName = displayName,
        rawData = null,
        sampleRate = entry.sampleRate,
        channels = entry.channels,
        bitDepth = entry.bitDepth,
        totalDurationMs = source.totalDurationMs,
        isLoaded = true,
        startPosition = startPosition,
        endPosition = endPosition,
        volumeAutomationLane = volumeAutomationLane?.normalized(),
        sourceId = source.id,
    )
}

private fun appendSampleEnvelopeSegmentToPath(
    path: Path,
    startPoint: TimelineAutomationPoint,
    endPoint: TimelineAutomationPoint,
    contentWidthPx: Float,
    laneHeightPx: Float,
    durationMs: Long,
    target: TimelineTrackAutomationTarget
) {
    if (abs(startPoint.curve) < 0.001f) {
        path.lineTo(
            sampleEnvelopeTimeToX(endPoint.timeMs, contentWidthPx, durationMs),
            sampleEnvelopeValueToY(
                value = endPoint.value,
                laneHeightPx = laneHeightPx,
                target = target
            )
        )
        return
    }

    val startX = sampleEnvelopeTimeToX(startPoint.timeMs, contentWidthPx, durationMs)
    val endX = sampleEnvelopeTimeToX(endPoint.timeMs, contentWidthPx, durationMs)
    for (step in 1..SampleEnvelopeCurvePathSteps) {
        val progress = step.toFloat() / SampleEnvelopeCurvePathSteps.toFloat()
        path.lineTo(
            sampleEnvelopeLerp(startX, endX, progress),
            sampleEnvelopeValueToY(
                value = sampleEnvelopeSegmentValueAtProgress(
                    startPoint = startPoint,
                    endPoint = endPoint,
                    progress = progress
                ),
                laneHeightPx = laneHeightPx,
                target = target
            )
        )
    }
}

private fun hitSampleEnvelopePoint(
    points: List<TimelineAutomationPoint>,
    tapOffset: Offset,
    contentWidthPx: Float,
    laneHeightPx: Float,
    durationMs: Long,
    target: TimelineTrackAutomationTarget
): TimelineAutomationPoint? {
    if (points.isEmpty()) return null

    return points.firstOrNull { point ->
        val pointOffset = Offset(
            x = sampleEnvelopeTimeToX(point.timeMs, contentWidthPx, durationMs),
            y = sampleEnvelopeValueToY(
                value = point.value,
                laneHeightPx = laneHeightPx,
                target = target
            )
        )
        sampleEnvelopeDistanceSquared(pointOffset, tapOffset) <=
                SampleEnvelopePointHitRadiusPx * SampleEnvelopePointHitRadiusPx
    }
}

private fun hitSampleEnvelopeSegment(
    points: List<TimelineAutomationPoint>,
    tapOffset: Offset,
    contentWidthPx: Float,
    laneHeightPx: Float,
    durationMs: Long,
    target: TimelineTrackAutomationTarget
): SampleEnvelopeSegmentHit? {
    if (points.size < 2) return null

    var closestHit: SampleEnvelopeSegmentHit? = null
    var closestDistance = Float.MAX_VALUE
    points.zipWithNext().forEach { (startPoint, endPoint) ->
        val startX = sampleEnvelopeTimeToX(startPoint.timeMs, contentWidthPx, durationMs)
        val endX = sampleEnvelopeTimeToX(endPoint.timeMs, contentWidthPx, durationMs)
        if (tapOffset.x < minOf(startX, endX) - SampleEnvelopeSegmentHitRadiusPx ||
            tapOffset.x > maxOf(startX, endX) + SampleEnvelopeSegmentHitRadiusPx
        ) {
            return@forEach
        }

        var previousSample = Offset(
            x = startX,
            y = sampleEnvelopeValueToY(
                value = startPoint.value,
                laneHeightPx = laneHeightPx,
                target = target
            )
        )
        val sampleCount = SampleEnvelopeCurvePathSteps * 2
        for (step in 1..sampleCount) {
            val progress = step.toFloat() / sampleCount.toFloat()
            val sample = Offset(
                x = sampleEnvelopeLerp(startX, endX, progress),
                y = sampleEnvelopeValueToY(
                    value = sampleEnvelopeSegmentValueAtProgress(
                        startPoint = startPoint,
                        endPoint = endPoint,
                        progress = progress
                    ),
                    laneHeightPx = laneHeightPx,
                    target = target
                )
            )
            val distance = sampleEnvelopeDistanceToSegment(
                point = tapOffset,
                start = previousSample,
                end = sample
            )
            if (distance < closestDistance) {
                closestDistance = distance
                closestHit = SampleEnvelopeSegmentHit(
                    startPoint = startPoint,
                    endPoint = endPoint
                )
            }
            previousSample = sample
        }
    }

    return closestHit?.takeIf { closestDistance <= SampleEnvelopeSegmentHitRadiusPx }
}

private fun sampleEnvelopeTimeToX(
    timeMs: Long,
    contentWidthPx: Float,
    durationMs: Long
): Float {
    if (durationMs <= 0L) return 0f
    return ((timeMs.coerceIn(0L, durationMs)).toFloat() / durationMs.toFloat()) * contentWidthPx
}

private fun sampleEnvelopeOffsetToTimeMs(
    x: Float,
    contentWidthPx: Float,
    durationMs: Long
): Long {
    if (contentWidthPx <= 0f || durationMs <= 0L) return 0L
    return ((x / contentWidthPx).coerceIn(0f, 1f) * durationMs.toFloat()).roundToLong()
}

private fun sampleEnvelopeValueToY(
    value: Float,
    laneHeightPx: Float,
    target: TimelineTrackAutomationTarget
): Float {
    val normalizedValue = target.valueToDisplayProgress(value)
    return laneHeightPx - (normalizedValue * laneHeightPx)
}

private fun sampleEnvelopeOffsetToValue(
    y: Float,
    laneHeightPx: Float,
    target: TimelineTrackAutomationTarget
): Float {
    if (laneHeightPx <= 0f) return target.defaultValue
    val normalizedValue = (1f - (y / laneHeightPx)).coerceIn(0f, 1f)
    return target.displayProgressToValue(normalizedValue)
}

private fun sampleEnvelopeSegmentValueAtProgress(
    startPoint: TimelineAutomationPoint,
    endPoint: TimelineAutomationPoint,
    progress: Float
): Float {
    val curvedProgress = applyAutomationCurve(
        progress = progress,
        curve = startPoint.curve
    )
    return sampleEnvelopeLerp(startPoint.value, endPoint.value, curvedProgress)
}

private fun isWithinSampleEnvelopeTapSlop(start: Offset, end: Offset): Boolean {
    return sampleEnvelopeDistanceSquared(start, end) <=
            SampleEnvelopeTapSlopPx * SampleEnvelopeTapSlopPx
}

private fun sampleEnvelopeDistanceToSegment(
    point: Offset,
    start: Offset,
    end: Offset
): Float {
    val segment = end - start
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared <= 0.0001f) {
        return sqrt(sampleEnvelopeDistanceSquared(start, point))
    }

    val t = (((point.x - start.x) * segment.x) + ((point.y - start.y) * segment.y)) / lengthSquared
    val projection = Offset(
        x = start.x + (segment.x * t.coerceIn(0f, 1f)),
        y = start.y + (segment.y * t.coerceIn(0f, 1f))
    )
    return sqrt(sampleEnvelopeDistanceSquared(projection, point))
}

private fun sampleEnvelopeDistanceSquared(start: Offset, end: Offset): Float {
    val deltaX = start.x - end.x
    val deltaY = start.y - end.y
    return (deltaX * deltaX) + (deltaY * deltaY)
}

private fun sampleEnvelopeLerp(start: Float, end: Float, progress: Float): Float {
    return start + ((end - start) * progress.coerceIn(0f, 1f))
}

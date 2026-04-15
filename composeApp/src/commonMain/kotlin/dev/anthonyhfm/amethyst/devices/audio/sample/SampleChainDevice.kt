package dev.anthonyhfm.amethyst.devices.audio.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.twotone.AudioFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.applyAutomationCurve
import dev.anthonyhfm.amethyst.timeline.data.samplesToUs
import dev.anthonyhfm.amethyst.timeline.data.usToRoundedMs
import dev.anthonyhfm.amethyst.ui.components.WaveformView
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.TextDial
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
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

private const val SampleEnvelopeHeightDp = 96
private const val SampleEnvelopeDoubleTapTimeoutMs = 300L
private const val SampleEnvelopeTapSlopPx = 10f
private const val SampleEnvelopePointHitRadiusPx = 14f
private const val SampleEnvelopeSegmentHitRadiusPx = 12f
private const val SampleEnvelopeCurveDragSensitivityPx = 96f
private const val SampleEnvelopeCurvePathSteps = 16

class SampleChainDevice : AudioChainDevice<SampleChainDeviceState>() {
    override val state = MutableStateFlow(SampleChainDeviceState())

    companion object {
        private const val VOLUME_MIN_DB = -24f
        private const val VOLUME_MAX_DB = 24f
        private const val VOLUME_RANGE_DB = VOLUME_MAX_DB - VOLUME_MIN_DB
        private const val MAX_FADE_MS = 1000f
        private const val SIGN_EXTEND_24BIT = -0x1000000
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        ChainDeviceShell(
            title = "Sample",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(if (deviceState.isLoaded) 420.dp else 200.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            if (deviceState.isLoaded) {
                AudioView()
            } else {
                EmptyDeviceView()
            }
        }
    }

    @Composable
    private fun AudioView() {
        val deviceState by state.collectAsState()
        var waveformWidthPx by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            // File info: filename + duration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shortName = deviceState.fileName
                    .substringAfterLast('/')
                    .substringAfterLast('\\')

                Text(
                    text = shortName,
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatDuration(deviceState.totalDurationMs),
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
            }

            // Waveform display
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 120.dp)
                    .onSizeChanged { waveformWidthPx = it.width }
                    .clip(RoundedCornerShape(6.dp))
                    .background(Theme[colors][secondary])
                    .border(1.dp, Theme[colors][border], RoundedCornerShape(6.dp))
            ) {
                val effectiveWidthPx = waveformWidthPx.takeIf { it > 0 }?.toFloat() ?: 430f
                val zoomLevel = if (deviceState.totalDurationMs > 0) {
                    effectiveWidthPx / deviceState.totalDurationMs.toFloat()
                } else {
                    1f
                }

                WaveformView(
                    rawData = deviceState.rawData,
                    sampleRate = deviceState.sampleRate,
                    channels = deviceState.channels,
                    bitDepth = deviceState.bitDepth,
                    timelineStartUs = 0L,
                    startSample = 0L,
                    endSample = run {
                        val bps = (deviceState.bitDepth / 8) * deviceState.channels
                        if (bps > 0) (deviceState.rawData?.size?.toLong() ?: 0L) / bps else 0L
                    },
                    renderWidthPx = waveformWidthPx.takeIf { it > 0 },
                    zoomLevel = zoomLevel,
                    fadeInMs = deviceState.fadeInMs,
                    fadeOutMs = deviceState.fadeOutMs,
                    startPosition = deviceState.startPosition,
                    endPosition = deviceState.endPosition,
                    onStartPositionChange = { newStart ->
                        state.update { it.copy(startPosition = newStart.coerceIn(0f, it.endPosition - 0.01f)) }
                    },
                    onEndPositionChange = { newEnd ->
                        state.update { it.copy(endPosition = newEnd.coerceIn(it.startPosition + 0.01f, 1f)) }
                    },
                    onFadeInChange = { newFadeIn ->
                        state.update { it.copy(fadeInMs = newFadeIn.coerceIn(0f, MAX_FADE_MS)) }
                    },
                    onFadeOutChange = { newFadeOut ->
                        state.update { it.copy(fadeOutMs = newFadeOut.coerceIn(0f, MAX_FADE_MS)) }
                    },
                    maxFadeMs = MAX_FADE_MS,
                    modifier = Modifier.fillMaxSize()
                )
            }

            SampleVolumeEnvelopeEditor(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .fillMaxWidth()
                    .height(SampleEnvelopeHeightDp.dp),
                lane = deviceState.volumeAutomationLane
                    ?: TimelineAutomationLane(target = TimelineTrackAutomationTarget.VOLUME),
                durationMs = deviceState.totalDurationMs,
                onLaneCommitted = { beforeLane, afterLane ->
                    val normalizedBeforeLane = beforeLane
                        .normalized()
                        .takeIf { it.points.isNotEmpty() }
                    val normalizedAfterLane = afterLane
                        .normalized()
                        .takeIf { it.points.isNotEmpty() }
                    pushStateChange(
                        before = state.value.copy(volumeAutomationLane = normalizedBeforeLane),
                        after = state.value.copy(volumeAutomationLane = normalizedAfterLane)
                    )
                    state.update { currentState ->
                        currentState.copy(volumeAutomationLane = normalizedAfterLane)
                    }
                }
            )

            // Controls: Fade In | Fade Out | Volume  (Start/End via waveform handles)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var beforeFadeIn = deviceState.fadeInMs
                TextDial(
                    headline = "Fade In",
                    text = "${deviceState.fadeInMs.roundToInt()} ms",
                    value = deviceState.fadeInMs / MAX_FADE_MS,
                    onStartValueChange = { beforeFadeIn = deviceState.fadeInMs },
                    onValueChange = { value ->
                        state.update {
                            it.copy(fadeInMs = (value * MAX_FADE_MS).coerceIn(0f, MAX_FADE_MS))
                        }
                    },
                    onFinishValueChange = {
                        pushStateChange(
                            before = state.value.copy(fadeInMs = beforeFadeIn),
                            after = state.value
                        )
                    },
                    onResolveTextValue = { text ->
                        val ms = text.removeSuffix("ms").trim().toIntOrNull()
                        ms?.let { v ->
                            if (v in 0..MAX_FADE_MS.toInt()) {
                                val before = state.value
                                state.update { it.copy(fadeInMs = v.toFloat()) }
                                pushStateChange(before, state.value)
                            }
                        }
                    }
                )

                var beforeFadeOut = deviceState.fadeOutMs
                TextDial(
                    headline = "Fade Out",
                    text = "${deviceState.fadeOutMs.roundToInt()} ms",
                    value = deviceState.fadeOutMs / MAX_FADE_MS,
                    onStartValueChange = { beforeFadeOut = deviceState.fadeOutMs },
                    onValueChange = { value ->
                        state.update {
                            it.copy(fadeOutMs = (value * MAX_FADE_MS).coerceIn(0f, MAX_FADE_MS))
                        }
                    },
                    onFinishValueChange = {
                        pushStateChange(
                            before = state.value.copy(fadeOutMs = beforeFadeOut),
                            after = state.value
                        )
                    },
                    onResolveTextValue = { text ->
                        val ms = text.removeSuffix("ms").trim().toIntOrNull()
                        ms?.let { v ->
                            if (v in 0..MAX_FADE_MS.toInt()) {
                                val before = state.value
                                state.update { it.copy(fadeOutMs = v.toFloat()) }
                                pushStateChange(before, state.value)
                            }
                        }
                    }
                )

                var beforeVolume = deviceState.volumeDb
                TextDial(
                    headline = "Volume",
                    text = "${if (deviceState.volumeDb >= 0) "+" else ""}${deviceState.volumeDb} dB",
                    value = (deviceState.volumeDb - VOLUME_MIN_DB) / VOLUME_RANGE_DB,
                    onStartValueChange = { beforeVolume = deviceState.volumeDb },
                    onValueChange = { value ->
                        state.update {
                            it.copy(volumeDb = ((value * VOLUME_RANGE_DB) + VOLUME_MIN_DB).coerceIn(VOLUME_MIN_DB, VOLUME_MAX_DB))
                        }
                    },
                    onFinishValueChange = {
                        pushStateChange(
                            before = state.value.copy(volumeDb = beforeVolume),
                            after = state.value
                        )
                    },
                    onResolveTextValue = { text ->
                        val db = text.replace("dB", "").replace("+", "").trim().toFloatOrNull()
                        db?.let { v ->
                            if (v in VOLUME_MIN_DB..VOLUME_MAX_DB) {
                                val before = state.value
                                state.update { it.copy(volumeDb = v) }
                                pushStateChange(before, state.value)
                            }
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun EmptyDeviceView() {
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = Icons.TwoTone.AudioFile,
                contentDescription = null,
                tint = Theme[colors][mutedForeground],
                modifier = Modifier.size(32.dp)
            )

            Text(
                text = "Load a sample",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )

            Button(
                onClick = {
                    scope.launch {
                        val file = FileKit.openFilePicker(
                            mode = FileKitMode.Single,
                            title = "Select Audio File",
                            type = FileKitType.File(
                                extensions = AudioDecoder.getSupportedFormats()
                            )
                        )

                        file?.let { selectedFile ->
                            try {
                                val audioSignal = AudioDecoder.decodeAudioData(
                                    audioData = selectedFile.readBytes(),
                                    fileName = selectedFile.name
                                )

                                audioSignal?.let { signal ->
                                    val bytesPerSample = signal.bitDepth / 8
                                    val frameSize = bytesPerSample * signal.channels
                                    val totalFrames = (signal.rawData?.size ?: 0) / frameSize
                                    val durationMs = ((totalFrames.toFloat() / signal.sampleRate) * 1000f).toLong()

                                    state.update { currentState ->
                                        currentState.copy(
                                            fileName = selectedFile.name,
                                            rawData = signal.rawData,
                                            sampleRate = signal.sampleRate,
                                            channels = signal.channels,
                                            bitDepth = signal.bitDepth,
                                            totalDurationMs = durationMs,
                                            isLoaded = true
                                        )
                                    }
                                } ?: run {
                                    println("Failed to decode audio file: ${selectedFile.name}")
                                }
                            } catch (e: Exception) {
                                println("Error loading audio file: ${e.message}")
                            }
                        }
                    }
                },
                variant = ButtonVariant.Secondary,
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = Theme[colors][secondaryForeground],
                )
                Text("Open Sample")
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        n.filterIsInstance<Signal.Midi>().forEach { midiSignal ->
            if (midiSignal.velocity != 0 && state.value.isLoaded) {
                dev.anthonyhfm.amethyst.core.engine.echo.Echo.cancel(this)
                val deviceState = state.value

                val processedData = applyAudioEffects(
                    rawData = deviceState.rawData,
                    sampleRate = deviceState.sampleRate,
                    channels = deviceState.channels,
                    bitDepth = deviceState.bitDepth,
                    fadeInMs = deviceState.fadeInMs,
                    fadeOutMs = deviceState.fadeOutMs,
                    volumeDb = deviceState.volumeDb,
                    startPosition = deviceState.startPosition,
                    endPosition = deviceState.endPosition,
                    volumeAutomationLane = deviceState.volumeAutomationLane
                )

                val audioSignal = Signal.AudioSignal(
                    origin = this,
                    rawData = processedData,
                    sampleRate = deviceState.sampleRate,
                    channels = deviceState.channels,
                    bitDepth = deviceState.bitDepth
                )

                signalExit?.invoke(listOf(audioSignal))
            } else if (midiSignal.velocity != 0 && !state.value.isLoaded) {
                signalExit?.invoke(n)
            }
        }
    }

    private fun applyAudioEffects(
        rawData: ByteArray?,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        fadeInMs: Float,
        fadeOutMs: Float,
        volumeDb: Float,
        startPosition: Float,
        endPosition: Float,
        volumeAutomationLane: TimelineAutomationLane?
    ): ByteArray? {
        if (rawData == null || rawData.isEmpty()) return rawData

        val bytesPerSample = bitDepth / 8
        val frameSize = bytesPerSample * channels
        val totalFrames = rawData.size / frameSize

        val startFrame = (totalFrames * startPosition).toInt().coerceIn(0, totalFrames)
        val endFrame = (totalFrames * endPosition).toInt().coerceIn(startFrame, totalFrames)
        val activeFrames = endFrame - startFrame

        if (activeFrames <= 0) return ByteArray(0)

        val outputData = ByteArray(activeFrames * frameSize)

        val fadeInFrames = ((fadeInMs / 1000f) * sampleRate).toInt().coerceAtMost(activeFrames)
        val fadeOutFrames = ((fadeOutMs / 1000f) * sampleRate).toInt().coerceAtMost(activeFrames)
        val fadeOutStartFrame = activeFrames - fadeOutFrames

        val volumeGain = 10.0.pow(volumeDb / 20.0).toFloat()
        val normalizedVolumeAutomationLane = volumeAutomationLane
            ?.normalized()
            ?.takeIf { it.enabled && it.target == TimelineTrackAutomationTarget.VOLUME }

        for (frame in 0 until activeFrames) {
            var gain = volumeGain
            val frameTimeMs = if (sampleRate > 0) {
                ((frame.toDouble() / sampleRate.toDouble()) * 1000.0).roundToLong()
            } else {
                0L
            }

            gain *= normalizedVolumeAutomationLane?.valueAt(
                timeMs = frameTimeMs,
                defaultValue = TimelineTrackAutomationTarget.VOLUME.defaultValue
            ) ?: TimelineTrackAutomationTarget.VOLUME.defaultValue

            if (frame < fadeInFrames && fadeInFrames > 0) {
                gain *= frame.toFloat() / fadeInFrames.toFloat()
            }

            if (frame >= fadeOutStartFrame && fadeOutFrames > 0) {
                gain *= (activeFrames - frame).toFloat() / fadeOutFrames.toFloat()
            }

            for (ch in 0 until channels) {
                val sourceOffset = (startFrame + frame) * frameSize + ch * bytesPerSample
                val destOffset = frame * frameSize + ch * bytesPerSample

                when (bitDepth) {
                    8 -> {
                        val sample = rawData[sourceOffset].toInt() and 0xFF
                        val centered = sample - 128
                        val amplified = (centered * gain).toInt().coerceIn(-128, 127)
                        outputData[destOffset] = (amplified + 128).toByte()
                    }
                    16 -> {
                        val lo = rawData[sourceOffset].toInt() and 0xFF
                        val hi = rawData[sourceOffset + 1].toInt() shl 8
                        val sample = (hi or lo).toShort().toInt()
                        val amplified = (sample * gain).toInt().coerceIn(-32768, 32767)
                        outputData[destOffset] = (amplified and 0xFF).toByte()
                        outputData[destOffset + 1] = ((amplified shr 8) and 0xFF).toByte()
                    }
                    24 -> {
                        val b0 = rawData[sourceOffset].toInt() and 0xFF
                        val b1 = rawData[sourceOffset + 1].toInt() and 0xFF
                        val b2 = rawData[sourceOffset + 2].toInt() and 0xFF
                        var sample = b0 or (b1 shl 8) or (b2 shl 16)
                        if ((sample and 0x800000) != 0) sample = sample or SIGN_EXTEND_24BIT
                        val amplified = (sample * gain).toInt().coerceIn(-8388608, 8388607)
                        outputData[destOffset] = (amplified and 0xFF).toByte()
                        outputData[destOffset + 1] = ((amplified shr 8) and 0xFF).toByte()
                        outputData[destOffset + 2] = ((amplified shr 16) and 0xFF).toByte()
                    }
                    32 -> {
                        val b0 = rawData[sourceOffset].toInt() and 0xFF
                        val b1 = rawData[sourceOffset + 1].toInt() and 0xFF
                        val b2 = rawData[sourceOffset + 2].toInt() and 0xFF
                        val b3 = rawData[sourceOffset + 3].toInt() and 0xFF
                        val sample = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                        val amplified = (sample * gain).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                        outputData[destOffset] = (amplified and 0xFF).toByte()
                        outputData[destOffset + 1] = ((amplified shr 8) and 0xFF).toByte()
                        outputData[destOffset + 2] = ((amplified shr 16) and 0xFF).toByte()
                        outputData[destOffset + 3] = ((amplified shr 24) and 0xFF).toByte()
                    }
                }
            }
        }

        return outputData
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000L
        val remainderMs = durationMs % 1000L
        return if (durationMs < 10_000L) {
            // "X.XX s" — two decimal places via integer math
            val hundredths = (remainderMs / 10L).toString().padStart(2, '0')
            "$totalSeconds.$hundredths s"
        } else {
            // "XX.X s" — one decimal place
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
                .clip(RoundedCornerShape(6.dp))
                .background(Theme[colors][secondary])
                .border(1.dp, Theme[colors][border], RoundedCornerShape(6.dp))
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
    val volumeAutomationLane: TimelineAutomationLane? = null
) : DeviceState()

fun sampleChainStateFromAudioEntry(
    entry: AudioEntry,
    volumeAutomationLane: TimelineAutomationLane? = null
): SampleChainDeviceState? {
    val source = entry.source() ?: return null
    val bytesPerFrame = entry.bytesPerSample
    if (bytesPerFrame <= 0) return null

    val startByte = (entry.clipStartSample * bytesPerFrame).toInt().coerceIn(0, source.rawData.size)
    val endByte = (entry.clipEndSample * bytesPerFrame).toInt().coerceIn(startByte, source.rawData.size)
    if (endByte <= startByte) return null

    val clippedRawData = source.rawData.sliceArray(startByte until endByte)
    val clipDurationMs = usToRoundedMs(samplesToUs(entry.clipSampleCount, entry.sampleRate))
    val displayName = entry.name.ifBlank { entry.fileName }

    return SampleChainDeviceState(
        fileName = displayName,
        rawData = clippedRawData,
        sampleRate = entry.sampleRate,
        channels = entry.channels,
        bitDepth = entry.bitDepth,
        totalDurationMs = clipDurationMs,
        isLoaded = true,
        startPosition = 0f,
        endPosition = 1f,
        volumeAutomationLane = volumeAutomationLane?.normalized()
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
